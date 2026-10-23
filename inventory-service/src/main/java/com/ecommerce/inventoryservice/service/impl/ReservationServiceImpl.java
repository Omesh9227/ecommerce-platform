package com.ecommerce.inventoryservice.service.impl;

import com.ecommerce.inventoryservice.dto.request.ReservationRequest;
import com.ecommerce.inventoryservice.dto.response.ReservationResponse;
import com.ecommerce.inventoryservice.entity.InventoryItem;
import com.ecommerce.inventoryservice.entity.MovementType;
import com.ecommerce.inventoryservice.entity.Reservation;
import com.ecommerce.inventoryservice.entity.ReservationItem;
import com.ecommerce.inventoryservice.entity.ReservationStatus;
import com.ecommerce.inventoryservice.entity.StockMovement;
import com.ecommerce.inventoryservice.exception.BadRequestException;
import com.ecommerce.inventoryservice.exception.ConflictException;
import com.ecommerce.inventoryservice.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.mapper.InventoryMapper;
import com.ecommerce.inventoryservice.repository.InventoryItemRepository;
import com.ecommerce.inventoryservice.repository.ReservationRepository;
import com.ecommerce.inventoryservice.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reservations hold stock for an order. Overselling is prevented three ways:
 * 1. the stock rows are locked (SELECT ... FOR UPDATE) for the whole check-and-update,
 * 2. all checks run before any change, so it's all-or-nothing,
 * 3. the database CHECK (reserved <= on hand) rejects anything that slips through.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryItemRepository itemRepository;
    private final StockChangeRecorder stockChangeRecorder;
    private final InventoryMapper mapper;

    @Override
    @Transactional
    public ReservationResult reserve(ReservationRequest request, UUID actorId) {
        String orderReference = request.orderReference().trim();

        Optional<Reservation> existing = reservationRepository.findByOrderReference(orderReference);
        if (existing.isPresent()) {
            // Safe retry: the caller (e.g. order-service after a timeout) gets the same answer again
            return new ReservationResult(mapper.toResponse(existing.get()), false);
        }

        // Same product listed twice is merged; TreeMap keeps a stable order
        Map<UUID, Integer> requested = new TreeMap<>();
        request.items().forEach(i -> requested.merge(i.productId(), i.quantity(), Integer::sum));

        Map<UUID, InventoryItem> items = lockItems(requested.keySet());

        List<String> problems = new ArrayList<>();
        requested.forEach((productId, quantity) -> {
            InventoryItem item = items.get(productId);
            if (!item.isSellable()) {
                problems.add(item.getSku() + " is not available for sale (status " + item.getProductStatus() + ")");
            } else if (item.getAvailableQuantity() < quantity) {
                problems.add(item.getSku() + ": requested " + quantity + ", available " + item.getAvailableQuantity());
            }
        });
        if (!problems.isEmpty()) {
            throw new ConflictException("Cannot reserve stock. " + String.join("; ", problems));
        }

        Reservation reservation = new Reservation();
        reservation.setOrderReference(orderReference);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setCreatedBy(actorId);

        List<StockMovement> movements = new ArrayList<>();
        requested.forEach((productId, quantity) -> {
            InventoryItem item = items.get(productId);
            item.setQuantityReserved(item.getQuantityReserved() + quantity);
            reservation.addItem(productId, quantity);
            movements.add(StockChangeRecorder.movement(item, MovementType.RESERVE, quantity, null, orderReference, actorId));
        });

        Reservation saved;
        try {
            saved = reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            // Another request with the same order reference won the race; this transaction rolls back
            throw new ConflictException("A reservation for order " + orderReference + " already exists");
        }
        stockChangeRecorder.record(new ArrayList<>(items.values()), movements);

        log.info("Reserved stock for order={} items={} by={}", orderReference, requested, actorId);
        return new ReservationResult(mapper.toResponse(saved), true);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(String orderReference) {
        return mapper.toResponse(reservationRepository.findByOrderReference(orderReference)
                .orElseThrow(() -> notFound(orderReference)));
    }

    @Override
    @Transactional
    public ReservationResponse release(String orderReference, UUID actorId) {
        Reservation reservation = lockReservation(orderReference);
        switch (reservation.getStatus()) {
            case RELEASED -> {
                return mapper.toResponse(reservation);
            }
            case CONFIRMED, RETURNED -> throw new ConflictException(
                    "Reservation for order " + orderReference + " is already confirmed and cannot be released");
            case RESERVED -> {
                // continue below
            }
        }

        Map<UUID, InventoryItem> items = lockItems(productIds(reservation));
        List<StockMovement> movements = new ArrayList<>();
        for (ReservationItem line : reservation.getItems()) {
            InventoryItem item = items.get(line.getProductId());
            item.setQuantityReserved(item.getQuantityReserved() - line.getQuantity());
            movements.add(StockChangeRecorder.movement(item, MovementType.RELEASE, -line.getQuantity(),
                    null, orderReference, actorId));
        }
        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.saveAndFlush(reservation);
        stockChangeRecorder.record(new ArrayList<>(items.values()), movements);

        log.info("Released reservation order={} by={}", orderReference, actorId);
        return mapper.toResponse(reservation);
    }

    @Override
    @Transactional
    public ReservationResponse confirm(String orderReference, UUID actorId) {
        Reservation reservation = lockReservation(orderReference);
        switch (reservation.getStatus()) {
            case CONFIRMED -> {
                return mapper.toResponse(reservation);
            }
            case RELEASED, RETURNED -> throw new ConflictException(
                    "Reservation for order " + orderReference + " was " + reservation.getStatus().name().toLowerCase()
                            + " and cannot be confirmed");
            case RESERVED -> {
                // continue below
            }
        }

        Map<UUID, InventoryItem> items = lockItems(productIds(reservation));
        List<StockMovement> movements = new ArrayList<>();
        for (ReservationItem line : reservation.getItems()) {
            InventoryItem item = items.get(line.getProductId());
            item.setQuantityOnHand(item.getQuantityOnHand() - line.getQuantity());
            item.setQuantityReserved(item.getQuantityReserved() - line.getQuantity());
            movements.add(StockChangeRecorder.movement(item, MovementType.SALE, -line.getQuantity(),
                    null, orderReference, actorId));
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.saveAndFlush(reservation);
        stockChangeRecorder.record(new ArrayList<>(items.values()), movements);

        log.info("Confirmed reservation order={} by={}", orderReference, actorId);
        return mapper.toResponse(reservation);
    }

    @Override
    @Transactional
    public ReservationResponse returnStock(String orderReference, UUID actorId) {
        Reservation reservation = lockReservation(orderReference);
        switch (reservation.getStatus()) {
            case RETURNED -> {
                return mapper.toResponse(reservation);
            }
            case RESERVED -> throw new ConflictException(
                    "Reservation for order " + orderReference + " was never confirmed; release it instead");
            case RELEASED -> throw new ConflictException(
                    "Reservation for order " + orderReference + " was released; there is no stock to return");
            case CONFIRMED -> {
                // continue below
            }
        }

        Map<UUID, InventoryItem> items = lockItems(productIds(reservation));
        List<StockMovement> movements = new ArrayList<>();
        for (ReservationItem line : reservation.getItems()) {
            InventoryItem item = items.get(line.getProductId());
            item.setQuantityOnHand(item.getQuantityOnHand() + line.getQuantity());
            movements.add(StockChangeRecorder.movement(item, MovementType.RETURN, line.getQuantity(),
                    null, orderReference, actorId));
        }
        reservation.setStatus(ReservationStatus.RETURNED);
        reservationRepository.saveAndFlush(reservation);
        stockChangeRecorder.record(new ArrayList<>(items.values()), movements);

        log.info("Returned stock for order={} by={}", orderReference, actorId);
        return mapper.toResponse(reservation);
    }

    // ---------- Helpers ----------

    private Map<UUID, InventoryItem> lockItems(Set<UUID> productIds) {
        Map<UUID, InventoryItem> items = itemRepository.findAllByIdForUpdate(productIds).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
        if (items.size() != productIds.size()) {
            List<UUID> missing = productIds.stream().filter(id -> !items.containsKey(id)).toList();
            throw new BadRequestException("No inventory record for product(s): " + missing);
        }
        return items;
    }

    private Reservation lockReservation(String orderReference) {
        return reservationRepository.findByOrderReferenceForUpdate(orderReference)
                .orElseThrow(() -> notFound(orderReference));
    }

    private static Set<UUID> productIds(Reservation reservation) {
        return reservation.getItems().stream().map(ReservationItem::getProductId).collect(Collectors.toSet());
    }

    private static ResourceNotFoundException notFound(String orderReference) {
        return new ResourceNotFoundException("Reservation not found for order: " + orderReference);
    }
}

package com.ecommerce.inventoryservice.service.impl;

import com.ecommerce.inventoryservice.dto.request.ReservationRequest;
import com.ecommerce.inventoryservice.dto.response.ReservationResponse;
import com.ecommerce.inventoryservice.entity.InventoryItem;
import com.ecommerce.inventoryservice.entity.MovementType;
import com.ecommerce.inventoryservice.entity.Reservation;
import com.ecommerce.inventoryservice.entity.ReservationStatus;
import com.ecommerce.inventoryservice.entity.StockMovement;
import com.ecommerce.inventoryservice.exception.BadRequestException;
import com.ecommerce.inventoryservice.exception.ConflictException;
import com.ecommerce.inventoryservice.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.mapper.InventoryMapper;
import com.ecommerce.inventoryservice.repository.InventoryItemRepository;
import com.ecommerce.inventoryservice.repository.ReservationRepository;
import com.ecommerce.inventoryservice.service.ReservationService.ReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private StockChangeRecorder stockChangeRecorder;
    @Captor
    private ArgumentCaptor<List<StockMovement>> movementsCaptor;

    private ReservationServiceImpl reservationService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID phoneId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reservationService = new ReservationServiceImpl(reservationRepository, itemRepository, stockChangeRecorder,
                new InventoryMapper());
    }

    private static InventoryItem item(UUID id, String sku, String status, int onHand, int reserved) {
        return InventoryItem.of(id, sku, sku + " name", status, onHand, reserved, 5);
    }

    private static ReservationRequest.Item line(UUID productId, int quantity) {
        return new ReservationRequest.Item(productId, quantity);
    }

    private Reservation reservation(String orderReference, ReservationStatus status, UUID productId, int quantity) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setOrderReference(orderReference);
        reservation.setStatus(status);
        reservation.addItem(productId, quantity);
        return reservation;
    }

    // ---------- reserve ----------

    @Test
    void reserve_holdsStockForEveryItem_andMergesRepeatedProducts() {
        InventoryItem phone = item(phoneId, "PHONE", "ACTIVE", 10, 1);
        InventoryItem phoneCase = item(caseId, "CASE", "ACTIVE", 5, 0);
        when(reservationRepository.findByOrderReference("ORD-1")).thenReturn(Optional.empty());
        when(itemRepository.findAllByIdForUpdate(any())).thenReturn(List.of(phone, phoneCase));
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResult result = reservationService.reserve(new ReservationRequest(" ORD-1 ",
                List.of(line(phoneId, 2), line(caseId, 5), line(phoneId, 1))), adminId);

        assertThat(result.created()).isTrue();
        ReservationResponse response = result.reservation();
        assertThat(response.orderReference()).isEqualTo("ORD-1");
        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(response.items()).containsExactlyInAnyOrder(
                new ReservationResponse.Item(phoneId, 3), new ReservationResponse.Item(caseId, 5));

        assertThat(phone.getQuantityReserved()).isEqualTo(4);
        assertThat(phoneCase.getQuantityReserved()).isEqualTo(5);
        assertThat(phone.getQuantityOnHand()).isEqualTo(10); // on-hand only changes on confirm

        verify(stockChangeRecorder).record(anyList(), movementsCaptor.capture());
        assertThat(movementsCaptor.getValue())
                .allSatisfy(m -> {
                    assertThat(m.getMovementType()).isEqualTo(MovementType.RESERVE);
                    assertThat(m.getReference()).isEqualTo("ORD-1");
                })
                .extracting(StockMovement::getQuantityChange).containsExactlyInAnyOrder(3, 5);
    }

    @Test
    void reserve_sameOrderReferenceAgain_returnsExistingWithoutChanges() {
        Reservation existing = reservation("ORD-1", ReservationStatus.RESERVED, phoneId, 2);
        when(reservationRepository.findByOrderReference("ORD-1")).thenReturn(Optional.of(existing));

        ReservationResult result = reservationService.reserve(
                new ReservationRequest("ORD-1", List.of(line(phoneId, 2))), adminId);

        assertThat(result.created()).isFalse();
        assertThat(result.reservation().reservationId()).isEqualTo(existing.getId());
        verifyNoInteractions(itemRepository, stockChangeRecorder);
    }

    @Test
    void reserve_notEnoughStockOrNotForSale_rejectsEverything() {
        InventoryItem phone = item(phoneId, "PHONE", "ACTIVE", 3, 2);           // only 1 available
        InventoryItem phoneCase = item(caseId, "CASE", "DISCONTINUED", 50, 0);  // not for sale
        when(reservationRepository.findByOrderReference("ORD-1")).thenReturn(Optional.empty());
        when(itemRepository.findAllByIdForUpdate(any())).thenReturn(List.of(phone, phoneCase));

        assertThatThrownBy(() -> reservationService.reserve(
                new ReservationRequest("ORD-1", List.of(line(phoneId, 2), line(caseId, 1))), adminId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PHONE: requested 2, available 1")
                .hasMessageContaining("CASE is not available for sale");

        assertThat(phone.getQuantityReserved()).isEqualTo(2);
        assertThat(phoneCase.getQuantityReserved()).isZero();
        verify(reservationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stockChangeRecorder);
    }

    @Test
    void reserve_unknownProduct_isBadRequest() {
        when(reservationRepository.findByOrderReference("ORD-1")).thenReturn(Optional.empty());
        when(itemRepository.findAllByIdForUpdate(any())).thenReturn(List.of());

        assertThatThrownBy(() -> reservationService.reserve(
                new ReservationRequest("ORD-1", List.of(line(phoneId, 1))), adminId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(phoneId.toString());
        verifyNoInteractions(stockChangeRecorder);
    }

    @Test
    void reserve_lostRaceOnSameOrderReference_isConflict() {
        when(reservationRepository.findByOrderReference("ORD-1")).thenReturn(Optional.empty());
        when(itemRepository.findAllByIdForUpdate(any())).thenReturn(List.of(item(phoneId, "PHONE", "ACTIVE", 10, 0)));
        when(reservationRepository.saveAndFlush(any(Reservation.class)))
                .thenThrow(new DataIntegrityViolationException("uk_reservations_order_reference"));

        assertThatThrownBy(() -> reservationService.reserve(
                new ReservationRequest("ORD-1", List.of(line(phoneId, 1))), adminId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
        verifyNoInteractions(stockChangeRecorder);
    }

    // ---------- release ----------

    @Test
    void release_givesHeldStockBack() {
        Reservation reservation = reservation("ORD-1", ReservationStatus.RESERVED, phoneId, 3);
        InventoryItem phone = item(phoneId, "PHONE", "ACTIVE", 10, 3);
        when(reservationRepository.findByOrderReferenceForUpdate("ORD-1")).thenReturn(Optional.of(reservation));
        when(itemRepository.findAllByIdForUpdate(any())).thenReturn(List.of(phone));

        ReservationResponse response = reservationService.release("ORD-1", adminId);

        assertThat(response.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(phone.getQuantityReserved()).isZero();
        assertThat(phone.getQuantityOnHand()).isEqualTo(10);
        verify(stockChangeRecorder).record(anyList(), movementsCaptor.capture());
        assertThat(movementsCaptor.getValue()).singleElement().satisfies(m -> {
            assertThat(m.getMovementType()).isEqualTo(MovementType.RELEASE);
            assertThat(m.getQuantityChange()).isEqualTo(-3);
        });
    }

    @Test
    void release_twice_isHarmless() {
        when(reservationRepository.findByOrderReferenceForUpdate("ORD-1"))
                .thenReturn(Optional.of(reservation("ORD-1", ReservationStatus.RELEASED, phoneId, 3)));

        assertThat(reservationService.release("ORD-1", adminId).status()).isEqualTo(ReservationStatus.RELEASED);
        verifyNoInteractions(itemRepository, stockChangeRecorder);
    }

    @Test
    void release_confirmedReservation_isConflict() {
        when(reservationRepository.findByOrderReferenceForUpdate("ORD-1"))
                .thenReturn(Optional.of(reservation("ORD-1", ReservationStatus.CONFIRMED, phoneId, 3)));

        assertThatThrownBy(() -> reservationService.release("ORD-1", adminId))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(itemRepository, stockChangeRecorder);
    }

    @Test
    void release_unknownOrder_isNotFound() {
        when(reservationRepository.findByOrderReferenceForUpdate("ORD-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.release("ORD-404", adminId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- confirm ----------

    @Test
    void confirm_turnsHoldIntoSale() {
        Reservation reservation = reservation("ORD-1", ReservationStatus.RESERVED, phoneId, 3);
        InventoryItem phone = item(phoneId, "PHONE", "ACTIVE", 10, 5);
        when(reservationRepository.findByOrderReferenceForUpdate("ORD-1")).thenReturn(Optional.of(reservation));
        when(itemRepository.findAllByIdForUpdate(any())).thenReturn(List.of(phone));

        ReservationResponse response = reservationService.confirm("ORD-1", adminId);

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(phone.getQuantityOnHand()).isEqualTo(7);
        assertThat(phone.getQuantityReserved()).isEqualTo(2);
        verify(stockChangeRecorder).record(anyList(), movementsCaptor.capture());
        assertThat(movementsCaptor.getValue()).singleElement().satisfies(m -> {
            assertThat(m.getMovementType()).isEqualTo(MovementType.SALE);
            assertThat(m.getQuantityChange()).isEqualTo(-3);
            assertThat(m.getQuantityOnHandAfter()).isEqualTo(7);
        });
    }

    @Test
    void confirm_twice_isHarmless() {
        when(reservationRepository.findByOrderReferenceForUpdate("ORD-1"))
                .thenReturn(Optional.of(reservation("ORD-1", ReservationStatus.CONFIRMED, phoneId, 3)));

        assertThat(reservationService.confirm("ORD-1", adminId).status()).isEqualTo(ReservationStatus.CONFIRMED);
        verifyNoInteractions(itemRepository, stockChangeRecorder);
    }

    @Test
    void confirm_releasedReservation_isConflict() {
        when(reservationRepository.findByOrderReferenceForUpdate("ORD-1"))
                .thenReturn(Optional.of(reservation("ORD-1", ReservationStatus.RELEASED, phoneId, 3)));

        assertThatThrownBy(() -> reservationService.confirm("ORD-1", adminId))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(itemRepository, stockChangeRecorder);
    }
}

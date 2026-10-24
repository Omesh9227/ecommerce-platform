package com.ecommerce.shippingservice.service;

import com.ecommerce.shippingservice.dto.response.DeliveryEstimateResponse;
import com.ecommerce.shippingservice.dto.response.PageResponse;
import com.ecommerce.shippingservice.dto.response.ShipmentResponse;
import com.ecommerce.shippingservice.dto.response.ShipmentSummaryResponse;
import com.ecommerce.shippingservice.dto.response.TrackingResponse;
import com.ecommerce.shippingservice.entity.Shipment;
import com.ecommerce.shippingservice.entity.ShipmentStatus;
import com.ecommerce.shippingservice.exception.BadRequestException;
import com.ecommerce.shippingservice.exception.ResourceNotFoundException;
import com.ecommerce.shippingservice.repository.ShipmentRepository;
import com.ecommerce.shippingservice.repository.ShipmentSpecifications;
import com.ecommerce.shippingservice.repository.TrackingEventRepository;
import com.ecommerce.shippingservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Reading shipments. Customers only ever see their own; someone else's shipment is a 404, as if it didn't exist. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentQueryService {

    static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "updatedAt", "estimatedDeliveryDate", "status");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);

    private final ShipmentRepository shipmentRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final DeliveryEstimator estimator;

    public PageResponse<ShipmentSummaryResponse> mine(AuthenticatedUser caller, Pageable pageable) {
        checkSort(pageable);
        return PageResponse.from(shipmentRepository.findAll(ShipmentSpecifications.belongsTo(caller.id()), pageable)
                .map(ShipmentSummaryResponse::of));
    }

    /** The shipment of one of the caller's orders (any order for an admin). */
    public ShipmentResponse forOrder(String orderNumber, AuthenticatedUser caller) {
        String number = orderNumber == null ? "" : orderNumber.trim().toUpperCase(Locale.ROOT);
        Shipment shipment = shipmentRepository.findByOrderNumber(number)
                .filter(s -> caller.isAdmin() || s.getUserId().equals(caller.id()))
                .orElseThrow(() -> new ResourceNotFoundException("No shipment for order " + number
                        + " (orders get a shipment once they are paid)"));
        return full(shipment, caller.isAdmin());
    }

    /** Public: anyone with the tracking number. */
    public TrackingResponse track(String trackingNumber) {
        String number = TrackingNumbers.normalize(trackingNumber);
        Shipment shipment = (number == null ? null : shipmentRepository.findByTrackingNumber(number).orElse(null));
        if (shipment == null) {
            throw new ResourceNotFoundException("No shipment with tracking number " + trackingNumber);
        }
        return TrackingResponse.of(shipment, trackingEventRepository.findByShipmentIdOrderByOccurredAtAscSeqAsc(shipment.getId()));
    }

    public DeliveryEstimateResponse estimate(String postalCode) {
        String code = postalCode == null ? "" : postalCode.trim();
        if (!DeliveryEstimator.PIN_CODE.matcher(code).matches()) {
            throw new BadRequestException("postalCode must be a 6-digit PIN code");
        }
        int days = estimator.deliveryDays(code);
        LocalDate date = estimator.estimateFromNow(code);
        return new DeliveryEstimateResponse(code, days, date, "Delivery by " + DAY.format(date));
    }

    // ---------- Admin ----------

    public PageResponse<ShipmentSummaryResponse> all(ShipmentStatus status, String orderNumber, String carrier, Pageable pageable) {
        checkSort(pageable);
        Specification<Shipment> filter = Specification.allOf(ShipmentSpecifications.hasStatus(status),
                ShipmentSpecifications.forOrder(orderNumber), ShipmentSpecifications.withCarrier(carrier));
        return PageResponse.from(shipmentRepository.findAll(filter, pageable).map(ShipmentSummaryResponse::of));
    }

    public ShipmentResponse byId(UUID id) {
        return full(shipmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Shipment", id)), true);
    }

    // ---------- Helpers ----------

    private ShipmentResponse full(Shipment shipment, boolean forAdmin) {
        return ShipmentResponse.of(shipment, trackingEventRepository.findByShipmentIdOrderByOccurredAtAscSeqAsc(shipment.getId()), forAdmin);
    }

    private static void checkSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Sortable fields: "
                        + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()));
            }
        }
    }
}

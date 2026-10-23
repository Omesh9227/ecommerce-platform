package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.dto.request.ReservationRequest;
import com.ecommerce.inventoryservice.dto.response.ReservationResponse;

import java.util.UUID;

public interface ReservationService {

    /** @param created false when a reservation with this order reference already existed (idempotent retry) */
    record ReservationResult(ReservationResponse reservation, boolean created) {
    }

    /** All-or-nothing: either every item is reserved or nothing changes. */
    ReservationResult reserve(ReservationRequest request, UUID actorId);

    ReservationResponse getReservation(String orderReference);

    /** Gives the held stock back (order cancelled, payment failed). Idempotent. */
    ReservationResponse release(String orderReference, UUID actorId);

    /** Turns the hold into a sale: on-hand and reserved both go down. Idempotent. */
    ReservationResponse confirm(String orderReference, UUID actorId);

    /** Undoes a sale (paid order cancelled before shipping): the stock goes back on hand. Idempotent. */
    ReservationResponse returnStock(String orderReference, UUID actorId);
}

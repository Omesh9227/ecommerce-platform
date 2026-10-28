package com.ecommerce.shippingservice.service;

import com.ecommerce.shippingservice.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Tracking numbers: the carrier's own if the warehouse enters one, otherwise ours - "EC" + 12 random digits
 * (e.g. EC482019375610). Random, so they can't be guessed from another customer's number; the public tracking page
 * shows no names or addresses anyway.
 */
@Component
@RequiredArgsConstructor
public class TrackingNumbers {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ATTEMPTS = 5;

    private final ShipmentRepository shipmentRepository;

    public String generate() {
        for (int i = 0; i < ATTEMPTS; i++) {
            StringBuilder number = new StringBuilder("EC");
            for (int d = 0; d < 12; d++) {
                number.append(RANDOM.nextInt(10));
            }
            if (!shipmentRepository.existsByTrackingNumber(number.toString())) {
                return number.toString();
            }
        }
        throw new IllegalStateException("Could not generate a unique tracking number");
    }

    /** Tracking numbers are stored and compared upper-case, without surrounding spaces. */
    public static String normalize(String trackingNumber) {
        return trackingNumber == null || trackingNumber.isBlank() ? null : trackingNumber.trim().toUpperCase(Locale.ROOT);
    }
}

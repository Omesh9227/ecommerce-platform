package com.ecommerce.paymentservice.gateway;

import java.time.YearMonth;

/** Card checks done before talking to the provider: number format (Luhn), expiry, brand. */
public final class CardSupport {

    private CardSupport() {
    }

    /** Digits only (spaces and dashes removed), or null. */
    public static String normalize(String number) {
        return number == null ? null : number.replaceAll("[\\s-]", "");
    }

    /** The Luhn checksum every real card number passes. */
    public static boolean isValidNumber(String digits) {
        if (digits == null || !digits.matches("\\d{12,19}")) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** A card is valid until the end of its expiry month. */
    public static boolean isExpired(int month, int year, YearMonth now) {
        return YearMonth.of(year, month).isBefore(now);
    }

    public static String brand(String digits) {
        if (digits.startsWith("4")) {
            return "VISA";
        }
        if (digits.matches("^(5[1-5]|2[2-7]).*")) {
            return "MASTERCARD";
        }
        if (digits.matches("^3[47].*")) {
            return "AMEX";
        }
        if (digits.matches("^(60|65|81|82|508).*")) {
            return "RUPAY";
        }
        return "CARD";
    }

    public static String last4(String digits) {
        return digits.substring(digits.length() - 4);
    }

    /** omesh.kumar@okhdfc -> om********@okhdfc */
    public static String maskUpi(String upiId) {
        int at = upiId.indexOf('@');
        String name = upiId.substring(0, at);
        String visible = name.length() <= 2 ? name : name.substring(0, 2);
        return visible + "*".repeat(Math.max(0, name.length() - visible.length())) + upiId.substring(at);
    }
}

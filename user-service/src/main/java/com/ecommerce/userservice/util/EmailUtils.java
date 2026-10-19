package com.ecommerce.userservice.util;

import java.util.Locale;

public final class EmailUtils {

    private EmailUtils() {
    }

    /** Emails are stored trimmed and lower-cased so "Omesh@Example.com" and "omesh@example.com" are the same account. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

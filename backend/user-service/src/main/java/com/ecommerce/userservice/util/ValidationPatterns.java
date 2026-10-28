package com.ecommerce.userservice.util;

/** Regex constants shared by request DTOs (annotation values must be compile-time constants). */
public final class ValidationPatterns {

    /** Letters (any language), spaces, apostrophes, dots and hyphens. */
    public static final String NAME = "^[\\p{L} .'-]+$";

    /** 10 to 15 digits, no spaces or symbols. */
    public static final String PHONE = "^[0-9]{10,15}$";

    /** Requires a dot in the domain part, unlike the default @Email which accepts "a@b". */
    public static final String EMAIL = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    /**
     * 8-64 chars, at least one lower-case, one upper-case, one digit and one special character, no whitespace.
     * Upper bound keeps us under BCrypt's 72-byte input limit.
     */
    public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,64}$";

    public static final String PASSWORD_MESSAGE =
            "Password must be 8-64 characters with at least one uppercase letter, one lowercase letter, "
                    + "one digit and one special character, and no spaces";

    private ValidationPatterns() {
    }
}

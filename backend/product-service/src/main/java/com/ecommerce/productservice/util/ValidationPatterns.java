package com.ecommerce.productservice.util;

/** Regex constants shared by request DTOs (annotation values must be compile-time constants). */
public final class ValidationPatterns {

    /** Letters, digits, spaces and common punctuation: "Mobiles & Tablets", "Men's Wear", "T-Shirts". */
    public static final String CATEGORY_NAME = "^[\\p{L}\\p{N} &'(),./-]+$";

    /** 3-64 letters, digits or hyphens. Stored upper-case: "iphone-15-128" becomes "IPHONE-15-128". */
    public static final String SKU = "^[A-Za-z0-9-]{3,64}$";

    /** http(s) URL without spaces. */
    public static final String IMAGE_URL = "^https?://\\S+$";

    private ValidationPatterns() {
    }
}

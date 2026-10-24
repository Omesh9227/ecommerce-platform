package com.ecommerce.adminservice.ingest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** "INR 1,800.00" for activity lines. */
public final class Money {

    private Money() {
    }

    public static String format(BigDecimal amount, String currency) {
        if (amount == null) {
            return "-";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return (currency == null ? "" : currency + " ") + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }
}

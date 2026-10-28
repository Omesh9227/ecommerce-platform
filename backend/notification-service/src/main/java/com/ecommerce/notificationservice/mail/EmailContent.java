package com.ecommerce.notificationservice.mail;

import java.util.List;

/**
 * What an email says, independent of how it is rendered (HTML template + plain-text version).
 * Amounts are already formatted (e.g. "INR 1,800.00").
 */
public record EmailContent(
        String subject,
        String heading,
        String intro,
        List<Detail> details,
        List<ItemLine> items,
        String total,
        String outro
) {

    public record Detail(String label, String value) {
    }

    public record ItemLine(String name, int quantity, String lineTotal) {
    }
}

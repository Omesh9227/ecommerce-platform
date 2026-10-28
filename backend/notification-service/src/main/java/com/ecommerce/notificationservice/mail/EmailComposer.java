package com.ecommerce.notificationservice.mail;

import com.ecommerce.notificationservice.config.NotificationProperties;
import com.ecommerce.notificationservice.event.EventTypes;
import com.ecommerce.notificationservice.event.OrderEvent;
import com.ecommerce.notificationservice.event.PaymentEvent;
import com.ecommerce.notificationservice.event.ShipmentEvent;
import com.ecommerce.notificationservice.event.WishlistEvent;
import com.ecommerce.notificationservice.mail.EmailContent.Detail;
import com.ecommerce.notificationservice.mail.EmailContent.ItemLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides which events become emails and what they say. Events without an email here (e.g. ORDER_CONFIRMED, which
 * always comes with PAYMENT_SUCCEEDED) send nothing, so the customer gets one email per step.
 */
@Component
@RequiredArgsConstructor
public class EmailComposer {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);

    private final NotificationProperties properties;

    public Optional<EmailContent> forOrderEvent(OrderEvent e) {
        String number = e.orderNumber();
        List<ItemLine> items = itemLines(e.items(), e.currency());
        String total = money(e.totalAmount(), e.currency());
        return Optional.ofNullable(switch (e.eventType()) {
            case EventTypes.ORDER_PLACED -> new EmailContent(
                    "Order " + number + " placed - please complete your payment",
                    "Thanks for your order!",
                    "We have reserved your items. Please complete the payment within " + properties.paymentWindowMinutes()
                            + " minutes; after that the order is cancelled automatically and the items are released.",
                    List.of(new Detail("Order number", number), new Detail("Status", "Waiting for payment")),
                    items, total, "You can pay from your orders page.");
            case EventTypes.ORDER_SHIPPED -> {
                List<Detail> details = new ArrayList<>();
                details.add(new Detail("Order number", number));
                if (e.carrier() != null) {
                    details.add(new Detail("Carrier", e.carrier()));
                }
                if (e.trackingNumber() != null) {
                    details.add(new Detail("Tracking number", e.trackingNumber()));
                }
                if (e.estimatedDeliveryDate() != null) {
                    details.add(new Detail("Expected delivery", DAY.format(e.estimatedDeliveryDate())));
                }
                yield new EmailContent("Your order " + number + " has shipped", "Your order is on its way",
                        "Good news - your order has left our warehouse.", details, items, total, null);
            }
            case EventTypes.ORDER_DELIVERED -> new EmailContent("Your order " + number + " was delivered",
                    "Delivered!", "Your order has been delivered. We hope you enjoy it.",
                    List.of(new Detail("Order number", number)), items, total, null);
            case EventTypes.ORDER_CANCELLED -> new EmailContent("Your order " + number + " was cancelled",
                    "Order cancelled",
                    "Your order has been cancelled" + (e.reason() == null ? "." : ": " + e.reason() + "."),
                    List.of(new Detail("Order number", number)), items, total,
                    "If you had already paid, the full amount will be refunded - you'll get a separate email when it's done.");
            default -> null;
        });
    }

    /** @param items the order's items (from its snapshot), shown with the payment */
    public Optional<EmailContent> forPaymentEvent(PaymentEvent e, List<OrderEvent.Item> items) {
        String number = e.orderNumber();
        String amount = money(e.amount(), e.currency());
        return Optional.ofNullable(switch (e.eventType()) {
            case EventTypes.PAYMENT_SUCCEEDED -> new EmailContent("Payment received for order " + number,
                    "Payment received - your order is confirmed",
                    "We received " + amount + " by " + e.describeMethod() + ". We'll let you know when your order ships.",
                    List.of(new Detail("Order number", number), new Detail("Paid with", e.describeMethod())),
                    itemLines(items, e.currency()), amount, null);
            case EventTypes.PAYMENT_FAILED -> new EmailContent("Payment for order " + number + " didn't go through",
                    "Your payment didn't go through",
                    (e.reason() == null ? "The payment was declined." : e.reason())
                            + " You have not been charged.",
                    List.of(new Detail("Order number", number), new Detail("Amount", amount)),
                    List.of(), null,
                    "You can try again with another card or UPI before the order's payment window ends.");
            case EventTypes.PAYMENT_REFUNDED -> new EmailContent("Refund of " + amount + " for order " + number,
                    "Your refund is on its way",
                    "We have refunded " + amount + " to your original payment method (" + e.describeMethod()
                            + "). Depending on your bank it can take 5-7 business days to show up.",
                    List.of(new Detail("Order number", number), new Detail("Refunded", amount)),
                    List.of(), null, null);
            default -> null;
        });
    }

    /** Out for delivery ("arriving today") and missed deliveries. Shipped/delivered emails come from order events. */
    public Optional<EmailContent> forShipmentEvent(ShipmentEvent e) {
        String number = e.orderNumber();
        String carrier = e.carrier() != null ? e.carrier() : "The courier";
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Order number", number));
        if (e.carrier() != null) {
            details.add(new Detail("Carrier", e.carrier()));
        }
        if (e.trackingNumber() != null) {
            details.add(new Detail("Tracking number", e.trackingNumber()));
        }
        return Optional.ofNullable(switch (e.eventType()) {
            case EventTypes.SHIPMENT_OUT_FOR_DELIVERY -> new EmailContent("Your order " + number + " is out for delivery",
                    "Arriving today",
                    carrier + " is on the way with your order and should reach you today. Please keep your phone handy.",
                    details, List.of(), null, null);
            case EventTypes.SHIPMENT_DELIVERY_FAILED -> {
                details.add(new Detail("Attempt", String.valueOf(Math.max(e.failedAttempts(), 1))));
                yield new EmailContent("We missed you - delivery of order " + number, "We couldn't deliver your order",
                        carrier + " tried to deliver your order" + (e.note() == null ? "" : " but couldn't: " + e.note())
                                + ". They'll try again soon.",
                        details, List.of(), null,
                        "If you won't be around, contact the courier with your tracking number to choose another time.");
            }
            default -> null;
        });
    }

    /** A product the customer saved got cheaper. */
    public Optional<EmailContent> forWishlistEvent(WishlistEvent e) {
        if (!EventTypes.WISHLIST_PRICE_DROPPED.equals(e.eventType()) || e.newPrice() == null || e.previousPrice() == null) {
            return Optional.empty();
        }
        String name = e.productName() != null ? e.productName() : e.sku();
        String now = money(e.newPrice(), e.currency());
        String before = money(e.previousPrice(), e.currency());
        String saving = money(e.previousPrice().subtract(e.newPrice()), e.currency());
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Product", name));
        details.add(new Detail("Was", before));
        details.add(new Detail("Now", now));
        details.add(new Detail("You save", saving));
        if (e.priceWhenAdded() != null && e.priceWhenAdded().compareTo(e.previousPrice()) != 0) {
            details.add(new Detail("When you saved it", money(e.priceWhenAdded(), e.currency())));
        }
        return Optional.of(new EmailContent("Price drop: " + name + " is now " + now,
                "A product on your wishlist got cheaper",
                name + " dropped from " + before + " to " + now + ".",
                details, List.of(), null,
                "Prices can change at any time. Open your wishlist to buy it or move it to your cart."));
    }

    /** True for payment events that send an email (and so need the order's snapshot). */
    public static boolean emailsFor(PaymentEvent e) {
        return EventTypes.PAYMENT_SUCCEEDED.equals(e.eventType()) || EventTypes.PAYMENT_FAILED.equals(e.eventType())
                || EventTypes.PAYMENT_REFUNDED.equals(e.eventType());
    }

    private static List<ItemLine> itemLines(List<OrderEvent.Item> items, String currency) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(i -> new ItemLine(i.productName() != null ? i.productName() : i.sku(), i.quantity(),
                        money(i.unitPrice() == null ? null : i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())), currency)))
                .toList();
    }

    /** INR 1,800.00 */
    static String money(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return (currency == null ? "" : currency + " ") + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }
}

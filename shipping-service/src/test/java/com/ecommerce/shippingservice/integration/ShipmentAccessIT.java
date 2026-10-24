package com.ecommerce.shippingservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who sees what: customers their own shipments, everyone a parcel's public tracking, admins everything. */
class ShipmentAccessIT extends AbstractIntegrationTest {

    @Test
    void customersSeeOnlyTheirOwnShipments() throws Exception {
        UUID mine = UUID.randomUUID();
        UUID alsoMine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        shipmentFor(mine, customerId);
        shipmentFor(alsoMine, customerId);
        shipmentFor(theirs, otherCustomerId);

        getAs(customer, SHIPMENTS_URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(3))
                .andExpect(jsonPath("$.data.content[0].city").value("Bengaluru"));
        getAs(customer, SHIPMENTS_URL + "/orders/" + orderNumber(theirs)).andExpect(status().isNotFound());
        getAs(otherCustomer, SHIPMENTS_URL + "/orders/" + orderNumber(mine)).andExpect(status().isNotFound());
        getAs(customer, SHIPMENTS_URL + "/orders/" + orderNumber(mine).toLowerCase()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").doesNotExist());
        getAs(admin, SHIPMENTS_URL + "/orders/" + orderNumber(mine)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(customerId.toString()));
        getAs(customer, SHIPMENTS_URL + "/orders/ORD-000000000000").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("once they are paid")));
        getAs(customer, SHIPMENTS_URL + "?sort=userId,asc").andExpect(status().isBadRequest());
    }

    @Test
    void anyoneCanTrackAParcelButSeesNoPersonalDetails() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID shipmentId = shipmentFor(orderId, customerId);
        String trackingNumber = packAndShip(shipmentId);
        scan(shipmentId, "IN_TRANSIT", "Bengaluru hub", null).andExpect(status().isOk());

        String body = getAs(null, SHIPMENTS_URL + "/track/" + trackingNumber.toLowerCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.carrier").value("Delhivery"))
                .andExpect(jsonPath("$.data.destination").value("Bengaluru, Karnataka"))
                .andExpect(jsonPath("$.data.estimatedDeliveryDate").exists())
                .andExpect(jsonPath("$.data.events.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Omesh", "9876543210", "Green Residency", orderNumber(orderId), customerId.toString(),
                "omesh@example.com", adminId.toString());

        getAs(null, SHIPMENTS_URL + "/track/EC000000000000").andExpect(status().isNotFound());
    }

    @Test
    void anyoneCanAskWhenAnOrderWouldArrive() throws Exception {
        clock.set(Instant.parse("2026-10-24T06:00:00Z"));
        JsonNode estimate = readJson(getAs(null, SHIPMENTS_URL + "/delivery-estimate?postalCode=110001")
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(estimate.get("deliveryDays").asInt()).isEqualTo(5);
        assertThat(estimate.get("estimatedDeliveryDate").asText()).isEqualTo("2026-10-29");
        assertThat(estimate.get("message").asText()).isEqualTo("Delivery by Thu, 29 Oct");

        getAs(null, SHIPMENTS_URL + "/delivery-estimate?postalCode=560034").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Delivery by Sun, 25 Oct"));
        getAs(null, SHIPMENTS_URL + "/delivery-estimate?postalCode=1234").andExpect(status().isBadRequest());
        getAs(null, SHIPMENTS_URL + "/delivery-estimate").andExpect(status().isBadRequest());
    }

    @Test
    void warehouseActionsNeedAnAdmin() throws Exception {
        UUID shipmentId = shipmentFor(UUID.randomUUID(), customerId);
        getAs(null, SHIPMENTS_URL).andExpect(status().isUnauthorized());
        getAs(customer, ADMIN_URL).andExpect(status().isForbidden());
        postJson(ADMIN_URL + "/" + shipmentId + "/pack", customer, java.util.Map.of()).andExpect(status().isForbidden());
        postJson(ADMIN_URL + "/" + shipmentId + "/pack", null, java.util.Map.of()).andExpect(status().isUnauthorized());
        assertThat(statusOf(shipmentId)).isEqualTo("PENDING");
    }

    @Test
    void adminsFilterTheWorkQueue() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID firstId = shipmentFor(first, customerId);
        shipmentFor(second, otherCustomerId);
        UUID thirdId = shipmentFor(third, customerId);
        pack(firstId).andExpect(status().isOk());
        pack(thirdId).andExpect(status().isOk());
        ship(thirdId, "Ecom Express", null).andExpect(status().isOk());

        getAs(admin, ADMIN_URL + "?status=PENDING").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(orderNumber(second)));
        getAs(admin, ADMIN_URL + "?carrier=ecom express").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        getAs(admin, ADMIN_URL + "?orderNumber=" + orderNumber(first).toLowerCase()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(firstId.toString()));
        getAs(admin, ADMIN_URL).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
        getAs(admin, ADMIN_URL + "/" + firstId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PACKED"))
                .andExpect(jsonPath("$.data.userId").value(customerId.toString()));
        getAs(admin, ADMIN_URL + "/" + UUID.randomUUID()).andExpect(status().isNotFound());
        getAs(admin, ADMIN_URL + "?status=LOST").andExpect(status().isBadRequest());
    }
}

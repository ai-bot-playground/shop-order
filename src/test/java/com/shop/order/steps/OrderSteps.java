package com.shop.order.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.order.domain.Order;
import com.shop.order.repo.OrderRepository;
import com.shop.order.repo.OutboxRepository;
import com.shop.order.service.OrderService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private final OrderService service;
    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final int port;

    private final Map<String, String> aliases = new HashMap<>();   // "O1" -> real order id
    private final Map<String, String> keyToId = new HashMap<>();    // idempotency key -> order id
    private String currentOrderId;
    private int lastStatus;
    private String secondOrderId;

    public OrderSteps(OrderService service, OrderRepository orders, OutboxRepository outbox,
                      @Value("${local.server.port}") int port) {
        this.service = service;
        this.orders = orders;
        this.outbox = outbox;
        this.port = port;
    }

    // ---- order creation ----

    @When("I POST \\/orders for {int} units of {string} with Idempotency-Key {string}")
    public void postOrder(int qty, String productId, String key) {
        Resp r = post(key, productId, qty);
        lastStatus = r.status;
        currentOrderId = r.body.path("orderId").asText();
        keyToId.put(key, currentOrderId);
    }

    @Given("an order was created with Idempotency-Key {string}")
    public void orderCreatedWithKey(String key) {
        Resp r = post(key, "P1", 1);
        currentOrderId = r.body.path("orderId").asText();
        keyToId.put(key, currentOrderId);
    }

    @When("I POST \\/orders again with Idempotency-Key {string}")
    public void postAgain(String key) {
        Resp r = post(key, "P1", 1);
        lastStatus = r.status;
        secondOrderId = r.body.path("orderId").asText();
    }

    @Then("the same order id is returned")
    public void sameOrderIdReturned() {
        assertThat(secondOrderId).isEqualTo(currentOrderId);
    }

    @Then("only one OrderCreated event has been published")
    public void onlyOneOrderCreated() {
        assertThat(countOutbox("OrderCreated")).isEqualTo(1L);
    }

    @Then("an OrderCreated event is published")
    public void orderCreatedPublished() {
        assertOutbox("OrderCreated", currentOrderId);
    }

    // ---- given orders in a state ----

    @Given("a PENDING order {string}")
    public void pendingOrder(String name) {
        currentOrderId = alias(name, createOrder("P1", 2));
    }

    @Given("a RESERVED order {string}")
    public void reservedOrder(String name) {
        String id = createOrder("P1", 2);
        service.handleInventoryEvent(inventoryEvent("StockReserved", id));
        currentOrderId = alias(name, id);
    }

    @Given("a CONFIRMED order {string}")
    public void confirmedOrder(String name) {
        String id = createOrder("P1", 2);
        service.handleInventoryEvent(inventoryEvent("StockReserved", id));
        service.handlePaymentEvent(paymentEvent("PaymentCompleted", id));
        currentOrderId = alias(name, id);
    }

    // ---- events driving the saga ----

    @When("a StockReserved event arrives for {string}")
    public void stockReserved(String name) {
        currentOrderId = resolve(name);
        service.handleInventoryEvent(inventoryEvent("StockReserved", currentOrderId));
    }

    @When("a StockReservationFailed event arrives for {string}")
    public void stockReservationFailed(String name) {
        currentOrderId = resolve(name);
        service.handleInventoryEvent(inventoryEvent("StockReservationFailed", currentOrderId));
    }

    @When("a PaymentCompleted event arrives for {string}")
    public void paymentCompleted(String name) {
        currentOrderId = resolve(name);
        service.handlePaymentEvent(paymentEvent("PaymentCompleted", currentOrderId));
    }

    @When("a PaymentFailed event arrives for {string}")
    public void paymentFailed(String name) {
        currentOrderId = resolve(name);
        service.handlePaymentEvent(paymentEvent("PaymentFailed", currentOrderId));
    }

    @Given("no payment result arrives within the saga timeout")
    public void noPaymentWithinTimeout() {
        Order o = orders.findById(currentOrderId).orElseThrow();
        o.setPaymentDeadline(OffsetDateTime.now().minusSeconds(1));
        orders.save(o);
    }

    @When("the stuck-saga scanner runs")
    public void scannerRuns() {
        service.cancelTimedOutSagas();
    }

    @When("I GET \\/orders\\/{word}")
    public void getOrder(String name) {
        currentOrderId = resolve(name);
        lastStatus = get("/orders/" + currentOrderId).status;
    }

    // ---- assertions ----

    @Then("the response status is {int}")
    public void responseStatusIs(int code) {
        assertThat(lastStatus).isEqualTo(code);
    }

    @Then("the order status is {word}")
    public void orderStatusIs(String expected) {
        assertThat(get("/orders/" + currentOrderId).body.path("status").asText()).isEqualTo(expected);
    }

    @Then("a PaymentRequested event is published for {string}")
    public void paymentRequestedPublished(String name) {
        assertOutbox("PaymentRequested", resolve(name));
    }

    @Then("an OrderRejected event is published for {string}")
    public void orderRejectedPublished(String name) {
        assertOutbox("OrderRejected", resolve(name));
    }

    @Then("an OrderConfirmed event is published for {string}")
    public void orderConfirmedPublished(String name) {
        assertOutbox("OrderConfirmed", resolve(name));
    }

    @Then("a ReleaseStock command is published for {string}")
    public void releaseStockPublished(String name) {
        assertOutbox("ReleaseStock", resolve(name));
    }

    @Then("an OrderCancelled event is published for {string}")
    public void orderCancelledPublished(String name) {
        assertOutbox("OrderCancelled", resolve(name));
    }

    @Then("no ReleaseStock command is published for {string}")
    public void noReleaseStock(String name) {
        boolean any = outbox.findAll().stream()
                .anyMatch(o -> o.getType().equals("ReleaseStock") && payloadOrderId(o.getPayload()).equals(resolve(name)));
        assertThat(any).isFalse();
    }

    // ---- helpers ----

    private String createOrder(String productId, int qty) {
        return post(null, productId, qty).body.path("orderId").asText();
    }

    private String alias(String name, String id) {
        aliases.put(name, id);
        return id;
    }

    private String resolve(String name) {
        return aliases.getOrDefault(name, name);
    }

    private void assertOutbox(String type, String orderId) {
        boolean found = outbox.findAll().stream()
                .anyMatch(o -> o.getType().equals(type) && payloadOrderId(o.getPayload()).equals(orderId));
        assertThat(found).as("%s for order %s in outbox", type, orderId).isTrue();
    }

    private long countOutbox(String type) {
        return outbox.findAll().stream().filter(o -> o.getType().equals(type)).count();
    }

    private String payloadOrderId(String payload) {
        try {
            return MAPPER.readTree(payload).path("orderId").asText();
        } catch (Exception e) {
            return "";
        }
    }

    private String inventoryEvent(String type, String orderId) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"" + type + "\",\"orderId\":\"" + orderId + "\"}";
    }

    private String paymentEvent(String type, String orderId) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"" + type + "\",\"orderId\":\"" + orderId + "\"}";
    }

    private Resp post(String key, String productId, int qty) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/orders"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"productId\":\"" + productId + "\",\"quantity\":" + qty + "}"));
            if (key != null) {
                b.header("Idempotency-Key", key);
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(r.statusCode(), MAPPER.readTree(r.body()));
        } catch (Exception e) {
            throw new RuntimeException("POST /orders failed", e);
        }
    }

    private Resp get(String path) {
        try {
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = (r.body() == null || r.body().isBlank()) ? MAPPER.createObjectNode() : MAPPER.readTree(r.body());
            return new Resp(r.statusCode(), body);
        } catch (Exception e) {
            throw new RuntimeException("GET " + path + " failed", e);
        }
    }

    private record Resp(int status, JsonNode body) {
    }
}

# shop-order

Saga orchestrator for the shop platform. Drives the distributed order state machine and compensates on failure.  
Stack: **Spring Boot 4 / Java 25 · PostgreSQL · Kafka · Flyway**.

## Order state machine

```
PENDING ──StockReserved──> RESERVED ──PaymentCompleted──> CONFIRMED
   │                           │
   │  StockReservationFailed   │  PaymentFailed / timeout
   ▼                           ▼
REJECTED                    CANCELLED  (+ ReleaseStock emitted)
```

## REST API

| Method | Path           | Notes                                                   |
|--------|----------------|---------------------------------------------------------|
| POST   | `/orders`      | body: `{productId, quantity}`; optional `Idempotency-Key` header |
| GET    | `/orders/{id}` | returns `{orderId, status}`                             |

`Idempotency-Key` is optional. When provided, a duplicate request returns the existing order instead of creating a new one.

## Kafka topics

| Direction | Topic              | Event types                                               |
|-----------|--------------------|-----------------------------------------------------------|
| Publishes | `order-events`     | `OrderCreated`, `OrderConfirmed`, `OrderCancelled`, `OrderRejected`, `ReleaseStock` |
| Publishes | `payment-events`   | `PaymentRequested`                                        |
| Consumes  | `inventory-events` | `StockReserved`, `StockReservationFailed`                 |
| Consumes  | `payment-events`   | `PaymentCompleted`, `PaymentFailed`                       |

Consumer group: `shop-order` (default, overridable via `SPRING_KAFKA_CONSUMER_GROUP_ID`).

## Database schema (Flyway `V1__init.sql`)

- `orders` — order state + `payment_deadline` for saga timeout
- `outbox` — transactional outbox; polled every 1 s, publishes top 100 unpublished events
- `processed_events` — consumer-side deduplication by `eventId`

## Configuration

| Env var                            | Default              | Description                          |
|------------------------------------|----------------------|--------------------------------------|
| `SPRING_DATASOURCE_URL`            | —                    | JDBC URL (PostgreSQL)                |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS`   | —                    | Kafka broker address                 |
| `SPRING_KAFKA_CONSUMER_GROUP_ID`   | `shop-order`         | Consumer group                       |
| `SAGA_PAYMENT_TIMEOUT_SECONDS`     | `30`                 | Payment deadline after stock reserve |
| `CATALOG_SERVICE_URI`              | `http://shop-catalog:8080` | Upstream catalog service       |

## Build & run

```bash
# Build fat jar
./gradlew bootJar

# Build Docker image
docker build -t shop-order:0.0.1 .

# Run tests (Testcontainers spins up PostgreSQL automatically)
./gradlew test
```

## Kubernetes (preprod)

```bash
kubectl --context kind-preprod apply -f k8s/shop-order.yaml
```

Deploys to namespace `shop` with liveness/readiness probes on `/actuator/health`.

## CI

Every PR triggers the cross-service preprod acceptance suite via `.github/workflows/pr-to-main.yml` (reuses `ai-bot-playground/shop-acceptance-tests/.github/workflows/gate.yml@main`).

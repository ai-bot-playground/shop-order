Feature: Order saga orchestration
  shop-order orchestrates the purchase as a saga and compensates on failure.

  Scenario: Creating an order starts the saga
    When I POST /orders for 2 units of "P1" with Idempotency-Key "K1"
    Then the response status is 202
    And the order status is PENDING
    And an OrderCreated event is published

  Scenario: Repeating the same Idempotency-Key returns the existing order
    Given an order was created with Idempotency-Key "K1"
    When I POST /orders again with Idempotency-Key "K1"
    Then the same order id is returned
    And only one OrderCreated event has been published

  Scenario: Stock reserved moves the order to RESERVED and requests payment
    Given a PENDING order "O1"
    When a StockReserved event arrives for "O1"
    Then the order status is RESERVED
    And a PaymentRequested event is published for "O1"

  Scenario: Stock reservation failure rejects the order (forward recovery)
    Given a PENDING order "O2"
    When a StockReservationFailed event arrives for "O2"
    Then the order status is REJECTED
    And an OrderRejected event is published for "O2"
    And no ReleaseStock command is published for "O2"

  Scenario: Payment completion confirms the order
    Given a RESERVED order "O1"
    When a PaymentCompleted event arrives for "O1"
    Then the order status is CONFIRMED
    And an OrderConfirmed event is published for "O1"

  Scenario: Payment failure cancels the order and compensates
    Given a RESERVED order "O1"
    When a PaymentFailed event arrives for "O1"
    Then the order status is CANCELLED
    And a ReleaseStock command is published for "O1"
    And an OrderCancelled event is published for "O1"

  Scenario: Payment timeout triggers compensation
    Given a RESERVED order "O1"
    And no payment result arrives within the saga timeout
    When the stuck-saga scanner runs
    Then the order status is CANCELLED
    And a ReleaseStock command is published for "O1"

  Scenario: Querying order status
    Given a CONFIRMED order "O1"
    When I GET /orders/O1
    Then the response status is 200
    And the order status is CONFIRMED

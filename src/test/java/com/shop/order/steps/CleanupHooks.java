package com.shop.order.steps;

import com.shop.order.repo.OrderRepository;
import com.shop.order.repo.OutboxRepository;
import com.shop.order.repo.ProcessedEventRepository;
import io.cucumber.java.Before;

public class CleanupHooks {

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final ProcessedEventRepository processed;

    public CleanupHooks(OrderRepository orders, OutboxRepository outbox, ProcessedEventRepository processed) {
        this.orders = orders;
        this.outbox = outbox;
        this.processed = processed;
    }

    @Before
    public void clean() {
        outbox.deleteAll();
        processed.deleteAll();
        orders.deleteAll();
    }
}

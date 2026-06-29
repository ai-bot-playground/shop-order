package com.shop.order.saga;

import com.shop.order.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically cancels RESERVED orders whose payment did not arrive in time.
 * Disabled in component tests, which invoke OrderService.cancelTimedOutSagas()
 * explicitly.
 */
@Component
@ConditionalOnProperty(name = "shop.saga.timeout-scanner-enabled", havingValue = "true", matchIfMissing = true)
public class SagaTimeoutScanner {

    private final OrderService service;

    public SagaTimeoutScanner(OrderService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${shop.saga.scan-ms:5000}")
    public void scan() {
        service.cancelTimedOutSagas();
    }
}

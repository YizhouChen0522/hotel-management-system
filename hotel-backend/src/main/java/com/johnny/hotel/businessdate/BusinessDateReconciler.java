package com.johnny.hotel.businessdate;

import com.johnny.hotel.nightaudit.NightAuditService;
import com.johnny.hotel.nightaudit.NightAuditSystemActor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hotel.time.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
@Order(100)
@RequiredArgsConstructor
public class BusinessDateReconciler implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BusinessDateReconciler.class);
    private final HotelTimeAuthority time;
    private final BusinessDateService dates;
    private final NightAuditService audits;
    private final HotelTimeContextService context;
    private final NightAuditSystemActor systemActor;

    @Override
    public void run(ApplicationArguments args) {
        reconcile();
    }

    @Scheduled(fixedDelayString = "${hotel.time.reconciliation-delay:1m}",
            initialDelayString = "${hotel.time.reconciliation-delay:1m}")
    public synchronized void reconcile() {
        dates.initializeOnce();
        var today = time.hotelDate();
        var businessDate = dates.current().getBusinessDate();
        if (businessDate.isAfter(today)) {
            log.error("Business Date {} is ahead of Hotel Date {}", businessDate, today);
            return;
        }
        if (businessDate.equals(today)) {
            return;
        }
        for (int day = 0; day < 366 && businessDate.isBefore(today); day++) {
            try {
                var result = audits.execute("hotel-time-reconcile-" + businessDate, systemActor.id());
                if (result.run() == null || !"COMPLETED".equals(result.run().getStatus())) {
                    log.error("Business Date catch-up stopped on {}: {}", businessDate, context.current().reason());
                    return;
                }
                businessDate = dates.current().getBusinessDate();
            } catch (Exception failure) {
                log.error("Business Date catch-up failed on {}", businessDate, failure);
                return;
            }
        }
        if (businessDate.isBefore(today)) {
            log.error("Business Date catch-up is incomplete at {}; Hotel Date is {}", businessDate, today);
        }
    }
}

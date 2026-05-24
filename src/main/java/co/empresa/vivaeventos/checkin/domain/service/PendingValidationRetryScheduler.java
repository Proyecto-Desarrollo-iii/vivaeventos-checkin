package co.empresa.vivaeventos.checkin.domain.service;

import co.empresa.vivaeventos.checkin.config.InternalJwtIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PendingValidationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingValidationRetryScheduler.class);
    private static final String MDC_KEY = "correlationId";

    private final ICheckinService checkinService;
    private final InternalJwtIssuer jwtIssuer;

    public PendingValidationRetryScheduler(ICheckinService checkinService, InternalJwtIssuer jwtIssuer) {
        this.checkinService = checkinService;
        this.jwtIssuer = jwtIssuer;
    }

    @Scheduled(fixedDelayString = "${checkin.pending-sync.retry-interval-ms:30000}",
               initialDelayString = "${checkin.pending-sync.retry-interval-ms:30000}")
    public void reconcilePendingValidations() {
        String runId = "retry-" + UUID.randomUUID();
        MDC.put(MDC_KEY, runId);
        try {
            String token = "Bearer " + jwtIssuer.issueServiceToken();
            int reconciled = checkinService.retryPendingMarkUsed(token);
            if (reconciled > 0) {
                log.info("Reconciliadas {} validaciones pendientes", reconciled);
            }
        } catch (RuntimeException e) {
            log.warn("Falla reintento de validaciones pendientes: {}", e.getMessage());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}

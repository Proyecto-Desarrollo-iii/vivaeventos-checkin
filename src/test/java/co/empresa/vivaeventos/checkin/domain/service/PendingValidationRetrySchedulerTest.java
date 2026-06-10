package co.empresa.vivaeventos.checkin.domain.service;

import co.empresa.vivaeventos.checkin.config.InternalJwtIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingValidationRetrySchedulerTest {

    @Mock
    private ICheckinService checkinService;

    @Mock
    private InternalJwtIssuer jwtIssuer;

    @InjectMocks
    private PendingValidationRetryScheduler scheduler;

    @Test
    void reconcilePendingValidations_callsServiceWithToken() {
        when(jwtIssuer.issueServiceToken()).thenReturn("internal-jwt");
        when(checkinService.retryPendingMarkUsed("Bearer internal-jwt")).thenReturn(5);

        scheduler.reconcilePendingValidations();

        verify(checkinService).retryPendingMarkUsed("Bearer internal-jwt");
    }

    @Test
    void reconcilePendingValidations_handlesRuntimeException() {
        when(jwtIssuer.issueServiceToken()).thenReturn("internal-jwt");
        doThrow(new RuntimeException("fallo")).when(checkinService).retryPendingMarkUsed(anyString());

        scheduler.reconcilePendingValidations();
    }
}

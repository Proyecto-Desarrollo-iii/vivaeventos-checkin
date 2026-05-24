package co.empresa.vivaeventos.checkin.delivery.rest;

import co.empresa.vivaeventos.checkin.config.CorrelationIdFilter;
import co.empresa.vivaeventos.checkin.domain.model.Dto.EventStatsResponse;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineSyncRequest;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineSyncResponse;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineValidationItem;
import co.empresa.vivaeventos.checkin.domain.model.Dto.ValidateTicketRequest;
import co.empresa.vivaeventos.checkin.domain.model.Dto.ValidationResponse;
import co.empresa.vivaeventos.checkin.domain.model.ValidationResult;
import co.empresa.vivaeventos.checkin.domain.service.ICheckinService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckinControllerTest {

    @Mock
    private ICheckinService checkinService;

    @InjectMocks
    private CheckinController controller;

    private static final String AUTH = "Bearer test-token";
    private static final String CID = "corr-abc";

    private HttpServletRequest reqWithCorrelation() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(CorrelationIdFilter.REQUEST_ATTR, CID);
        return req;
    }

    @Test
    void validate_returnsOkWhenSuccess() {
        ValidateTicketRequest req = new ValidateTicketRequest();
        req.setQrCode("QR-1");
        ValidationResponse vr = sampleResponse(ValidationResult.SUCCESS, "Ingreso autorizado", false);
        when(checkinService.validateTicket(any(ValidateTicketRequest.class), eq(AUTH), eq(CID))).thenReturn(vr);

        ResponseEntity<Map<String, Object>> response = controller.validate(req, AUTH, reqWithCorrelation());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("autorizado"));
        assertEquals(CID, response.getBody().get("correlationId"));
    }

    @Test
    void validate_returnsConflictWhenAlreadyUsed() {
        ValidateTicketRequest req = new ValidateTicketRequest();
        req.setQrCode("QR-2");
        ValidationResponse vr = sampleResponse(ValidationResult.ALREADY_USED, "La boleta ya fue utilizada", false);
        when(checkinService.validateTicket(any(ValidateTicketRequest.class), eq(AUTH), eq(CID))).thenReturn(vr);

        ResponseEntity<Map<String, Object>> response = controller.validate(req, AUTH, reqWithCorrelation());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(false, response.getBody().get("autorizado"));
    }

    @Test
    void sync_returnsOkWithSummary() {
        OfflineValidationItem item = new OfflineValidationItem();
        item.setQrCode("QR-OFFLINE");
        item.setValidatedAt(LocalDateTime.now());
        OfflineSyncRequest req = new OfflineSyncRequest();
        req.setValidations(List.of(item));

        OfflineSyncResponse summary = new OfflineSyncResponse(1, 1, 0, 0, 0, List.of(
                sampleResponse(ValidationResult.SUCCESS, "Ingreso autorizado", false)
        ));
        when(checkinService.syncOfflineValidations(any(OfflineSyncRequest.class), eq(AUTH), eq(CID))).thenReturn(summary);

        ResponseEntity<Map<String, Object>> response = controller.sync(req, AUTH, reqWithCorrelation());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Sincronizacion offline procesada", response.getBody().get("mensaje"));
        assertEquals(summary, response.getBody().get("resumen"));
    }

    @Test
    void getByTicket_returnsList() {
        UUID id = UUID.randomUUID();
        ValidationResponse vr = sampleResponse(ValidationResult.SUCCESS, null, false);
        when(checkinService.getValidationsByTicket(id)).thenReturn(List.of(vr));

        ResponseEntity<Map<String, Object>> response = controller.getByTicket(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("total"));
    }

    @Test
    void getByEvent_returnsList() {
        UUID eventId = UUID.randomUUID();
        ValidationResponse vr = sampleResponse(ValidationResult.SUCCESS, null, false);
        when(checkinService.getValidationsByEvent(eventId)).thenReturn(List.of(vr, vr));

        ResponseEntity<Map<String, Object>> response = controller.getByEvent(eventId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().get("total"));
    }

    @Test
    void getStats_returnsStatsPayload() {
        UUID eventId = UUID.randomUUID();
        EventStatsResponse stats = new EventStatsResponse(eventId, 10, 8, 1, 0, 1, 0, Map.of("Puerta 1", 8L), LocalDateTime.now());
        when(checkinService.getEventStats(eventId)).thenReturn(stats);

        ResponseEntity<Map<String, Object>> response = controller.getStats(eventId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(stats, response.getBody().get("stats"));
    }

    @Test
    void retryPending_returnsCount() {
        when(checkinService.retryPendingMarkUsed(AUTH)).thenReturn(5);

        ResponseEntity<Map<String, Object>> response = controller.retryPending(AUTH);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("reconciliadas"));
    }

    private ValidationResponse sampleResponse(ValidationResult result, String message, boolean pending) {
        return new ValidationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Festival Demo",
                "VIP",
                "Ana",
                "ana@test.com",
                "QR-X",
                result,
                message,
                "Puerta 1",
                "logistica-01",
                "device-01",
                false,
                pending,
                CID,
                LocalDateTime.now()
        );
    }
}

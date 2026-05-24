package co.empresa.vivaeventos.checkin.delivery.rest;

import co.empresa.vivaeventos.checkin.config.CorrelationIdFilter;
import co.empresa.vivaeventos.checkin.domain.model.Dto.EventStatsResponse;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineSyncRequest;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineSyncResponse;
import co.empresa.vivaeventos.checkin.domain.model.Dto.ValidateTicketRequest;
import co.empresa.vivaeventos.checkin.domain.model.Dto.ValidationResponse;
import co.empresa.vivaeventos.checkin.domain.model.ValidationResult;
import co.empresa.vivaeventos.checkin.domain.service.ICheckinService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkin")
public class CheckinController {

    private final ICheckinService checkinService;

    public CheckinController(ICheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @Valid @RequestBody ValidateTicketRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            HttpServletRequest httpRequest) {

        String correlationId = (String) httpRequest.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
        ValidationResponse validation = checkinService.validateTicket(request, authorization, correlationId);

        Map<String, Object> response = new HashMap<>();
        response.put("validacion", validation);
        response.put("autorizado", validation.result() == ValidationResult.SUCCESS);
        response.put("correlationId", correlationId);

        HttpStatus status = validation.result() == ValidationResult.SUCCESS
                ? HttpStatus.OK
                : HttpStatus.CONFLICT;

        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @Valid @RequestBody OfflineSyncRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            HttpServletRequest httpRequest) {

        String correlationId = (String) httpRequest.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
        OfflineSyncResponse result = checkinService.syncOfflineValidations(request, authorization, correlationId);

        Map<String, Object> response = new HashMap<>();
        response.put("mensaje", "Sincronizacion offline procesada");
        response.put("resumen", result);
        response.put("correlationId", correlationId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/retry-pending")
    public ResponseEntity<Map<String, Object>> retryPending(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {

        int reconciled = checkinService.retryPendingMarkUsed(authorization);

        Map<String, Object> response = new HashMap<>();
        response.put("mensaje", "Reintento de validaciones pendientes ejecutado");
        response.put("reconciliadas", reconciled);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/validations/ticket/{ticketId}")
    public ResponseEntity<Map<String, Object>> getByTicket(@PathVariable UUID ticketId) {
        List<ValidationResponse> validations = checkinService.getValidationsByTicket(ticketId);

        Map<String, Object> response = new HashMap<>();
        response.put("validaciones", validations);
        response.put("total", validations.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/validations/event/{eventId}")
    public ResponseEntity<Map<String, Object>> getByEvent(@PathVariable UUID eventId) {
        List<ValidationResponse> validations = checkinService.getValidationsByEvent(eventId);

        Map<String, Object> response = new HashMap<>();
        response.put("validaciones", validations);
        response.put("total", validations.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/event/{eventId}")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable UUID eventId) {
        EventStatsResponse stats = checkinService.getEventStats(eventId);

        Map<String, Object> response = new HashMap<>();
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }
}

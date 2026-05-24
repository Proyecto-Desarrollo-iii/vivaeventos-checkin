package co.empresa.vivaeventos.checkin.domain.service;

import co.empresa.vivaeventos.checkin.domain.model.Dto.EventStatsResponse;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineSyncRequest;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineSyncResponse;
import co.empresa.vivaeventos.checkin.domain.model.Dto.ValidateTicketRequest;
import co.empresa.vivaeventos.checkin.domain.model.Dto.ValidationResponse;

import java.util.List;
import java.util.UUID;

public interface ICheckinService {

    ValidationResponse validateTicket(ValidateTicketRequest request, String bearerToken, String correlationId);

    OfflineSyncResponse syncOfflineValidations(OfflineSyncRequest request, String bearerToken, String correlationId);

    List<ValidationResponse> getValidationsByTicket(UUID ticketId);

    List<ValidationResponse> getValidationsByEvent(UUID eventId);

    EventStatsResponse getEventStats(UUID eventId);

    int retryPendingMarkUsed(String bearerToken);
}

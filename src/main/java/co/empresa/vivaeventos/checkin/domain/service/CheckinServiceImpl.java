package co.empresa.vivaeventos.checkin.domain.service;

import co.empresa.vivaeventos.checkin.domain.model.Dto.EventStatsResponse;
import co.empresa.vivaeventos.checkin.domain.model.Dto.IssuedTicketView;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineSyncRequest;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineSyncResponse;
import co.empresa.vivaeventos.checkin.domain.model.Dto.OfflineValidationItem;
import co.empresa.vivaeventos.checkin.domain.model.Dto.ValidateTicketRequest;
import co.empresa.vivaeventos.checkin.domain.model.Dto.ValidationResponse;
import co.empresa.vivaeventos.checkin.domain.model.TicketStatus;
import co.empresa.vivaeventos.checkin.domain.model.TicketValidation;
import co.empresa.vivaeventos.checkin.domain.model.ValidationResult;
import co.empresa.vivaeventos.checkin.domain.repository.ITicketValidationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CheckinServiceImpl implements ICheckinService {

    private static final Logger log = LoggerFactory.getLogger(CheckinServiceImpl.class);

    private final ITicketValidationRepository validationRepository;
    private final ITicketsClient ticketsClient;
    private final boolean degradedModeEnabled;

    public CheckinServiceImpl(ITicketValidationRepository validationRepository,
                              ITicketsClient ticketsClient,
                              @Value("${checkin.degraded-mode.enabled:true}") boolean degradedModeEnabled) {
        this.validationRepository = validationRepository;
        this.ticketsClient = ticketsClient;
        this.degradedModeEnabled = degradedModeEnabled;
    }

    @Override
    @Transactional
    public ValidationResponse validateTicket(ValidateTicketRequest request, String bearerToken, String correlationId) {
        return runValidation(
                request.getQrCode(),
                request.getGateLocation(),
                request.getValidatedBy(),
                request.getDeviceId(),
                null,
                false,
                bearerToken,
                correlationId);
    }

    @Override
    @Transactional
    public OfflineSyncResponse syncOfflineValidations(OfflineSyncRequest request, String bearerToken, String correlationId) {
        List<OfflineValidationItem> items = new ArrayList<>(request.getValidations());
        items.sort(Comparator.comparing(OfflineValidationItem::getValidatedAt));

        List<ValidationResponse> processed = new ArrayList<>(items.size());
        int success = 0, alreadyUsed = 0, revoked = 0, notFound = 0;

        for (OfflineValidationItem item : items) {
            ValidationResponse vr = runValidation(
                    item.getQrCode(),
                    item.getGateLocation(),
                    item.getValidatedBy(),
                    item.getDeviceId(),
                    item.getValidatedAt(),
                    true,
                    bearerToken,
                    correlationId);
            processed.add(vr);
            switch (vr.result()) {
                case SUCCESS -> success++;
                case ALREADY_USED -> alreadyUsed++;
                case REVOKED -> revoked++;
                case NOT_FOUND -> notFound++;
            }
        }
        return new OfflineSyncResponse(items.size(), success, alreadyUsed, revoked, notFound, processed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ValidationResponse> getValidationsByTicket(UUID ticketId) {
        List<TicketValidation> validations = validationRepository.findByIssuedTicketIdOrderByValidatedAtDesc(ticketId);
        if (validations.isEmpty()) {
            throw new ValidationNotFoundException("No hay validaciones registradas para la boleta: " + ticketId);
        }
        return validations.stream()
                .map(v -> buildResponse(v, null, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ValidationResponse> getValidationsByEvent(UUID eventId) {
        return validationRepository.findByEventIdOrderByValidatedAtDesc(eventId).stream()
                .map(v -> buildResponse(v, null, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventStatsResponse getEventStats(UUID eventId) {
        long success = validationRepository.countByEventIdAndResult(eventId, ValidationResult.SUCCESS);
        long alreadyUsed = validationRepository.countByEventIdAndResult(eventId, ValidationResult.ALREADY_USED);
        long revoked = validationRepository.countByEventIdAndResult(eventId, ValidationResult.REVOKED);
        long notFound = validationRepository.countByEventIdAndResult(eventId, ValidationResult.NOT_FOUND);
        long pending = validationRepository.countByEventIdAndPendingMarkUsedTrue(eventId);

        Map<String, Long> porPuerta = new LinkedHashMap<>();
        for (Object[] row : validationRepository.sumByGateForSuccess(eventId)) {
            porPuerta.put((String) row[0], ((Number) row[1]).longValue());
        }

        LocalDateTime ultima = validationRepository.findByEventIdOrderByValidatedAtDesc(eventId).stream()
                .findFirst()
                .map(TicketValidation::getValidatedAt)
                .orElse(null);

        return new EventStatsResponse(
                eventId,
                success + alreadyUsed + revoked + notFound,
                success,
                alreadyUsed,
                revoked,
                notFound,
                pending,
                porPuerta,
                ultima
        );
    }

    @Override
    @Transactional
    public int retryPendingMarkUsed(String bearerToken) {
        List<TicketValidation> pending = validationRepository.findTop50ByPendingMarkUsedTrueOrderByValidatedAtAsc();
        int reconciled = 0;
        for (TicketValidation v : pending) {
            if (v.getIssuedTicketId() == null) {
                continue;
            }
            try {
                ticketsClient.markAsUsed(v.getIssuedTicketId(), bearerToken);
                v.setPendingMarkUsed(false);
                validationRepository.save(v);
                reconciled++;
            } catch (TicketsUnavailableException | TicketsClientException e) {
                log.warn("Pendiente sigue sin poder reconciliarse validationId={} ticketId={} cause={}",
                        v.getId(), v.getIssuedTicketId(), e.getMessage());
            }
        }
        return reconciled;
    }

    private ValidationResponse runValidation(String qrCode,
                                             String gateLocation,
                                             String validatedBy,
                                             String deviceId,
                                             LocalDateTime occurredAt,
                                             boolean offline,
                                             String bearerToken,
                                             String correlationId) {
        TicketValidation validation = new TicketValidation();
        validation.setQrCode(qrCode);
        validation.setGateLocation(gateLocation);
        validation.setValidatedBy(validatedBy);
        validation.setDeviceId(deviceId);
        validation.setSyncedFromOffline(offline);
        validation.setCorrelationId(correlationId);
        if (offline && occurredAt != null) {
            validation.setValidatedAt(occurredAt);
        }

        Optional<IssuedTicketView> opt;
        try {
            opt = ticketsClient.findByQrCode(qrCode, bearerToken);
        } catch (TicketsUnavailableException e) {
            return handleDegradedLookup(validation, qrCode, e);
        }

        if (opt.isEmpty()) {
            validation.setResult(ValidationResult.NOT_FOUND);
            TicketValidation saved = validationRepository.save(validation);
            return buildResponse(saved, null, "QR no corresponde a una boleta emitida");
        }

        IssuedTicketView ticket = opt.get();
        validation.setIssuedTicketId(ticket.id());
        validation.setEventId(ticket.eventId());

        if (ticket.status() == TicketStatus.REVOKED) {
            validation.setResult(ValidationResult.REVOKED);
            TicketValidation saved = validationRepository.save(validation);
            return buildResponse(saved, ticket, "La boleta fue revocada");
        }

        boolean alreadyConsumed = ticket.status() == TicketStatus.USED
                || validationRepository.existsByQrCodeAndResult(qrCode, ValidationResult.SUCCESS);
        if (alreadyConsumed) {
            validation.setResult(ValidationResult.ALREADY_USED);
            TicketValidation saved = validationRepository.save(validation);
            return buildResponse(saved, ticket, "La boleta ya fue utilizada");
        }

        try {
            IssuedTicketView marked = ticketsClient.markAsUsed(ticket.id(), bearerToken);
            validation.setResult(ValidationResult.SUCCESS);
            validation.setPendingMarkUsed(false);
            TicketValidation saved = validationRepository.save(validation);
            return buildResponse(saved, marked != null ? marked : ticket, "Ingreso autorizado");
        } catch (TicketsUnavailableException e) {
            return handleDegradedMark(validation, ticket, e);
        }
    }

    private ValidationResponse handleDegradedLookup(TicketValidation validation, String qrCode, TicketsUnavailableException cause) {
        if (!degradedModeEnabled) {
            throw cause;
        }
        if (validationRepository.existsByQrCodeAndResult(qrCode, ValidationResult.SUCCESS)) {
            validation.setResult(ValidationResult.ALREADY_USED);
            TicketValidation saved = validationRepository.save(validation);
            log.warn("Modo degradado: QR ya consumido localmente qr={} correlationId={}", qrCode, validation.getCorrelationId());
            return buildResponse(saved, null, "La boleta ya fue utilizada (validacion local sin boleteria)");
        }
        validation.setResult(ValidationResult.SUCCESS);
        validation.setPendingMarkUsed(true);
        TicketValidation saved = validationRepository.save(validation);
        log.warn("Modo degradado: autorizando sin lookup qr={} correlationId={} cause={}", qrCode, validation.getCorrelationId(), cause.getMessage());
        return buildResponse(saved, null, "Ingreso autorizado (modo degradado, pendiente de sincronizacion)");
    }

    private ValidationResponse handleDegradedMark(TicketValidation validation, IssuedTicketView ticket, TicketsUnavailableException cause) {
        if (!degradedModeEnabled) {
            throw cause;
        }
        validation.setResult(ValidationResult.SUCCESS);
        validation.setPendingMarkUsed(true);
        TicketValidation saved = validationRepository.save(validation);
        log.warn("Modo degradado: autorizado sin mark-used ticketId={} correlationId={} cause={}",
                ticket.id(), validation.getCorrelationId(), cause.getMessage());
        return buildResponse(saved, ticket, "Ingreso autorizado (pendiente de sincronizacion con boleteria)");
    }

    private ValidationResponse buildResponse(TicketValidation validation, IssuedTicketView ticket, String message) {
        return new ValidationResponse(
                validation.getId(),
                validation.getIssuedTicketId(),
                validation.getEventId(),
                ticket != null ? ticket.eventName() : null,
                ticket != null ? ticket.ticketType() : null,
                ticket != null ? ticket.holderName() : null,
                ticket != null ? ticket.holderEmail() : null,
                validation.getQrCode(),
                validation.getResult(),
                message,
                validation.getGateLocation(),
                validation.getValidatedBy(),
                validation.getDeviceId(),
                validation.isSyncedFromOffline(),
                validation.isPendingMarkUsed(),
                validation.getCorrelationId(),
                validation.getValidatedAt()
        );
    }
}

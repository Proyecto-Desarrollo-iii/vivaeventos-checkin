package co.empresa.vivaeventos.checkin.domain.model.Dto;

import co.empresa.vivaeventos.checkin.domain.model.TicketStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IssuedTicketView(
        UUID id,
        UUID orderId,
        UUID eventId,
        String eventName,
        UUID ticketTypeId,
        String ticketType,
        String holderName,
        String holderEmail,
        String holderDocument,
        BigDecimal price,
        String qrCode,
        TicketStatus status,
        LocalDateTime issuedAt,
        LocalDateTime usedAt,
        LocalDateTime revokedAt,
        String revokedReason
) {}

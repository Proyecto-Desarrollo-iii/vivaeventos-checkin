package co.empresa.vivaeventos.checkin.domain.service;

import co.empresa.vivaeventos.checkin.domain.model.Dto.IssuedTicketView;

import java.util.Optional;
import java.util.UUID;

public interface ITicketsClient {

    Optional<IssuedTicketView> findByQrCode(String qrCode, String bearerToken);

    IssuedTicketView markAsUsed(UUID ticketId, String bearerToken);
}

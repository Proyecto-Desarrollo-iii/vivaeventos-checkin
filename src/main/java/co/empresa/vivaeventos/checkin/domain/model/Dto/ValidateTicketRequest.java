package co.empresa.vivaeventos.checkin.domain.model.Dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ValidateTicketRequest {

    @NotBlank(message = "qrCode es obligatorio")
    private String qrCode;

    private UUID eventId;

    private String gateLocation;

    private String validatedBy;

    private String deviceId;
}

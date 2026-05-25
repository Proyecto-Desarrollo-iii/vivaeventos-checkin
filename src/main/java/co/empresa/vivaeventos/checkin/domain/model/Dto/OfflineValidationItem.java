package co.empresa.vivaeventos.checkin.domain.model.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class OfflineValidationItem {

    @NotBlank(message = "qrCode es obligatorio")
    private String qrCode;

    @NotNull(message = "validatedAt es obligatorio")
    private LocalDateTime validatedAt;

    private UUID eventId;

    private String gateLocation;

    private String validatedBy;

    private String deviceId;
}

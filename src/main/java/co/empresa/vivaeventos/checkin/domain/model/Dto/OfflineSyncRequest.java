package co.empresa.vivaeventos.checkin.domain.model.Dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OfflineSyncRequest {

    @NotEmpty(message = "validations no puede estar vacio")
    @Valid
    private List<OfflineValidationItem> validations;
}

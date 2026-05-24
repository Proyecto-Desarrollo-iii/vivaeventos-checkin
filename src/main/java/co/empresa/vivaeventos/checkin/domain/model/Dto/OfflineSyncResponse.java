package co.empresa.vivaeventos.checkin.domain.model.Dto;

import java.util.List;

public record OfflineSyncResponse(
        int total,
        int success,
        int alreadyUsed,
        int revoked,
        int notFound,
        List<ValidationResponse> validations
) {}

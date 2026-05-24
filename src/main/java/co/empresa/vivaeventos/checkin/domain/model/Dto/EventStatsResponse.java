package co.empresa.vivaeventos.checkin.domain.model.Dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record EventStatsResponse(
        UUID eventId,
        long totalValidaciones,
        long ingresados,
        long rechazadosYaUsada,
        long rechazadosRevocada,
        long rechazadosNoEncontrada,
        long pendientesSync,
        Map<String, Long> ingresadosPorPuerta,
        LocalDateTime ultimaValidacionEn
) {}

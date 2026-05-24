package co.empresa.vivaeventos.checkin.domain.repository;

import co.empresa.vivaeventos.checkin.domain.model.TicketValidation;
import co.empresa.vivaeventos.checkin.domain.model.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ITicketValidationRepository extends JpaRepository<TicketValidation, UUID> {

    List<TicketValidation> findByIssuedTicketIdOrderByValidatedAtDesc(UUID issuedTicketId);

    List<TicketValidation> findByEventIdOrderByValidatedAtDesc(UUID eventId);

    boolean existsByQrCodeAndResult(String qrCode, ValidationResult result);

    long countByEventIdAndResult(UUID eventId, ValidationResult result);

    long countByEventIdAndPendingMarkUsedTrue(UUID eventId);

    List<TicketValidation> findTop50ByPendingMarkUsedTrueOrderByValidatedAtAsc();

    @Query("""
            SELECT COALESCE(v.gateLocation, 'sin-puerta') AS gate, COUNT(v) AS total
            FROM TicketValidation v
            WHERE v.eventId = :eventId AND v.result = co.empresa.vivaeventos.checkin.domain.model.ValidationResult.SUCCESS
            GROUP BY v.gateLocation
            """)
    List<Object[]> sumByGateForSuccess(UUID eventId);
}

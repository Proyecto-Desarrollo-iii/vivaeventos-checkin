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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckinServiceImplTest {

    @Mock
    private ITicketValidationRepository validationRepository;

    @Mock
    private ITicketsClient ticketsClient;

    private CheckinServiceImpl service;

    private static final String TOKEN = "Bearer test-token";
    private static final String CID = "corr-123";

    private CheckinServiceImpl newService(boolean degraded) {
        return new CheckinServiceImpl(validationRepository, ticketsClient, degraded);
    }

    @Test
    void validate_returnsSuccess_andMarksTicketAsUsed() {
        service = newService(true);
        ValidateTicketRequest req = buildRequest("QR-OK");
        IssuedTicketView ticket = ticketView(TicketStatus.ISSUED);
        when(ticketsClient.findByQrCode("QR-OK", TOKEN)).thenReturn(Optional.of(ticket));
        when(validationRepository.existsByQrCodeAndResult("QR-OK", ValidationResult.SUCCESS)).thenReturn(false);
        when(ticketsClient.markAsUsed(eq(ticket.id()), eq(TOKEN))).thenReturn(ticket);
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> {
            TicketValidation v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        ValidationResponse response = service.validateTicket(req, TOKEN, CID);

        assertEquals(ValidationResult.SUCCESS, response.result());
        assertEquals("Ingreso autorizado", response.message());
        assertFalse(response.pendingMarkUsed());
        assertEquals(CID, response.correlationId());
        verify(ticketsClient).markAsUsed(eq(ticket.id()), eq(TOKEN));
    }

    @Test
    void validate_returnsNotFound_whenQrUnknown() {
        service = newService(true);
        ValidateTicketRequest req = buildRequest("QR-MISSING");
        when(ticketsClient.findByQrCode(eq("QR-MISSING"), eq(TOKEN))).thenReturn(Optional.empty());
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationResponse response = service.validateTicket(req, TOKEN, CID);

        assertEquals(ValidationResult.NOT_FOUND, response.result());
        verify(ticketsClient, never()).markAsUsed(any(), anyString());
    }

    @Test
    void validate_returnsRevoked_whenTicketRevoked() {
        service = newService(true);
        ValidateTicketRequest req = buildRequest("QR-REV");
        IssuedTicketView ticket = ticketView(TicketStatus.REVOKED);
        when(ticketsClient.findByQrCode("QR-REV", TOKEN)).thenReturn(Optional.of(ticket));
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationResponse response = service.validateTicket(req, TOKEN, CID);

        assertEquals(ValidationResult.REVOKED, response.result());
        verify(ticketsClient, never()).markAsUsed(any(), anyString());
    }

    @Test
    void validate_returnsAlreadyUsed_whenTicketStatusUsed() {
        service = newService(true);
        ValidateTicketRequest req = buildRequest("QR-USED");
        IssuedTicketView ticket = ticketView(TicketStatus.USED);
        when(ticketsClient.findByQrCode("QR-USED", TOKEN)).thenReturn(Optional.of(ticket));
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationResponse response = service.validateTicket(req, TOKEN, CID);

        assertEquals(ValidationResult.ALREADY_USED, response.result());
        verify(ticketsClient, never()).markAsUsed(any(), anyString());
    }

    @Test
    void validate_returnsAlreadyUsed_whenLocalSuccessExists() {
        service = newService(true);
        ValidateTicketRequest req = buildRequest("QR-DUP");
        IssuedTicketView ticket = ticketView(TicketStatus.ISSUED);
        when(ticketsClient.findByQrCode("QR-DUP", TOKEN)).thenReturn(Optional.of(ticket));
        when(validationRepository.existsByQrCodeAndResult("QR-DUP", ValidationResult.SUCCESS)).thenReturn(true);
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationResponse response = service.validateTicket(req, TOKEN, CID);

        assertEquals(ValidationResult.ALREADY_USED, response.result());
        verify(ticketsClient, never()).markAsUsed(any(), anyString());
    }

    @Test
    void validate_degradedMode_authorizesWhenLookupFailsAndNotConsumedLocally() {
        service = newService(true);
        ValidateTicketRequest req = buildRequest("QR-DEG");
        when(ticketsClient.findByQrCode(eq("QR-DEG"), eq(TOKEN)))
                .thenThrow(new TicketsUnavailableException("circuit open"));
        when(validationRepository.existsByQrCodeAndResult("QR-DEG", ValidationResult.SUCCESS)).thenReturn(false);
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationResponse response = service.validateTicket(req, TOKEN, CID);

        assertEquals(ValidationResult.SUCCESS, response.result());
        assertTrue(response.pendingMarkUsed());
        assertTrue(response.message().contains("modo degradado"));
        verify(ticketsClient, never()).markAsUsed(any(), anyString());
    }

    @Test
    void validate_degradedMode_rejectsWhenAlreadyConsumedLocally() {
        service = newService(true);
        ValidateTicketRequest req = buildRequest("QR-DEG-DUP");
        when(ticketsClient.findByQrCode(eq("QR-DEG-DUP"), eq(TOKEN)))
                .thenThrow(new TicketsUnavailableException("circuit open"));
        when(validationRepository.existsByQrCodeAndResult("QR-DEG-DUP", ValidationResult.SUCCESS)).thenReturn(true);
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationResponse response = service.validateTicket(req, TOKEN, CID);

        assertEquals(ValidationResult.ALREADY_USED, response.result());
        assertFalse(response.pendingMarkUsed());
    }

    @Test
    void validate_degradedMode_setsPending_whenMarkUsedFails() {
        service = newService(true);
        ValidateTicketRequest req = buildRequest("QR-MARK-FAIL");
        IssuedTicketView ticket = ticketView(TicketStatus.ISSUED);
        when(ticketsClient.findByQrCode(eq("QR-MARK-FAIL"), eq(TOKEN))).thenReturn(Optional.of(ticket));
        when(validationRepository.existsByQrCodeAndResult("QR-MARK-FAIL", ValidationResult.SUCCESS)).thenReturn(false);
        when(ticketsClient.markAsUsed(eq(ticket.id()), eq(TOKEN)))
                .thenThrow(new TicketsUnavailableException("circuit open"));
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationResponse response = service.validateTicket(req, TOKEN, CID);

        assertEquals(ValidationResult.SUCCESS, response.result());
        assertTrue(response.pendingMarkUsed());
        assertTrue(response.message().contains("pendiente"));
    }

    @Test
    void validate_degradedDisabled_propagatesError() {
        service = newService(false);
        ValidateTicketRequest req = buildRequest("QR-X");
        when(ticketsClient.findByQrCode(eq("QR-X"), eq(TOKEN)))
                .thenThrow(new TicketsUnavailableException("circuit open"));

        assertThrows(TicketsUnavailableException.class, () -> service.validateTicket(req, TOKEN, CID));
    }

    @Test
    void syncOffline_processesItemsInTimestampOrder_andPreventsDoubleEntry() {
        service = newService(true);
        UUID ticketId = UUID.randomUUID();
        IssuedTicketView ticket = ticketView(ticketId, TicketStatus.ISSUED);

        OfflineValidationItem older = offlineItem("QR-SAME", LocalDateTime.now().minusMinutes(10));
        OfflineValidationItem newer = offlineItem("QR-SAME", LocalDateTime.now().minusMinutes(2));
        OfflineSyncRequest req = new OfflineSyncRequest();
        req.setValidations(List.of(newer, older));

        when(ticketsClient.findByQrCode(eq("QR-SAME"), eq(TOKEN))).thenReturn(Optional.of(ticket));
        when(validationRepository.existsByQrCodeAndResult("QR-SAME", ValidationResult.SUCCESS))
                .thenReturn(false, true);
        when(ticketsClient.markAsUsed(eq(ticketId), eq(TOKEN))).thenReturn(ticket);

        ArgumentCaptor<TicketValidation> captor = ArgumentCaptor.forClass(TicketValidation.class);
        when(validationRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        OfflineSyncResponse response = service.syncOfflineValidations(req, TOKEN, CID);

        assertEquals(2, response.total());
        assertEquals(1, response.success());
        assertEquals(1, response.alreadyUsed());
        verify(ticketsClient, times(1)).markAsUsed(eq(ticketId), eq(TOKEN));

        List<TicketValidation> saved = captor.getAllValues();
        assertEquals(ValidationResult.SUCCESS, saved.get(0).getResult());
        assertEquals(ValidationResult.ALREADY_USED, saved.get(1).getResult());
        assertTrue(saved.get(0).isSyncedFromOffline());
    }

    @Test
    void getValidationsByTicket_throwsWhenEmpty() {
        service = newService(true);
        UUID id = UUID.randomUUID();
        when(validationRepository.findByIssuedTicketIdOrderByValidatedAtDesc(id)).thenReturn(List.of());

        assertThrows(ValidationNotFoundException.class, () -> service.getValidationsByTicket(id));
    }

    @Test
    void getEventStats_aggregatesCountsAndGates() {
        service = newService(true);
        UUID eventId = UUID.randomUUID();
        when(validationRepository.countByEventIdAndResult(eventId, ValidationResult.SUCCESS)).thenReturn(80L);
        when(validationRepository.countByEventIdAndResult(eventId, ValidationResult.ALREADY_USED)).thenReturn(5L);
        when(validationRepository.countByEventIdAndResult(eventId, ValidationResult.REVOKED)).thenReturn(2L);
        when(validationRepository.countByEventIdAndResult(eventId, ValidationResult.NOT_FOUND)).thenReturn(1L);
        when(validationRepository.countByEventIdAndPendingMarkUsedTrue(eventId)).thenReturn(3L);
        when(validationRepository.sumByGateForSuccess(eventId))
                .thenReturn(List.of(new Object[]{"Puerta 1", 50L}, new Object[]{"Puerta 2", 30L}));
        TicketValidation latest = new TicketValidation();
        latest.setValidatedAt(LocalDateTime.now());
        when(validationRepository.findByEventIdOrderByValidatedAtDesc(eventId)).thenReturn(List.of(latest));

        EventStatsResponse stats = service.getEventStats(eventId);

        assertEquals(88L, stats.totalValidaciones());
        assertEquals(80L, stats.ingresados());
        assertEquals(5L, stats.rechazadosYaUsada());
        assertEquals(3L, stats.pendientesSync());
        assertEquals(50L, stats.ingresadosPorPuerta().get("Puerta 1"));
        assertNotNull(stats.ultimaValidacionEn());
    }

    @Test
    void retryPendingMarkUsed_clearsFlagAfterSuccess() {
        service = newService(true);
        TicketValidation v = new TicketValidation();
        v.setId(UUID.randomUUID());
        v.setIssuedTicketId(UUID.randomUUID());
        v.setPendingMarkUsed(true);
        when(validationRepository.findTop50ByPendingMarkUsedTrueOrderByValidatedAtAsc()).thenReturn(List.of(v));
        when(ticketsClient.markAsUsed(eq(v.getIssuedTicketId()), eq(TOKEN))).thenReturn(null);
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        int reconciled = service.retryPendingMarkUsed(TOKEN);

        assertEquals(1, reconciled);
        assertFalse(v.isPendingMarkUsed());
    }

    @Test
    void syncOffline_processesAllResultTypes() {
        service = newService(true);
        UUID commonEventId = UUID.randomUUID();
        IssuedTicketView ticket = ticketViewWithEvent(commonEventId, TicketStatus.ISSUED);

        OfflineValidationItem success = offlineItem("QR-SUCCESS", LocalDateTime.now().minusMinutes(4));
        OfflineValidationItem used = offlineItem("QR-USED", LocalDateTime.now().minusMinutes(3));
        OfflineValidationItem revoked = offlineItem("QR-REVOKED", LocalDateTime.now().minusMinutes(2));
        OfflineValidationItem notFound = offlineItem("QR-NF", LocalDateTime.now().minusMinutes(1));

        success.setEventId(commonEventId);
        used.setEventId(commonEventId);
        revoked.setEventId(commonEventId);
        notFound.setEventId(commonEventId);

        OfflineSyncRequest req = new OfflineSyncRequest();
        req.setValidations(List.of(success, used, revoked, notFound));

        when(ticketsClient.findByQrCode(eq("QR-SUCCESS"), eq(TOKEN))).thenReturn(Optional.of(ticket));
        when(ticketsClient.findByQrCode(eq("QR-USED"), eq(TOKEN))).thenReturn(Optional.of(ticketViewWithEvent(commonEventId, TicketStatus.USED)));
        when(ticketsClient.findByQrCode(eq("QR-REVOKED"), eq(TOKEN))).thenReturn(Optional.of(ticketViewWithEvent(commonEventId, TicketStatus.REVOKED)));
        when(ticketsClient.findByQrCode(eq("QR-NF"), eq(TOKEN))).thenReturn(Optional.empty());

        when(validationRepository.existsByQrCodeAndResult(anyString(), eq(ValidationResult.SUCCESS))).thenReturn(false);
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        OfflineSyncResponse response = service.syncOfflineValidations(req, TOKEN, CID);

        assertEquals(4, response.total());
        assertEquals(1, response.success());
        assertEquals(1, response.alreadyUsed());
        assertEquals(1, response.revoked());
        assertEquals(1, response.notFound());
    }

    @Test
    void getValidationsByEvent_returnsEmpty_whenNotFound() {
        service = newService(true);
        UUID eventId = UUID.randomUUID();
        when(validationRepository.findByEventIdOrderByValidatedAtDesc(eventId)).thenReturn(List.of());

        List<ValidationResponse> result = service.getValidationsByEvent(eventId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getEventStats_returnsNullLastValidation_whenNoRecords() {
        service = newService(true);
        UUID eventId = UUID.randomUUID();
        when(validationRepository.countByEventIdAndResult(eventId, ValidationResult.SUCCESS)).thenReturn(0L);
        when(validationRepository.countByEventIdAndResult(eventId, ValidationResult.ALREADY_USED)).thenReturn(0L);
        when(validationRepository.countByEventIdAndResult(eventId, ValidationResult.REVOKED)).thenReturn(0L);
        when(validationRepository.countByEventIdAndResult(eventId, ValidationResult.NOT_FOUND)).thenReturn(0L);
        when(validationRepository.countByEventIdAndPendingMarkUsedTrue(eventId)).thenReturn(0L);
        when(validationRepository.sumByGateForSuccess(eventId)).thenReturn(List.of());
        when(validationRepository.findByEventIdOrderByValidatedAtDesc(eventId)).thenReturn(List.of());

        EventStatsResponse stats = service.getEventStats(eventId);

        assertEquals(0L, stats.totalValidaciones());
        assertNull(stats.ultimaValidacionEn());
    }

    @Test
    void validate_returnsWrongEvent_whenEventMismatch() {
        service = newService(true);
        UUID eventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        ValidateTicketRequest req = buildRequest("QR-WRONG-EVENT");
        req.setEventId(otherEventId);
        IssuedTicketView ticket = ticketView(TicketStatus.ISSUED);
        when(ticketsClient.findByQrCode("QR-WRONG-EVENT", TOKEN)).thenReturn(Optional.of(ticket));
        when(validationRepository.save(any(TicketValidation.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationResponse response = service.validateTicket(req, TOKEN, CID);

        assertEquals(ValidationResult.WRONG_EVENT, response.result());
        verify(ticketsClient, never()).markAsUsed(any(), anyString());
    }

    @Test
    void getValidationsByTicket_returnsList_whenFound() {
        service = newService(true);
        UUID ticketId = UUID.randomUUID();
        TicketValidation v = new TicketValidation();
        v.setId(UUID.randomUUID());
        v.setQrCode("QR-TEST");
        v.setResult(ValidationResult.SUCCESS);
        v.setValidatedAt(LocalDateTime.now());
        v.setCorrelationId(CID);
        when(validationRepository.findByIssuedTicketIdOrderByValidatedAtDesc(ticketId)).thenReturn(List.of(v));

        List<ValidationResponse> result = service.getValidationsByTicket(ticketId);

        assertEquals(1, result.size());
        assertEquals(ValidationResult.SUCCESS, result.get(0).result());
        assertEquals(CID, result.get(0).correlationId());
    }

    @Test
    void getValidationsByEvent_returnsList() {
        service = newService(true);
        UUID eventId = UUID.randomUUID();
        TicketValidation v = new TicketValidation();
        v.setId(UUID.randomUUID());
        v.setQrCode("QR-TEST");
        v.setResult(ValidationResult.SUCCESS);
        v.setValidatedAt(LocalDateTime.now());
        v.setCorrelationId(CID);
        when(validationRepository.findByEventIdOrderByValidatedAtDesc(eventId)).thenReturn(List.of(v));

        List<ValidationResponse> result = service.getValidationsByEvent(eventId);

        assertEquals(1, result.size());
        assertEquals(ValidationResult.SUCCESS, result.get(0).result());
    }

    @Test
    void retryPendingMarkUsed_skipsWhenIssuedTicketIdNull() {
        service = newService(true);
        TicketValidation v = new TicketValidation();
        v.setId(UUID.randomUUID());
        v.setIssuedTicketId(null);
        v.setPendingMarkUsed(true);
        when(validationRepository.findTop50ByPendingMarkUsedTrueOrderByValidatedAtAsc()).thenReturn(List.of(v));

        int reconciled = service.retryPendingMarkUsed(TOKEN);

        assertEquals(0, reconciled);
        verify(ticketsClient, never()).markAsUsed(any(), anyString());
    }

    @Test
    void retryPendingMarkUsed_keepsFlagWhenStillFailing() {
        service = newService(true);
        TicketValidation v = new TicketValidation();
        v.setId(UUID.randomUUID());
        v.setIssuedTicketId(UUID.randomUUID());
        v.setPendingMarkUsed(true);
        when(validationRepository.findTop50ByPendingMarkUsedTrueOrderByValidatedAtAsc()).thenReturn(List.of(v));
        when(ticketsClient.markAsUsed(eq(v.getIssuedTicketId()), eq(TOKEN)))
                .thenThrow(new TicketsUnavailableException("still down"));

        int reconciled = service.retryPendingMarkUsed(TOKEN);

        assertEquals(0, reconciled);
        assertTrue(v.isPendingMarkUsed());
        verify(validationRepository, never()).save(v);
    }

    private ValidateTicketRequest buildRequest(String qr) {
        ValidateTicketRequest req = new ValidateTicketRequest();
        req.setQrCode(qr);
        req.setGateLocation("Puerta 1");
        req.setValidatedBy("logistica-01");
        req.setDeviceId("device-01");
        return req;
    }

    private OfflineValidationItem offlineItem(String qr, LocalDateTime at) {
        OfflineValidationItem it = new OfflineValidationItem();
        it.setQrCode(qr);
        it.setValidatedAt(at);
        it.setGateLocation("Puerta 1");
        it.setValidatedBy("logistica-01");
        it.setDeviceId("device-01");
        return it;
    }

    private IssuedTicketView ticketView(TicketStatus status) {
        return ticketView(UUID.randomUUID(), status);
    }

    private IssuedTicketView ticketView(UUID id, TicketStatus status) {
        return new IssuedTicketView(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Festival Demo",
                UUID.randomUUID(),
                "VIP",
                "Ana",
                "ana@example.com",
                "CC123",
                new BigDecimal("100000"),
                "QR-X",
                status,
                LocalDateTime.now(),
                status == TicketStatus.USED ? LocalDateTime.now() : null,
                status == TicketStatus.REVOKED ? LocalDateTime.now() : null,
                status == TicketStatus.REVOKED ? "test" : null
        );
    }

    private IssuedTicketView ticketViewWithEvent(UUID eventId, TicketStatus status) {
        return new IssuedTicketView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventId,
                "Festival Demo",
                UUID.randomUUID(),
                "VIP",
                "Ana",
                "ana@example.com",
                "CC123",
                new BigDecimal("100000"),
                "QR-X",
                status,
                LocalDateTime.now(),
                status == TicketStatus.USED ? LocalDateTime.now() : null,
                status == TicketStatus.REVOKED ? LocalDateTime.now() : null,
                status == TicketStatus.REVOKED ? "test" : null
        );
    }
}

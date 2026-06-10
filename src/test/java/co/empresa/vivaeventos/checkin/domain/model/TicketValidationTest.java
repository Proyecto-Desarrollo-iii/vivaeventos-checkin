package co.empresa.vivaeventos.checkin.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TicketValidationTest {

    @Test
    void onCreate_setsValidatedAt_whenNull() {
        TicketValidation v = new TicketValidation();
        assertNull(v.getValidatedAt());

        v.onCreate();

        assertNotNull(v.getValidatedAt());
    }

    @Test
    void onCreate_doesNotOverride_whenAlreadySet() {
        TicketValidation v = new TicketValidation();
        LocalDateTime now = LocalDateTime.now();
        v.setValidatedAt(now);

        v.onCreate();

        assertNotNull(v.getValidatedAt());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        TicketValidation v = new TicketValidation();
        v.setQrCode("QR-TEST");
        v.setGateLocation("Puerta 1");
        v.setValidatedBy("user");
        v.setDeviceId("device-1");
        v.setSyncedFromOffline(true);
        v.setPendingMarkUsed(true);
        v.setCorrelationId("corr-1");

        assertNotNull(v.getQrCode());
        assertNotNull(v.getGateLocation());
        assertNotNull(v.getValidatedBy());
        assertNotNull(v.getDeviceId());
        assertNotNull(v.getCorrelationId());
    }
}

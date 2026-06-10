package co.empresa.vivaeventos.checkin.domain.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TicketsUnavailableExceptionTest {

    @Test
    void constructor_withMessageOnly() {
        TicketsUnavailableException ex = new TicketsUnavailableException("test error");
        assertEquals("test error", ex.getMessage());
    }

    @Test
    void constructor_withMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        TicketsUnavailableException ex = new TicketsUnavailableException("test error", cause);
        assertEquals("test error", ex.getMessage());
        assertNotNull(ex.getCause());
    }
}

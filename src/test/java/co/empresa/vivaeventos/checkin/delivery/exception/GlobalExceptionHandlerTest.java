package co.empresa.vivaeventos.checkin.delivery.exception;

import co.empresa.vivaeventos.checkin.domain.service.TicketsClientException;
import co.empresa.vivaeventos.checkin.domain.service.TicketsUnavailableException;
import co.empresa.vivaeventos.checkin.domain.service.ValidationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationNotFound_returns404() {
        ValidationNotFoundException ex = new ValidationNotFoundException("No encontrado");

        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex);

        assertEquals(404, response.getStatusCode().value());
        assertEquals("No encontrado", response.getBody().get("error"));
    }

    @Test
    void handleTicketsClient_returns503() {
        TicketsClientException ex = new TicketsClientException("Error de comunicacion");

        ResponseEntity<Map<String, Object>> response = handler.handleTicketsClient(ex);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Error de comunicacion", response.getBody().get("error"));
    }

    @Test
    void handleTicketsUnavailable_returns503WithDegradedFlag() {
        TicketsUnavailableException ex = new TicketsUnavailableException("CB abierto");

        ResponseEntity<Map<String, Object>> response = handler.handleTicketsUnavailable(ex);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("CB abierto", response.getBody().get("error"));
        assertEquals(false, response.getBody().get("modoDegradado"));
    }

    @Test
    void handleIllegalState_returns409() {
        IllegalStateException ex = new IllegalStateException("Estado invalido");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalState(ex);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("Estado invalido", response.getBody().get("error"));
    }

    @Test
    void handleMethodArgumentNotValid_returns400WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "qrCode", "qrCode es obligatorio");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Solicitud invalida", response.getBody().get("error"));
        assertNotNull(response.getBody().get("detalles"));
    }

    @Test
    void handleRuntime_returns500() {
        RuntimeException ex = new RuntimeException("Error inesperado");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntime(ex);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error inesperado", response.getBody().get("error"));
    }

    @Test
    void handleRuntime_withNullMessage_returnsDefaultMessage() {
        RuntimeException ex = new RuntimeException();

        ResponseEntity<Map<String, Object>> response = handler.handleRuntime(ex);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error inesperado", response.getBody().get("error"));
    }
}

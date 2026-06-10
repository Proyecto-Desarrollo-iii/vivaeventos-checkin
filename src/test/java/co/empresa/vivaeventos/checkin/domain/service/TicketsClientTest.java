// src/test/java/co/empresa/vivaeventos/checkin/domain/service/TicketsClientTest.java
package co.empresa.vivaeventos.checkin.domain.service;

import co.empresa.vivaeventos.checkin.domain.model.Dto.IssuedTicketView;
import co.empresa.vivaeventos.checkin.domain.model.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketsClientTest {

    @Mock
    private RestClient restClient;
    
    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    
    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;
    
    @Mock
    private RestClient.ResponseSpec responseSpec;
    
    private TicketsClient ticketsClient;
    
    @BeforeEach
    void setUp() {
        ticketsClient = new TicketsClient(restClient);
    }
    
    @Test
    void findByQrCode_ReturnsTicket_WhenExists() {
        // Arrange
        String qrCode = "QR-TEST-123";
        String bearerToken = "Bearer token123";
        IssuedTicketView expectedTicket = createTestTicket();
        
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq(qrCode))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        
        // Simular respuesta exitosa
        TicketsClient.TicketEnvelope envelope = new TicketsClient.TicketEnvelope(expectedTicket);
        when(responseSpec.body(TicketsClient.TicketEnvelope.class)).thenReturn(envelope);
        
        // Act
        Optional<IssuedTicketView> result = ticketsClient.findByQrCode(qrCode, bearerToken);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedTicket.id(), result.get().id());
        assertEquals(expectedTicket.qrCode(), result.get().qrCode());
    }
    
    @Test
    void findByQrCode_ReturnsEmpty_WhenNotFound() {
        // Arrange
        String qrCode = "QR-NOT-FOUND";
        String bearerToken = "Bearer token123";
        
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq(qrCode))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        // Simular error 404
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.body(TicketsClient.TicketEnvelope.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));
        
        // Act
        Optional<IssuedTicketView> result = ticketsClient.findByQrCode(qrCode, bearerToken);
        
        // Assert
        assertTrue(result.isEmpty());
    }
    
    @Test
    void findByQrCode_ThrowsTicketsClientException_WhenConnectionFails() {
        // Arrange
        String qrCode = "QR-TEST";
        String bearerToken = "Bearer token123";
        
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq(qrCode))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(new ResourceAccessException("Connection refused"));
        
        // Act & Assert
        assertThrows(TicketsClientException.class, 
            () -> ticketsClient.findByQrCode(qrCode, bearerToken));
    }
    
    @Test
    void markAsUsed_ReturnsUpdatedTicket_WhenSuccessful() {
        // Arrange
        UUID ticketId = UUID.randomUUID();
        String bearerToken = "Bearer token123";
        IssuedTicketView updatedTicket = createTestTicket();
        
        when(restClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), eq(ticketId))).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        
        TicketsClient.TicketEnvelope envelope = new TicketsClient.TicketEnvelope(updatedTicket);
        when(responseSpec.body(TicketsClient.TicketEnvelope.class)).thenReturn(envelope);
        
        // Act
        IssuedTicketView result = ticketsClient.markAsUsed(ticketId, bearerToken);
        
        // Assert
        assertNotNull(result);
        assertEquals(updatedTicket.id(), result.id());
    }
    
    @Test
    void markAsUsed_ThrowsException_WhenMarkUsedFails() {
        // Arrange
        UUID ticketId = UUID.randomUUID();
        String bearerToken = "Bearer token123";
        
        when(restClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), eq(ticketId))).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        
        when(responseSpec.onStatus(any(), any())).thenAnswer(invocation -> {
            // Simular error 400
            return responseSpec;
        });
        when(responseSpec.body(TicketsClient.TicketEnvelope.class))
                .thenThrow(new TicketsClientException("No se pudo marcar como usada"));
        
        // Act & Assert
        assertThrows(TicketsClientException.class, 
            () -> ticketsClient.markAsUsed(ticketId, bearerToken));
    }
    
    @Test
    void markAsUsed_ThrowsTicketsClientException_WhenConnectionFails() {
        // Arrange
        UUID ticketId = UUID.randomUUID();
        String bearerToken = "Bearer token123";
        
        when(restClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), eq(ticketId))).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(new ResourceAccessException("Connection refused"));
        
        // Act & Assert
        assertThrows(TicketsClientException.class, 
            () -> ticketsClient.markAsUsed(ticketId, bearerToken));
    }
    
    @Test
    void findByQrCodeFallback_throwsTicketsUnavailable_whenCallNotPermitted() {
        CallNotPermittedException cnp = mock(CallNotPermittedException.class, withSettings().withoutAnnotations());

        assertThrows(TicketsUnavailableException.class,
                () -> ticketsClient.findByQrCodeFallback("QR", "Bearer token", cnp));
    }

    @Test
    void findByQrCodeFallback_throwsTicketsUnavailable_whenTicketsClientException() {
        TicketsClientException tce = new TicketsClientException("error");

        assertThrows(TicketsUnavailableException.class,
                () -> ticketsClient.findByQrCodeFallback("QR", "Bearer token", tce));
    }

    @Test
    void findByQrCodeFallback_throwsTicketsClientException_whenOtherError() {
        RuntimeException re = new RuntimeException("unexpected");

        assertThrows(TicketsClientException.class,
                () -> ticketsClient.findByQrCodeFallback("QR", "Bearer token", re));
    }

    @Test
    void markAsUsedFallback_throwsTicketsUnavailable_whenCallNotPermitted() {
        CallNotPermittedException cnp = mock(CallNotPermittedException.class, withSettings().withoutAnnotations());

        assertThrows(TicketsUnavailableException.class,
                () -> ticketsClient.markAsUsedFallback(UUID.randomUUID(), "Bearer token", cnp));
    }

    @Test
    void markAsUsedFallback_throwsTicketsUnavailable_whenTicketsClientException() {
        TicketsClientException tce = new TicketsClientException("error");

        assertThrows(TicketsUnavailableException.class,
                () -> ticketsClient.markAsUsedFallback(UUID.randomUUID(), "Bearer token", tce));
    }

    @Test
    void markAsUsedFallback_throwsTicketsClientException_whenOtherError() {
        RuntimeException re = new RuntimeException("unexpected");

        assertThrows(TicketsClientException.class,
                () -> ticketsClient.markAsUsedFallback(UUID.randomUUID(), "Bearer token", re));
    }

    @Test
    void ticketNotFoundRemote_canBeInstantiated() {
        TicketsClient.TicketNotFoundRemote ex = new TicketsClient.TicketNotFoundRemote();
        assertEquals("Boleta no encontrada", ex.getMessage());
    }

    @Test
    void ticketEnvelope_canBeCreated() {
        IssuedTicketView ticket = createTestTicket();
        TicketsClient.TicketEnvelope envelope = new TicketsClient.TicketEnvelope(ticket);
        assertNotNull(envelope.boleta());
        assertEquals(ticket.id(), envelope.boleta().id());
    }

    private IssuedTicketView createTestTicket() {
        return new IssuedTicketView(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Festival Test",
            UUID.randomUUID(),
            "VIP",
            "Test User",
            "test@example.com",
            "123456789",
            new BigDecimal("50000"),
            "QR-TEST-123",
            TicketStatus.ISSUED,
            LocalDateTime.now(),
            null,
            null,
            null
        );
    }
}
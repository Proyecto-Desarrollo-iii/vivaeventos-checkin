package co.empresa.vivaeventos.checkin.domain.service;

import co.empresa.vivaeventos.checkin.config.CorrelationIdFilter;
import co.empresa.vivaeventos.checkin.domain.model.Dto.IssuedTicketView;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Component
public class TicketsClient implements ITicketsClient {

    private static final String CB_NAME = "tickets";

    private final RestClient restClient;

    public TicketsClient(RestClient ticketsRestClient) {
        this.restClient = ticketsRestClient;
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "findByQrCodeFallback")
    @Retry(name = CB_NAME)
    public Optional<IssuedTicketView> findByQrCode(String qrCode, String bearerToken) {
        try {
            TicketEnvelope envelope = restClient.get()
                    .uri("/api/v1/issued-tickets/qr/{qr}", qrCode)
                    .headers(h -> applyHeaders(h, bearerToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            throw new TicketNotFoundRemote();
                        }
                        throw new TicketsClientException("Error al consultar boleta: " + res.getStatusText());
                    })
                    .body(TicketEnvelope.class);
            return envelope != null ? Optional.ofNullable(envelope.boleta()) : Optional.empty();
        } catch (TicketNotFoundRemote | HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (ResourceAccessException e) {
            throw new TicketsClientException("Tickets no disponible: " + e.getMessage());
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "markAsUsedFallback")
    @Retry(name = CB_NAME)
    public IssuedTicketView markAsUsed(UUID ticketId, String bearerToken) {
        try {
            TicketEnvelope envelope = restClient.put()
                    .uri("/api/v1/issued-tickets/{id}/mark-used", ticketId)
                    .headers(h -> applyHeaders(h, bearerToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new TicketsClientException("No se pudo marcar como usada: " + res.getStatusText());
                    })
                    .body(TicketEnvelope.class);
            return envelope != null ? envelope.boleta() : null;
        } catch (ResourceAccessException e) {
            throw new TicketsClientException("Tickets no disponible: " + e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private Optional<IssuedTicketView> findByQrCodeFallback(String qrCode, String bearerToken, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            throw new TicketsUnavailableException("Circuit breaker abierto: tickets no disponible", t);
        }
        if (t instanceof TicketsClientException) {
            throw new TicketsUnavailableException("Tickets no disponible: " + t.getMessage(), t);
        }
        throw new TicketsClientException(t.getMessage());
    }

    @SuppressWarnings("unused")
    private IssuedTicketView markAsUsedFallback(UUID ticketId, String bearerToken, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            throw new TicketsUnavailableException("Circuit breaker abierto: tickets no disponible", t);
        }
        if (t instanceof TicketsClientException) {
            throw new TicketsUnavailableException("Tickets no disponible: " + t.getMessage(), t);
        }
        throw new TicketsClientException(t.getMessage());
    }

    private void applyHeaders(HttpHeaders headers, String bearerToken) {
        if (bearerToken != null && !bearerToken.isBlank()) {
            String value = bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken;
            headers.set(HttpHeaders.AUTHORIZATION, value);
        }
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set(CorrelationIdFilter.HEADER, correlationId);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TicketEnvelope(IssuedTicketView boleta) {}

    private static final class TicketNotFoundRemote extends RuntimeException {
        TicketNotFoundRemote() { super("Boleta no encontrada"); }
    }
}

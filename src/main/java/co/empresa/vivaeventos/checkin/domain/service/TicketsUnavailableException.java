package co.empresa.vivaeventos.checkin.domain.service;

public class TicketsUnavailableException extends RuntimeException {
    public TicketsUnavailableException(String message) {
        super(message);
    }

    public TicketsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

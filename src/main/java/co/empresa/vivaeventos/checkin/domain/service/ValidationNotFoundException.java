package co.empresa.vivaeventos.checkin.domain.service;

public class ValidationNotFoundException extends RuntimeException {
    public ValidationNotFoundException(String message) {
        super(message);
    }
}

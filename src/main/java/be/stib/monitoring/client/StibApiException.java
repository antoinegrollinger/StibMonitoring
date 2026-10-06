package be.stib.monitoring.client;

public class StibApiException extends RuntimeException {

    public StibApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

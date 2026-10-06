package be.stib.monitoring.web;

import be.stib.monitoring.client.StibApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

@RestControllerAdvice
public class StibExceptionHandler {

    @ExceptionHandler(StibApiException.class)
    ProblemDetail handleStibError(StibApiException e) {
        if (e.getCause() instanceof HttpClientErrorException.TooManyRequests) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "STIB API rate limit exceeded, try again shortly");
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}

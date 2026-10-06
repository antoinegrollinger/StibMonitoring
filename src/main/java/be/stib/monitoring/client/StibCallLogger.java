package be.stib.monitoring.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Logs every call made to the STIB API with its status and duration, at DEBUG level only. Calls
 * answered from the cache never reach STIB, so they are not logged here. The API token travels in
 * a header and is never logged.
 */
public class StibCallLogger implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StibCallLogger.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!log.isDebugEnabled()) {
            return execution.execute(request, body);
        }
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            log.debug("STIB {} {} -> {} in {} ms", request.getMethod(), request.getURI(),
                    response.getStatusCode().value(), elapsedMillis(start));
            return response;
        } catch (IOException e) {
            log.debug("STIB {} {} -> failed with {} after {} ms", request.getMethod(), request.getURI(),
                    e.getClass().getSimpleName(), elapsedMillis(start));
            throw e;
        }
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}

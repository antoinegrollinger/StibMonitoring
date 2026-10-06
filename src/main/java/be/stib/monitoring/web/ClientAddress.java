package be.stib.monitoring.web;

import jakarta.servlet.http.HttpServletRequest;

/** The address a request comes from, as used for rate limiting and logging. */
final class ClientAddress {

    private ClientAddress() {
    }

    /**
     * The last value of {@code header} when it is set (proxies append to X-Forwarded-For, so the
     * last value is the one our proxy saw), otherwise the connection's address.
     */
    static String of(HttpServletRequest request, String header) {
        String value = header == null || header.isBlank() ? null : request.getHeader(header);
        if (value != null) {
            String[] values = value.split(",");
            String last = values[values.length - 1].strip();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }
}

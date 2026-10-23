package com.ecommerce.apigateway.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Turns every gateway-level failure into the standard JSON error:
 * - downstream service not running       → 503 (also: no instance registered in Eureka)
 * - downstream too slow                  → 504
 * - no route for the path                → 404
 * - anything unexpected                  → 500 (details logged, never returned)
 *
 * Order -2 puts it ahead of Spring Boot's default handler (-1).
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getPath().value();
        HttpStatus status;
        String message;

        if (hasCause(ex, ConnectException.class)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Service temporarily unavailable. Please try again later.";
            log.warn("Downstream service unreachable for {}: {}", path, rootMessage(ex));
        } else if (hasCause(ex, TimeoutException.class)) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            message = "The service took too long to respond. Please try again later.";
            log.warn("Downstream timeout for {}", path);
        } else if (ex instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            status = resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
            message = messageFor(status);
            if (status.is5xxServerError()) {
                log.warn("Gateway returned {} for {}: {}", status.value(), path, rootMessage(ex));
            }
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred";
            log.error("Unexpected gateway error on {}", path, ex);
        }

        return errorResponseWriter.write(exchange, status, message);
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "HTTP method is not supported for this endpoint";
            case UNSUPPORTED_MEDIA_TYPE -> "Content type not supported; use application/json";
            case BAD_REQUEST -> "Bad request";
            case SERVICE_UNAVAILABLE -> "Service temporarily unavailable. Please try again later.";
            case GATEWAY_TIMEOUT -> "The service took too long to respond. Please try again later.";
            default -> status.is5xxServerError() ? "An unexpected error occurred" : status.getReasonPhrase();
        };
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}

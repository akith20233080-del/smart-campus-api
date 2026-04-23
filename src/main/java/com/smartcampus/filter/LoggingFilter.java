package com.smartcampus.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Part 5.5 – API Request & Response Logging Filter.
 *
 * Implements both ContainerRequestFilter and ContainerResponseFilter so a
 * single class handles the full request/response lifecycle as a cross-cutting concern.
 *
 * This is far superior to manually inserting Logger.info() into every resource method
 * because:
 *   - It is applied automatically to every endpoint without touching resource code.
 *   - Logging logic lives in one place — easy to update or disable.
 *   - Resource methods stay focused on business logic only (separation of concerns).
 */
@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(LoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        LOGGER.info(String.format(
                "--> INCOMING REQUEST | Method: %s | URI: %s",
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri()
        ));
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        LOGGER.info(String.format(
                "<-- OUTGOING RESPONSE | Method: %s | URI: %s | Status: %d %s",
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri(),
                responseContext.getStatus(),
                responseContext.getStatusInfo().getReasonPhrase()
        ));
    }
}

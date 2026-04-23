package com.smartcampus.exception;

import com.smartcampus.model.ErrorResponse;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Part 5.4 - Global Safety Net.
 * Catches ALL unhandled exceptions EXCEPT WebApplicationExceptions
 * (like NotFoundException, BadRequestException) which Jersey handles itself.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable throwable) {

        // If it is a JAX-RS WebApplicationException (404, 400 etc)
        // let Jersey handle it with the correct status code
        if (throwable instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) throwable;
            Response original = wae.getResponse();
            ErrorResponse error = new ErrorResponse(
                original.getStatus(),
                original.getStatusInfo().getReasonPhrase(),
                throwable.getMessage() != null ? throwable.getMessage() : "Request error"
            );
            return Response.status(original.getStatus())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(error)
                    .build();
        }

        // For all other unexpected errors - log server side only, return clean 500
        LOGGER.log(Level.SEVERE, "Unhandled exception caught by global mapper", throwable);

        ErrorResponse error = new ErrorResponse(
                500,
                "Internal Server Error",
                "An unexpected error occurred. Please contact the API administrator."
        );
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}

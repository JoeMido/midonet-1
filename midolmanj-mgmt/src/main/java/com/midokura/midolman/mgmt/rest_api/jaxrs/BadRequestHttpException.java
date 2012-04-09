/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midolman.mgmt.rest_api.jaxrs;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * WebApplicationException class to represent 400 status.
 */
public class BadRequestHttpException extends WebApplicationException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a BadRequestHttpException object with no message.
     */
    public BadRequestHttpException() {
        this("");
    }

    /**
     * Create a BadRequestHttpException object with a message.
     *
     * @param message
     *            Error message.
     */
    public BadRequestHttpException(String message) {
        super(ResponseUtils.buildErrorResponse(
                Response.Status.BAD_REQUEST.getStatusCode(), message));
    }
}

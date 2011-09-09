/*
 * @(#)RouteResource        1.6 11/09/10
 *
 * Copyright 2011 Midokura KK
 */
package com.midokura.midolman.mgmt.rest_api.v1.resources;

import java.net.URI;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.mgmt.data.dao.RouteDataAccessor;
import com.midokura.midolman.mgmt.data.dto.Route;

/**
 * Root resource class for ports.
 *
 * @version        1.6 08 Sept 2011
 * @author         Ryu Ishimoto
 */
@Path("/routes")
public class RouteResource extends RestResource {
    /*
     * Implements REST API endpoints for routes.
     */

    private final static Logger log = LoggerFactory.getLogger(
            RouteResource.class);
    
    /**
     * Get the route with the given ID.
     * @param id  Route UUID.
     * @return  Route object.
     */
/*    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Route get(@PathParam("id") UUID id) {
        // Get a route for the given ID.
        RouteDataAccessor dao = new RouteDataAccessor(zookeeperConn);
        Route route = null;
        try {
            route = dao.get(id);
        } catch (Exception ex) {
            log.error("Error getting route", ex);
            throw new WebApplicationException(
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON).build());
        }
        return route;
    } */   
    
    /**
     * Sub-resource class for router's route.
     */
    public static class RouterRouteResource extends RestResource {
        
        private UUID routerId = null;
        
        /**
         * Default constructor.
         * 
         * @param   zkConn  Zookeeper connection string.
         * @param   routerId  UUID of a router.
         */
        public RouterRouteResource(String zkConn, UUID routerId) {
            this.zookeeperConn = zkConn;
            this.routerId = routerId;        
        }
        
        /**
         * Handler for create route API call.
         * 
         * @param   port  Router object mapped to the request input.
         * @throws Exception 
         * @returns Response object with 201 status code set if successful.
         */
        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response create(Route route, @Context UriInfo uriInfo)
                throws Exception {
            // Add a new route entry into zookeeper.
            route.setId(UUID.randomUUID());
            route.setRouterId(routerId);
            RouteDataAccessor dao = new RouteDataAccessor(zookeeperConn);

            try {
                dao.create(route);
            } catch (Exception ex) {
                log.error("Error creating route", ex);
                throw new WebApplicationException(
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .type(MediaType.APPLICATION_JSON).build());
            }
            
            URI uri = uriInfo.getBaseUriBuilder()
                .path("routes/" + route.getId()).build();
            return Response.created(uri).build();
        }        
    }
}

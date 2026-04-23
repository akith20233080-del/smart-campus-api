package com.smartcampus.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/discovery")
public class DiscoveryResource {

    // GET /api/v1/discovery
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discover() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("apiName", "Smart Campus Sensor & Room Management API");
        response.put("version", "1.0.0");
        response.put("description", "RESTful API for managing campus rooms and IoT sensors.");

        Map<String, String> contact = new LinkedHashMap<>();
        contact.put("team", "Campus Infrastructure Team");
        contact.put("email", "smartcampus@university.ac.uk");
        response.put("contact", contact);

        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("rooms", "/api/v1/rooms");
        resources.put("sensors", "/api/v1/sensors");
        response.put("resources", resources);

        Map<String, String> selfLink = new LinkedHashMap<>();
        selfLink.put("rel", "self");
        selfLink.put("href", "/api/v1/discovery");

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("self", selfLink);
        response.put("_links", links);

        return Response.ok(response).build();
    }
}

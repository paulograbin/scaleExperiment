package com.paulograbin.scale;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
public class HelloResource {

    private static final String RESPONSE = "Hello, World!";

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return RESPONSE;
    }
}

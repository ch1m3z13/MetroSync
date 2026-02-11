package com.commute.metrosync.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/api/v1")
public class JaxRsApplication extends Application {
    // JAX-RS will automatically discover and register all @Path resources
}
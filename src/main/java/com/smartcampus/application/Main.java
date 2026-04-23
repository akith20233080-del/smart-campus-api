package com.smartcampus.application;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;

public class Main {

    public static final String BASE_URI = "http://0.0.0.0:8080/";

    public static void main(String[] args) {
        System.out.println("Starting Smart Campus API...");
        System.out.flush();

        try {
            System.out.println("Step 1: Creating ResourceConfig...");
            System.out.flush();

            ResourceConfig config = ResourceConfig
                    .forApplicationClass(SmartCampusApplication.class);

            System.out.println("Step 2: Starting Grizzly server on " + BASE_URI);
            System.out.flush();

            HttpServer server = GrizzlyHttpServerFactory
                    .createHttpServer(URI.create(BASE_URI), config);

            System.out.println("=================================================");
            System.out.println(" Smart Campus API is running!");
            System.out.println(" Base URL : http://localhost:8080/api/v1");
            System.out.println(" Press CTRL+C to stop.");
            System.out.println("=================================================");
            System.out.flush();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                server.shutdownNow();
            }));

            Thread.currentThread().join();

        } catch (Throwable e) {
            System.err.println("STARTUP FAILED: " + e.getClass().getName());
            System.err.println("MESSAGE: " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }
    }
}

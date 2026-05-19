package com.paulograbin.scale;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.Executors;

public class Server {

    private static final byte[] HELLO_BYTES = "Hello, World!".getBytes();
    private static final byte[] HEALTH_BYTES = "{\"status\":\"UP\"}".getBytes();

    public static void main(String[] args) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(8080), 4096);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/hello", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, HELLO_BYTES.length);
            exchange.getResponseBody().write(HELLO_BYTES);
            exchange.close();
        });

        server.createContext("/actuator/health", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, HEALTH_BYTES.length);
            exchange.getResponseBody().write(HEALTH_BYTES);
            exchange.close();
        });

        server.start();
        System.out.println("Listening on :8080");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            server.stop(5);
        }));
    }
}

package com.paulograbin.scale.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private static final ResponseEntity<String> RESPONSE =
            ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Hello, World!");

    @GetMapping(value = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> hello() {
        return RESPONSE;
    }
}

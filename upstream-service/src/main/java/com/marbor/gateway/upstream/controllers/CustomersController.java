package com.marbor.gateway.upstream.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomersController {

    @GetMapping(path = "/customers")
    public ResponseEntity<String> getCustomers() {
        return ResponseEntity.ok()
                .body("""
                        {
                            "customers": []
                        }
                        """);
    }
}

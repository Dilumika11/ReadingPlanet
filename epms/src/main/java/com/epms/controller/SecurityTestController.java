package com.epms.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security-test")
@SecurityRequirement(name = "bearerAuth")
public class SecurityTestController {

    @GetMapping("/protected")
    public String protectedEndpoint(Authentication authentication) {

        return "Authenticated successfully as: "
                + authentication.getName();
    }
}
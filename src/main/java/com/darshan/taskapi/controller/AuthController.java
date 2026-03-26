package com.darshan.taskapi.controller;

import com.darshan.taskapi.dto.request.LoginRequest;
import com.darshan.taskapi.dto.request.RegisterRequest;
import com.darshan.taskapi.dto.response.AppResponse;
import com.darshan.taskapi.dto.response.AuthResponse;
import com.darshan.taskapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register and login to get a JWT token")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(
        tags = {"Authentication"},
        summary = "Register a new user",
        description = "Creates a new user account and returns a JWT token. Password must be at least 8 characters."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully",
            content = @Content(schema = @Schema(implementation = AppResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed — missing fields, invalid email, or password < 8 chars"),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<AppResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.success("User registered successfully", authResponse));
    }

    @PostMapping("/login")
    @Operation(
        tags = {"Authentication"},
        summary = "Login",
        description = "Authenticates an existing user and returns a JWT token. Paste the token into the Authorize button at the top."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = AppResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid email or password")
    })
    public ResponseEntity<AppResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(AppResponse.success("Login successful", authResponse));
    }
}

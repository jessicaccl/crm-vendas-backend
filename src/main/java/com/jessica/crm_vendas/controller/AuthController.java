package com.jessica.crm_vendas.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.jessica.crm_vendas.dto.LoginRequestDTO;
import com.jessica.crm_vendas.dto.LoginResponseDTO;
import com.jessica.crm_vendas.dto.UserResponseDTO;
import com.jessica.crm_vendas.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login e dados do usuário logado")
public class AuthController {

    private final AuthService service;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Faz login e devolve um token JWT", description = "Usuário de demonstração: demo@crmpro.dev / demo1234")
    public LoginResponseDTO login(@RequestBody @Valid LoginRequestDTO data) {
        return service.login(data);
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário logado")
    public UserResponseDTO me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(jwt.getSubject());
    }
}

package com.jessica.crm_vendas.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.jessica.crm_vendas.dto.LoginRequestDTO;
import com.jessica.crm_vendas.dto.LoginResponseDTO;
import com.jessica.crm_vendas.dto.UserResponseDTO;
import com.jessica.crm_vendas.security.ClientIpResolver;
import com.jessica.crm_vendas.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login e dados do usuário logado")
public class AuthController {

    private final AuthService service;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Faz login e devolve um token JWT", description = "Usuário de demonstração: demo@crmpro.dev / demo1234. Após 5 erros seguidos, o login fica bloqueado por 15 minutos (429).")
    public LoginResponseDTO login(@RequestBody @Valid LoginRequestDTO data, HttpServletRequest request) {
        return service.login(data, clientIpResolver.resolve(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário logado")
    public UserResponseDTO me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(jwt.getSubject());
    }
}

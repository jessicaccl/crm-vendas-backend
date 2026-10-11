package com.jessica.crm_vendas.dto;

import java.time.Instant;

public record LoginResponseDTO(String token, Instant expiresAt, UserResponseDTO user) {}

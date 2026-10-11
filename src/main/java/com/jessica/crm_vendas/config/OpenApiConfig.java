package com.jessica.crm_vendas.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** No Swagger (/docs): faça login em /auth/login, copie o token e clique em "Authorize". */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "CRM Vendas API",
        version = "v1",
        description = "API do CRM de vendas: clientes, funil e integrações. Usuário de demonstração: demo@crmpro.dev / demo1234"
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {}

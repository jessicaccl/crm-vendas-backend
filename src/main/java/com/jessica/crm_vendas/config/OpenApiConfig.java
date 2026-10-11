package com.jessica.crm_vendas.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@Configuration
@OpenAPIDefinition(info = @Info(
    title = "CRM Vendas API",
    version = "v1",
    description = "API do CRM de vendas: clientes, funil e integrações."
))
public class OpenApiConfig {}

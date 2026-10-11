package com.jessica.crm_vendas.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jessica.crm_vendas.dto.InboundLeadDTO;
import com.jessica.crm_vendas.dto.IntegrationStatusDTO;
import com.jessica.crm_vendas.dto.LeadIntakeResultDTO;
import com.jessica.crm_vendas.integration.LeadIntakeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrações", description = "Entrada de leads de outras plataformas")
public class IntegrationController {

    private final LeadIntakeService service;

    public record NetlifySecret(String secret) {}

    @PostMapping("/netlify/forms/{organizationSlug}")
    @SecurityRequirements
    @Operation(summary = "Recebe envios de formulários do Netlify (webhook) para uma organização",
            description = "Autenticado pela assinatura X-Webhook-Signature (JWS secret token da organização). "
                    + "Cria ou atualiza o Lead e abre um negócio em Prospecto. Reenvios do mesmo envio são ignorados.")
    public ResponseEntity<LeadIntakeResultDTO> receiveNetlify(
            @PathVariable String organizationSlug,
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        LeadIntakeResultDTO result = service.receiveNetlify(organizationSlug, body, signature);
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @GetMapping("/leads")
    @Operation(summary = "Últimos leads recebidos pela organização")
    public List<InboundLeadDTO> recentLeads(@RequestParam(defaultValue = "20") int limit) {
        return service.recent(Math.max(1, Math.min(limit, 100)));
    }

    @GetMapping("/status")
    @Operation(summary = "Situação das integrações da organização")
    public IntegrationStatusDTO status() {
        return service.status();
    }

    @PostMapping("/netlify/secret")
    @Operation(summary = "Gera uma nova chave para o webhook do Netlify (administradores)",
            description = "A chave anterior deixa de funcionar. Cole a nova no campo JWS secret token do Netlify.")
    public NetlifySecret regenerateNetlifySecret() {
        return new NetlifySecret(service.regenerateNetlifySecret());
    }
}

package com.jessica.crm_vendas.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jessica.crm_vendas.dto.InboundLeadDTO;
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

    static final String NETLIFY_WEBHOOK_PATH = "/api/v1/integrations/netlify/forms";

    private final LeadIntakeService service;

    public record IntegrationStatus(boolean netlifyEnabled, String netlifyWebhookPath) {}

    @PostMapping("/netlify/forms")
    @SecurityRequirements
    @Operation(summary = "Recebe envios de formulários do Netlify (webhook)",
            description = "Autenticado pela assinatura X-Webhook-Signature (JWS secret token configurado no Netlify). "
                    + "Cria ou atualiza o Lead e abre um negócio em Prospecto. Reenvios do mesmo envio são ignorados.")
    public ResponseEntity<LeadIntakeResultDTO> receiveNetlify(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        LeadIntakeResultDTO result = service.receiveNetlify(body, signature);
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @GetMapping("/leads")
    @Operation(summary = "Últimos leads recebidos por integrações")
    public List<InboundLeadDTO> recentLeads(@RequestParam(defaultValue = "20") int limit) {
        return service.recent(Math.max(1, Math.min(limit, 100)));
    }

    @GetMapping("/status")
    @Operation(summary = "Quais integrações estão ativas neste ambiente")
    public IntegrationStatus status() {
        return new IntegrationStatus(service.isNetlifyEnabled(), NETLIFY_WEBHOOK_PATH);
    }
}

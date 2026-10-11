package com.jessica.crm_vendas.integration;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jessica.crm_vendas.dto.DealRequestDTO;
import com.jessica.crm_vendas.dto.DealResponseDTO;
import com.jessica.crm_vendas.dto.InboundLeadDTO;
import com.jessica.crm_vendas.dto.IntegrationStatusDTO;
import com.jessica.crm_vendas.dto.LeadIntakeResultDTO;
import com.jessica.crm_vendas.exception.BusinessRuleException;
import com.jessica.crm_vendas.exception.InvalidSignatureException;
import com.jessica.crm_vendas.exception.ResourceNotFoundException;
import com.jessica.crm_vendas.integration.netlify.NetlifySignatureVerifier;
import com.jessica.crm_vendas.integration.netlify.NetlifySubmission;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.CustomerStatus;
import com.jessica.crm_vendas.model.DealSource;
import com.jessica.crm_vendas.model.DealStage;
import com.jessica.crm_vendas.model.InboundLead;
import com.jessica.crm_vendas.model.Organization;
import com.jessica.crm_vendas.repository.CustomerRepository;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.InboundLeadRepository;
import com.jessica.crm_vendas.repository.OrganizationRepository;
import com.jessica.crm_vendas.security.TenantContext;
import com.jessica.crm_vendas.service.DealService;

import lombok.RequiredArgsConstructor;

/**
 * Transforma leads de fora (webhook do Netlify, planilha CSV) em Lead + Negócio no funil da organização.
 * Todas as entradas usam a mesma regra: o mesmo contato (pelo e-mail ou pelo WhatsApp) não é duplicado,
 * ganha só um negócio novo, e o mesmo envio nunca é processado duas vezes.
 */
@Service
@RequiredArgsConstructor
public class LeadIntakeService {

    public static final String NETLIFY_WEBHOOK_PATH = "/api/v1/integrations/netlify/forms/";

    static final String NETLIFY = "NETLIFY";
    static final String CSV = "CSV";
    private static final int TITLE_MAX = 150;
    private static final Pattern VALID_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NetlifySignatureVerifier netlifyVerifier;
    private final OrganizationRepository organizationRepository;
    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final InboundLeadRepository inboundLeadRepository;
    private final DealService dealService;
    private final TenantContext tenant;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public LeadIntakeResultDTO receiveNetlify(String organizationSlug, byte[] body, String signature) {
        Organization organization = organizationRepository.findBySlug(organizationSlug)
                .filter(org -> !org.isDemo() && org.getNetlifyWebhookSecret() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Integração com o Netlify não configurada para esta organização."));

        if (!netlifyVerifier.isValid(organization.getNetlifyWebhookSecret(), signature, body)) {
            throw new InvalidSignatureException("Webhook sem assinatura válida do Netlify.");
        }

        NetlifySubmission submission = NetlifySubmission.from(parse(body));
        String externalId = submission.id() != null ? submission.id() : NetlifySignatureVerifier.sha256Hex(body);
        LeadData lead = new LeadData(
                submission.name(), submission.phone(), submission.email(), submission.company(),
                submission.result(), submission.score(),
                "Netlify Forms" + (submission.formName() != null ? " (" + submission.formName() + ")" : ""),
                "Lead do formulário" + (submission.formName() != null ? " " + submission.formName() : ""),
                Map.of());

        if (!lead.hasContact()) {
            throw new BusinessRuleException("O envio não tem nome, telefone nem e-mail.");
        }

        return register(organization.getId(), NETLIFY, externalId, submission.formName(), lead,
                new String(body, StandardCharsets.UTF_8), clock.instant(), DealSource.WEBSITE);
    }

    /**
     * Registra um lead na organização: reaproveita o contato existente (e-mail ou WhatsApp),
     * abre um negócio em Prospecto e guarda o conteúdo original. Se o mesmo envio
     * (provider + externalId) já foi processado, não faz nada e avisa que é duplicado.
     */
    @Transactional
    LeadIntakeResultDTO register(Long org, String provider, String externalId, String formName, LeadData data,
                                 String payloadJson, Instant receivedAt, DealSource source) {
        Optional<InboundLead> alreadyReceived =
                inboundLeadRepository.findByOrganizationIdAndProviderAndExternalId(org, provider, externalId);
        if (alreadyReceived.isPresent()) {
            InboundLead lead = alreadyReceived.get();
            return new LeadIntakeResultDTO(
                    lead.getCustomer() == null ? null : lead.getCustomer().getId(),
                    lead.getDeal() == null ? null : lead.getDeal().getId(),
                    false, true);
        }

        // E-mail é opcional; texto que não é e-mail é descartado em vez de ir para o cadastro
        String email = data.email() != null && VALID_EMAIL.matcher(data.email()).matches()
                ? data.email().toLowerCase()
                : null;
        String phoneDigits = PhoneNumbers.digits(data.phone());

        Optional<Customer> existing = Optional.<Customer>empty();
        if (email != null) existing = customerRepository.findFirstByOrganizationIdAndEmail(org, email);
        if (existing.isEmpty() && phoneDigits != null) existing = customerRepository.findFirstByPhoneDigits(org, phoneDigits);

        boolean newCustomer = existing.isEmpty();
        Customer customer = existing.orElseGet(Customer::new);
        if (newCustomer) {
            customer.setOrganizationId(org);
            customer.setName(data.name() != null ? data.name() : "Lead sem nome");
            customer.setStatus(CustomerStatus.LEAD);
        }
        // Completa o cadastro sem sobrescrever o que já existe
        if (customer.getEmail() == null && email != null) customer.setEmail(email);
        if (customer.getPhone() == null && phoneDigits != null) customer.setPhone(PhoneNumbers.format(phoneDigits));
        if (customer.getCompany() == null && data.company() != null) customer.setCompany(data.company());
        Customer saved = customerRepository.save(customer);

        // Entradas externas não têm login: o negócio é criado explicitamente na organização dona do lead
        DealResponseDTO deal = tenant.callAs(org, () -> dealService.create(new DealRequestDTO(
                dealTitle(data),
                saved.getId(),
                BigDecimal.ZERO,
                DealStage.PROSPECT,
                source,
                null,
                null,
                notes(data))));

        InboundLead lead = new InboundLead();
        lead.setOrganizationId(org);
        lead.setProvider(provider);
        lead.setExternalId(externalId);
        lead.setFormName(formName);
        lead.setPayload(payloadJson);
        lead.setCustomer(saved);
        lead.setDeal(dealRepository.getReferenceById(deal.id()));
        lead.setReceivedAt(receivedAt);
        inboundLeadRepository.save(lead);

        return new LeadIntakeResultDTO(saved.getId(), deal.id(), newCustomer, false);
    }

    @Transactional(readOnly = true)
    public List<InboundLeadDTO> recent(int limit) {
        return inboundLeadRepository.findByOrganizationIdOrderByReceivedAtDesc(tenant.organizationId(), PageRequest.of(0, limit))
                .stream()
                .map(InboundLeadDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationStatusDTO status() {
        Organization organization = currentOrganization();
        return new IntegrationStatusDTO(
                !organization.isDemo(),
                organization.getNetlifyWebhookSecret() != null,
                NETLIFY_WEBHOOK_PATH + organization.getSlug());
    }

    /**
     * Gera (ou troca) a chave que assina os webhooks do Netlify da organização.
     * A chave é devolvida para ser colada no Netlify; trocá-la invalida a anterior.
     */
    @Transactional
    public String regenerateNetlifySecret() {
        if (!tenant.isAdmin()) {
            throw new AccessDeniedException("Só administradores podem configurar integrações.");
        }
        Organization organization = currentOrganization();
        if (organization.isDemo()) {
            throw new BusinessRuleException("Integrações não estão disponíveis na organização de demonstração.");
        }
        byte[] bytes = new byte[30];
        RANDOM.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // 40 caracteres
        organization.setNetlifyWebhookSecret(secret);
        return secret;
    }

    private Organization currentOrganization() {
        return organizationRepository.findById(tenant.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organização não encontrada"));
    }

    private JsonNode parse(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) throw new IOException("não é um objeto JSON");
            return root;
        } catch (IOException e) {
            throw new BusinessRuleException("O conteúdo do webhook não é um JSON válido.");
        }
    }

    private static String dealTitle(LeadData data) {
        String title = data.result() != null ? "Diagnóstico: " + data.result() : data.fallbackTitle();
        return title.length() <= TITLE_MAX ? title : title.substring(0, TITLE_MAX - 1) + "…";
    }

    private static String notes(LeadData data) {
        List<String> lines = new ArrayList<>();
        lines.add("Origem: " + data.origin());
        if (data.result() != null) lines.add("Resultado do diagnóstico: " + data.result());
        if (data.score() != null) lines.add("Placar: " + data.score());
        data.extras().forEach((field, value) -> lines.add(field + ": " + value));
        String notes = String.join("\n", lines);
        return notes.length() <= 2000 ? notes : notes.substring(0, 1999) + "…";
    }
}

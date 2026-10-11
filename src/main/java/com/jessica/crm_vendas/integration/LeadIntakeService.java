package com.jessica.crm_vendas.integration;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jessica.crm_vendas.dto.DealRequestDTO;
import com.jessica.crm_vendas.dto.DealResponseDTO;
import com.jessica.crm_vendas.dto.InboundLeadDTO;
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
import com.jessica.crm_vendas.repository.CustomerRepository;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.InboundLeadRepository;
import com.jessica.crm_vendas.service.DealService;

import lombok.RequiredArgsConstructor;

/**
 * Transforma um envio de formulário em Lead + Negócio no funil.
 * O mesmo contato (pelo e-mail ou pelo WhatsApp) não é duplicado: ganha só um negócio novo.
 */
@Service
@RequiredArgsConstructor
public class LeadIntakeService {

    static final String NETLIFY = "NETLIFY";
    private static final int TITLE_MAX = 150;
    private static final Pattern VALID_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final NetlifySignatureVerifier netlifyVerifier;
    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final InboundLeadRepository inboundLeadRepository;
    private final DealService dealService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public LeadIntakeResultDTO receiveNetlify(byte[] body, String signature) {
        if (!netlifyVerifier.isEnabled()) {
            throw new ResourceNotFoundException("A integração com o Netlify não está configurada neste ambiente.");
        }
        if (!netlifyVerifier.isValid(signature, body)) {
            throw new InvalidSignatureException("Webhook sem assinatura válida do Netlify.");
        }

        NetlifySubmission submission = NetlifySubmission.from(parse(body));
        String externalId = submission.id() != null ? submission.id() : NetlifySignatureVerifier.sha256Hex(body);

        Optional<InboundLead> alreadyReceived = inboundLeadRepository.findByProviderAndExternalId(NETLIFY, externalId);
        if (alreadyReceived.isPresent()) {
            InboundLead lead = alreadyReceived.get();
            return new LeadIntakeResultDTO(
                    lead.getCustomer() == null ? null : lead.getCustomer().getId(),
                    lead.getDeal() == null ? null : lead.getDeal().getId(),
                    false, true);
        }

        if (submission.name() == null && submission.phone() == null && submission.email() == null) {
            throw new BusinessRuleException("O envio não tem nome, telefone nem e-mail.");
        }

        // E-mail é opcional no formulário; texto que não é e-mail é descartado em vez de ir para o cadastro
        String email = submission.email() != null && VALID_EMAIL.matcher(submission.email()).matches()
                ? submission.email().toLowerCase()
                : null;
        String phoneDigits = PhoneNumbers.digits(submission.phone());

        Optional<Customer> existing = Optional.<Customer>empty();
        if (email != null) existing = customerRepository.findFirstByEmail(email);
        if (existing.isEmpty() && phoneDigits != null) existing = customerRepository.findFirstByPhoneDigits(phoneDigits);

        boolean newCustomer = existing.isEmpty();
        Customer customer = existing.orElseGet(Customer::new);
        if (newCustomer) {
            customer.setName(submission.name() != null ? submission.name() : "Lead do formulário");
            customer.setStatus(CustomerStatus.LEAD);
        }
        // Completa o cadastro sem sobrescrever o que já existe
        if (customer.getEmail() == null && email != null) customer.setEmail(email);
        if (customer.getPhone() == null && phoneDigits != null) customer.setPhone(PhoneNumbers.format(phoneDigits));
        if (customer.getCompany() == null && submission.company() != null) customer.setCompany(submission.company());
        customer = customerRepository.save(customer);

        DealResponseDTO deal = dealService.create(new DealRequestDTO(
                dealTitle(submission),
                customer.getId(),
                BigDecimal.ZERO,
                DealStage.PROSPECT,
                DealSource.WEBSITE,
                null,
                null,
                notes(submission)));

        InboundLead lead = new InboundLead();
        lead.setProvider(NETLIFY);
        lead.setExternalId(externalId);
        lead.setFormName(submission.formName());
        lead.setPayload(new String(body, StandardCharsets.UTF_8));
        lead.setCustomer(customer);
        lead.setDeal(dealRepository.getReferenceById(deal.id()));
        lead.setReceivedAt(clock.instant());
        inboundLeadRepository.save(lead);

        return new LeadIntakeResultDTO(customer.getId(), deal.id(), newCustomer, false);
    }

    @Transactional(readOnly = true)
    public List<InboundLeadDTO> recent(int limit) {
        return inboundLeadRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(InboundLeadDTO::from)
                .toList();
    }

    public boolean isNetlifyEnabled() {
        return netlifyVerifier.isEnabled();
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

    private static String dealTitle(NetlifySubmission submission) {
        String title = submission.result() != null
                ? "Diagnóstico: " + submission.result()
                : "Lead do formulário" + (submission.formName() != null ? " " + submission.formName() : "");
        return title.length() <= TITLE_MAX ? title : title.substring(0, TITLE_MAX - 1) + "…";
    }

    private static String notes(NetlifySubmission submission) {
        List<String> lines = new ArrayList<>();
        lines.add("Origem: Netlify Forms" + (submission.formName() != null ? " (" + submission.formName() + ")" : ""));
        if (submission.result() != null) lines.add("Resultado do diagnóstico: " + submission.result());
        if (submission.score() != null) lines.add("Placar: " + submission.score());
        return String.join("\n", lines);
    }
}

package com.jessica.crm_vendas.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.dto.DealRequestDTO;
import com.jessica.crm_vendas.dto.DealResponseDTO;
import com.jessica.crm_vendas.dto.StageChangeRequestDTO;
import com.jessica.crm_vendas.event.DealStageChangedEvent;
import com.jessica.crm_vendas.exception.BusinessRuleException;
import com.jessica.crm_vendas.exception.ResourceNotFoundException;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.DealStage;
import com.jessica.crm_vendas.model.DealStageChange;
import com.jessica.crm_vendas.repository.CustomerRepository;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.DealSpecifications;
import com.jessica.crm_vendas.repository.DealStageChangeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DealService {

    private final DealRepository repository;
    private final CustomerRepository customerRepository;
    private final DealStageChangeRepository stageChangeRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<DealResponseDTO> list(String text, Long customerId) {
        return repository.findAll(DealSpecifications.search(text, customerId), Sort.by(Sort.Direction.DESC, "stageChangedAt"))
                .stream()
                .map(DealResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DealResponseDTO getById(Long id) {
        return DealResponseDTO.from(findOrThrow(id));
    }

    @Transactional
    public DealResponseDTO create(DealRequestDTO data) {
        Deal deal = new Deal();
        applyFields(deal, data);
        DealStage stage = data.stage() == null ? DealStage.PROSPECT : data.stage();
        enterStage(deal, stage, data.lostReason());
        return DealResponseDTO.from(repository.save(deal));
    }

    @Transactional
    public DealResponseDTO update(Long id, DealRequestDTO data) {
        Deal deal = findOrThrow(id);
        applyFields(deal, data);

        if (data.stage() != null && data.stage() != deal.getStage()) {
            enterStage(deal, data.stage(), data.lostReason());
        } else if (deal.getStage() == DealStage.LOST) {
            deal.setLostReason(requireLostReason(data.lostReason()));
        }

        return DealResponseDTO.from(repository.saveAndFlush(deal));
    }

    /** Move o negócio de etapa (o que acontece ao arrastar um card no Kanban). */
    @Transactional
    public DealResponseDTO changeStage(Long id, StageChangeRequestDTO data) {
        Deal deal = findOrThrow(id);
        if (data.stage() != deal.getStage()) {
            enterStage(deal, data.stage(), data.lostReason());
        }
        return DealResponseDTO.from(repository.saveAndFlush(deal));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private void enterStage(Deal deal, DealStage stage, String lostReason) {
        DealStage previous = deal.getId() == null ? null : deal.getStage();
        Instant now = clock.instant();

        deal.setLostReason(stage == DealStage.LOST ? requireLostReason(lostReason) : null);
        deal.setStage(stage);
        deal.setStageChangedAt(now);
        deal.setClosedAt(stage.isClosed() ? now : null);

        if (deal.getId() == null) {
            repository.save(deal);
        }
        stageChangeRepository.save(new DealStageChange(deal, previous, stage, now));
        events.publishEvent(new DealStageChangedEvent(deal.getId(), previous, stage, now));
    }

    private void applyFields(Deal deal, DealRequestDTO data) {
        deal.setTitle(data.title().trim());
        deal.setValue(data.value());
        deal.setSource(data.source());
        deal.setExpectedCloseDate(data.expectedCloseDate());
        deal.setCustomer(findCustomer(data.customerId()));
    }

    private static String requireLostReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Informe o motivo da perda.");
        }
        return reason.trim();
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado com o ID: " + customerId));
    }

    private Deal findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Negócio não encontrado com o ID: " + id));
    }
}

package com.jessica.crm_vendas.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.dto.ActivityRequestDTO;
import com.jessica.crm_vendas.dto.TimelineItemDTO;
import com.jessica.crm_vendas.exception.BusinessRuleException;
import com.jessica.crm_vendas.exception.ResourceNotFoundException;
import com.jessica.crm_vendas.model.Activity;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.DealStage;
import com.jessica.crm_vendas.model.DealStageChange;
import com.jessica.crm_vendas.model.InboundLead;
import com.jessica.crm_vendas.model.Task;
import com.jessica.crm_vendas.repository.ActivityRepository;
import com.jessica.crm_vendas.repository.CustomerRepository;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.DealStageChangeRepository;
import com.jessica.crm_vendas.repository.InboundLeadRepository;
import com.jessica.crm_vendas.repository.TaskRepository;
import com.jessica.crm_vendas.security.TenantContext;

import lombok.RequiredArgsConstructor;

/** Atividades registradas e a linha do tempo completa do cliente. */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final Map<DealStage, String> STAGE_LABELS = Map.of(
            DealStage.PROSPECT, "Prospecto",
            DealStage.QUALIFIED, "Qualificado",
            DealStage.PROPOSAL, "Proposta",
            DealStage.WON, "Ganho",
            DealStage.LOST, "Perdido");

    private final ActivityRepository activityRepository;
    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final DealStageChangeRepository stageChangeRepository;
    private final InboundLeadRepository inboundLeadRepository;
    private final TaskRepository taskRepository;
    private final TenantContext tenant;
    private final Clock clock;

    @Transactional
    public TimelineItemDTO create(Long customerId, ActivityRequestDTO data) {
        Long org = tenant.organizationId();
        Customer customer = customerRepository.findByIdAndOrganizationId(customerId, org)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado com o ID: " + customerId));

        Activity activity = new Activity();
        activity.setOrganizationId(org);
        activity.setCustomer(customer);
        activity.setDeal(data.dealId() == null ? null : dealOfCustomer(data.dealId(), customer, org));
        activity.setType(data.type());
        activity.setContent(data.content().trim());
        activity.setAuthorName(tenant.userName());
        activity.setCreatedAt(clock.instant());
        return toItem(activityRepository.save(activity));
    }

    @Transactional
    public void delete(Long activityId) {
        Activity activity = activityRepository.findByIdAndOrganizationId(activityId, tenant.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Atividade não encontrada com o ID: " + activityId));
        activityRepository.delete(activity);
    }

    /**
     * Tudo o que aconteceu com o cliente, do mais recente ao mais antigo:
     * atividades registradas, mudanças de etapa dos negócios, leads recebidos e tarefas concluídas.
     */
    @Transactional(readOnly = true)
    public List<TimelineItemDTO> timeline(Long customerId) {
        Long org = tenant.organizationId();
        customerRepository.findByIdAndOrganizationId(customerId, org)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado com o ID: " + customerId));

        List<TimelineItemDTO> items = new ArrayList<>();
        activityRepository.findByOrganizationIdAndCustomerIdOrderByCreatedAtDesc(org, customerId)
                .forEach(activity -> items.add(toItem(activity)));
        stageChangeRepository.findTimeline(org, customerId)
                .forEach(change -> items.add(toItem(change)));
        inboundLeadRepository.findByOrganizationIdAndCustomerIdOrderByReceivedAtDesc(org, customerId)
                .forEach(lead -> items.add(toItem(lead)));
        taskRepository.findByOrganizationIdAndCustomerIdAndDoneAtIsNotNull(org, customerId)
                .forEach(task -> items.add(toItem(task)));

        items.sort(Comparator.comparing(TimelineItemDTO::at).reversed());
        return items;
    }

    private Deal dealOfCustomer(Long dealId, Customer customer, Long org) {
        Deal deal = dealRepository.findByIdAndOrganizationId(dealId, org)
                .orElseThrow(() -> new ResourceNotFoundException("Negócio não encontrado com o ID: " + dealId));
        if (!deal.getCustomer().getId().equals(customer.getId())) {
            throw new BusinessRuleException("O negócio escolhido é de outro cliente.");
        }
        return deal;
    }

    private static TimelineItemDTO toItem(Activity activity) {
        Deal deal = activity.getDeal();
        return new TimelineItemDTO(activity.getType().name(), null, activity.getContent(), activity.getCreatedAt(),
                deal == null ? null : deal.getId(), deal == null ? null : deal.getTitle(),
                activity.getAuthorName(), activity.getId());
    }

    private static TimelineItemDTO toItem(DealStageChange change) {
        Deal deal = change.getDeal();
        String title = change.getFromStage() == null
                ? "Negócio criado em " + STAGE_LABELS.get(change.getToStage())
                : "Etapa: " + STAGE_LABELS.get(change.getFromStage()) + " → " + STAGE_LABELS.get(change.getToStage());
        String detail = change.getToStage() == DealStage.LOST && deal.getLostReason() != null
                ? "Motivo: " + deal.getLostReason()
                : null;
        return new TimelineItemDTO("STAGE_CHANGE", title, detail, change.getChangedAt(),
                deal.getId(), deal.getTitle(), null, null);
    }

    private static TimelineItemDTO toItem(InboundLead lead) {
        Deal deal = lead.getDeal();
        String title = "CSV".equals(lead.getProvider())
                ? "Importado da planilha " + lead.getFormName()
                : "Lead recebido pelo formulário" + (lead.getFormName() != null ? " " + lead.getFormName() : "");
        return new TimelineItemDTO("LEAD_RECEIVED", title, null, lead.getReceivedAt(),
                deal == null ? null : deal.getId(), deal == null ? null : deal.getTitle(), null, null);
    }

    private static TimelineItemDTO toItem(Task task) {
        Deal deal = task.getDeal();
        return new TimelineItemDTO("TASK_DONE", "Tarefa concluída", task.getTitle(), task.getDoneAt(),
                deal == null ? null : deal.getId(), deal == null ? null : deal.getTitle(), null, null);
    }
}

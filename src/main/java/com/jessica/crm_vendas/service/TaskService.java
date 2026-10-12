package com.jessica.crm_vendas.service;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.dto.TaskRequestDTO;
import com.jessica.crm_vendas.dto.TaskResponseDTO;
import com.jessica.crm_vendas.exception.BusinessRuleException;
import com.jessica.crm_vendas.exception.ResourceNotFoundException;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.Task;
import com.jessica.crm_vendas.repository.CustomerRepository;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.TaskRepository;
import com.jessica.crm_vendas.repository.TaskSpecifications;
import com.jessica.crm_vendas.security.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository repository;
    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final TenantContext tenant;
    private final Clock clock;

    /** Pendentes ordenadas pelo prazo mais próximo; concluídas pelas mais recentes. */
    @Transactional(readOnly = true)
    public List<TaskResponseDTO> list(Boolean done, Long customerId, Long dealId) {
        Sort sort = Boolean.TRUE.equals(done)
                ? Sort.by(Sort.Direction.DESC, "doneAt")
                : Sort.by(Sort.Direction.ASC, "dueAt");
        return repository.findAll(TaskSpecifications.search(tenant.organizationId(), done, customerId, dealId), sort)
                .stream()
                .map(TaskResponseDTO::from)
                .toList();
    }

    @Transactional
    public TaskResponseDTO create(TaskRequestDTO data) {
        Task task = new Task();
        task.setOrganizationId(tenant.organizationId());
        task.setCreatedAt(clock.instant());
        apply(task, data);
        return TaskResponseDTO.from(repository.save(task));
    }

    @Transactional
    public TaskResponseDTO update(Long id, TaskRequestDTO data) {
        Task task = findOrThrow(id);
        apply(task, data);
        return TaskResponseDTO.from(repository.saveAndFlush(task));
    }

    /** Marca ou desmarca como concluída. */
    @Transactional
    public TaskResponseDTO setDone(Long id, boolean done) {
        Task task = findOrThrow(id);
        task.setDoneAt(done ? clock.instant() : null);
        return TaskResponseDTO.from(repository.saveAndFlush(task));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private void apply(Task task, TaskRequestDTO data) {
        Long org = tenant.organizationId();
        Deal deal = data.dealId() == null ? null : dealRepository.findByIdAndOrganizationId(data.dealId(), org)
                .orElseThrow(() -> new ResourceNotFoundException("Negócio não encontrado com o ID: " + data.dealId()));
        Customer customer = data.customerId() == null ? null : customerRepository.findByIdAndOrganizationId(data.customerId(), org)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado com o ID: " + data.customerId()));

        if (deal != null && customer != null && !deal.getCustomer().getId().equals(customer.getId())) {
            throw new BusinessRuleException("O negócio escolhido é de outro cliente.");
        }
        task.setTitle(data.title().trim());
        task.setDueAt(data.dueAt());
        task.setDeal(deal);
        // Informando só o negócio, a tarefa fica também na ficha do cliente dele
        task.setCustomer(customer != null ? customer : deal != null ? deal.getCustomer() : null);
    }

    private Task findOrThrow(Long id) {
        return repository.findByIdAndOrganizationId(id, tenant.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada com o ID: " + id));
    }
}

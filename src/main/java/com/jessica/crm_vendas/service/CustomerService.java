package com.jessica.crm_vendas.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.dto.CustomerRequestDTO;
import com.jessica.crm_vendas.dto.CustomerResponseDTO;
import com.jessica.crm_vendas.exception.ConflictException;
import com.jessica.crm_vendas.exception.ResourceNotFoundException;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.CustomerStatus;
import com.jessica.crm_vendas.repository.CustomerRepository;
import com.jessica.crm_vendas.repository.CustomerSpecifications;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.security.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;
    private final DealRepository dealRepository;
    private final TenantContext tenant;

    @Transactional(readOnly = true)
    public Page<CustomerResponseDTO> search(String text, CustomerStatus status, Pageable pageable) {
        return repository.findAll(CustomerSpecifications.search(tenant.organizationId(), text, status), pageable)
                .map(CustomerResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponseDTO getById(Long id) {
        return CustomerResponseDTO.from(findOrThrow(id));
    }

    @Transactional
    public CustomerResponseDTO create(CustomerRequestDTO data) {
        Long organizationId = tenant.organizationId();
        String email = normalizeEmail(data.email());
        if (email != null && repository.existsByOrganizationIdAndEmail(organizationId, email)) {
            throw new ConflictException("E-mail já cadastrado no sistema!");
        }

        Customer customer = new Customer();
        customer.setOrganizationId(organizationId);
        apply(customer, data, email);
        return CustomerResponseDTO.from(repository.save(customer));
    }

    @Transactional
    public CustomerResponseDTO update(Long id, CustomerRequestDTO data) {
        Customer customer = findOrThrow(id);

        String email = normalizeEmail(data.email());
        if (email != null && repository.existsByOrganizationIdAndEmailAndIdNot(customer.getOrganizationId(), email, id)) {
            throw new ConflictException("E-mail já cadastrado em outro cliente!");
        }

        apply(customer, data, email);
        return CustomerResponseDTO.from(repository.saveAndFlush(customer));
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = findOrThrow(id);
        if (dealRepository.existsByCustomerId(id)) {
            throw new ConflictException("Este cliente tem negócios no funil. Exclua os negócios antes de excluir o cliente.");
        }
        repository.delete(customer);
    }

    /** Cliente de outra organização responde "não encontrado", como se não existisse. */
    private Customer findOrThrow(Long id) {
        return repository.findByIdAndOrganizationId(id, tenant.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado com o ID: " + id));
    }

    private static void apply(Customer customer, CustomerRequestDTO data, String email) {
        customer.setName(data.name().trim());
        customer.setEmail(email);
        customer.setPhone(blankToNull(data.phone()));
        customer.setCompany(blankToNull(data.company()));
        customer.setStatus(data.status() == null ? CustomerStatus.LEAD : data.status());
    }

    private static String normalizeEmail(String email) {
        String value = blankToNull(email);
        return value == null ? null : value.toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

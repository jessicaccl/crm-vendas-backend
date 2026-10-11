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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;

    @Transactional(readOnly = true)
    public Page<CustomerResponseDTO> search(String text, CustomerStatus status, Pageable pageable) {
        return repository.findAll(CustomerSpecifications.search(text, status), pageable)
                .map(CustomerResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponseDTO getById(Long id) {
        return CustomerResponseDTO.from(findOrThrow(id));
    }

    @Transactional
    public CustomerResponseDTO create(CustomerRequestDTO data) {
        String email = normalizeEmail(data.email());
        if (email != null && repository.existsByEmail(email)) {
            throw new ConflictException("E-mail já cadastrado no sistema!");
        }

        Customer customer = new Customer();
        apply(customer, data, email);
        return CustomerResponseDTO.from(repository.save(customer));
    }

    @Transactional
    public CustomerResponseDTO update(Long id, CustomerRequestDTO data) {
        Customer customer = findOrThrow(id);

        String email = normalizeEmail(data.email());
        if (email != null && repository.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("E-mail já cadastrado em outro cliente!");
        }

        apply(customer, data, email);
        return CustomerResponseDTO.from(repository.saveAndFlush(customer));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private Customer findOrThrow(Long id) {
        return repository.findById(id)
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

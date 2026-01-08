package com.jessica.crm_vendas.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.jessica.crm_vendas.dto.CustomerRequestDTO;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.repository.CustomerRepository;

@Service
public class CustomerService {

    @Autowired
    private CustomerRepository repository;

    public Customer saveCustomer(CustomerRequestDTO data) {
        if (repository.existsByEmail(data.email())) {
            throw new RuntimeException("E-mail já cadastrado no sistema!");
        }

        Customer customer = new Customer();
        customer.setName(data.name());
        customer.setEmail(data.email());
        customer.setPhone(data.phone());
        
        if (data.status() == null || data.status().isEmpty()) {
            customer.setStatus("PROSPECT");
        } else {
            customer.setStatus(data.status());
        }

        return repository.save(customer);
    }

    public List<Customer> getAllCustomers() {
        return repository.findAll();
    }

    public Customer getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado com o ID: " + id));
    }

    public Customer updateCustomer(Long id, Customer data) {
        Customer customer = getById(id); 
        
        customer.setName(data.getName());
        customer.setEmail(data.getEmail());
        customer.setPhone(data.getPhone());
        customer.setStatus(data.getStatus());
        
        return repository.save(customer);
    }

    public void deleteCustomer(Long id) {
        Customer customer = getById(id); 
        repository.delete(customer);
    }
}
package com.jessica.crm_vendas.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jessica.crm_vendas.dto.CustomerRequestDTO;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.service.CustomerService; 

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {

    @Autowired
    private CustomerService service;

    @GetMapping
    public List<Customer> getAll() {
        return service.getAllCustomers();
    }

    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody @Valid CustomerRequestDTO data) {
        Customer savedCustomer = service.saveCustomer(data);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCustomer);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id) {
        Customer customer = service.getById(id);
        return ResponseEntity.ok(customer);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(@PathVariable Long id, @RequestBody Customer customerDetails) {
        Customer updated = service.updateCustomer(id, customerDetails);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
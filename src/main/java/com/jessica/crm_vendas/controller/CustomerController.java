package com.jessica.crm_vendas.controller;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jessica.crm_vendas.dto.CustomerRequestDTO;
import com.jessica.crm_vendas.dto.CustomerResponseDTO;
import com.jessica.crm_vendas.model.CustomerStatus;
import com.jessica.crm_vendas.service.CustomerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Cadastro e consulta de clientes")
public class CustomerController {

    private final CustomerService service;

    @GetMapping
    @Operation(summary = "Lista clientes com busca, filtro de status e paginação")
    public Page<CustomerResponseDTO> list(
            @Parameter(description = "Texto buscado no nome, e-mail ou empresa") @RequestParam(required = false) String q,
            @RequestParam(required = false) CustomerStatus status,
            @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(q, status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um cliente pelo ID")
    public CustomerResponseDTO getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @Operation(summary = "Cadastra um cliente")
    public ResponseEntity<CustomerResponseDTO> create(@RequestBody @Valid CustomerRequestDTO data) {
        CustomerResponseDTO created = service.create(data);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um cliente")
    public CustomerResponseDTO update(@PathVariable Long id, @RequestBody @Valid CustomerRequestDTO data) {
        return service.update(id, data);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um cliente")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

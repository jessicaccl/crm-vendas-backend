package com.jessica.crm_vendas.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jessica.crm_vendas.dto.DealRequestDTO;
import com.jessica.crm_vendas.dto.DealResponseDTO;
import com.jessica.crm_vendas.dto.StageChangeRequestDTO;
import com.jessica.crm_vendas.service.DealService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
@Tag(name = "Negócios", description = "Oportunidades de venda e etapas do funil")
public class DealController {

    private final DealService service;

    @GetMapping
    @Operation(summary = "Lista negócios (mais recentes na etapa primeiro)")
    public List<DealResponseDTO> list(
            @Parameter(description = "Texto buscado no título, contato ou empresa") @RequestParam(required = false) String q,
            @RequestParam(required = false) Long customerId) {
        return service.list(q, customerId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um negócio pelo ID")
    public DealResponseDTO getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @Operation(summary = "Cria um negócio")
    public ResponseEntity<DealResponseDTO> create(@RequestBody @Valid DealRequestDTO data) {
        DealResponseDTO created = service.create(data);
        return ResponseEntity.created(URI.create("/api/v1/deals/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um negócio")
    public DealResponseDTO update(@PathVariable Long id, @RequestBody @Valid DealRequestDTO data) {
        return service.update(id, data);
    }

    @PatchMapping("/{id}/stage")
    @Operation(summary = "Move o negócio de etapa", description = "Grava o histórico e publica o evento de mudança de etapa. Para LOST, informe lostReason.")
    public DealResponseDTO changeStage(@PathVariable Long id, @RequestBody @Valid StageChangeRequestDTO data) {
        return service.changeStage(id, data);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um negócio")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

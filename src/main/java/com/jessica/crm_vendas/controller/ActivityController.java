package com.jessica.crm_vendas.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jessica.crm_vendas.dto.ActivityRequestDTO;
import com.jessica.crm_vendas.dto.TimelineItemDTO;
import com.jessica.crm_vendas.service.ActivityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Ficha do cliente", description = "Linha do tempo e atividades registradas")
public class ActivityController {

    private final ActivityService service;

    @GetMapping("/customers/{customerId}/timeline")
    @Operation(summary = "Linha do tempo do cliente",
            description = "Atividades registradas, mudanças de etapa dos negócios, leads recebidos e tarefas concluídas, do mais recente ao mais antigo.")
    public List<TimelineItemDTO> timeline(@PathVariable Long customerId) {
        return service.timeline(customerId);
    }

    @PostMapping("/customers/{customerId}/activities")
    @Operation(summary = "Registra uma atividade (anotação, ligação, WhatsApp, e-mail, reunião)")
    public ResponseEntity<TimelineItemDTO> create(@PathVariable Long customerId, @RequestBody @Valid ActivityRequestDTO data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(customerId, data));
    }

    @DeleteMapping("/activities/{id}")
    @Operation(summary = "Exclui uma atividade registrada")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

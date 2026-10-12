package com.jessica.crm_vendas.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jessica.crm_vendas.dto.TaskRequestDTO;
import com.jessica.crm_vendas.dto.TaskResponseDTO;
import com.jessica.crm_vendas.service.TaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tarefas", description = "Próximos passos com prazo, ligados a clientes e negócios")
public class TaskController {

    private final TaskService service;

    public record DoneRequest(boolean done) {}

    @GetMapping
    @Operation(summary = "Lista tarefas (pendentes pelo prazo; concluídas pelas mais recentes)")
    public List<TaskResponseDTO> list(
            @Parameter(description = "true = concluídas, false = pendentes, vazio = todas") @RequestParam(required = false) Boolean done,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long dealId) {
        return service.list(done, customerId, dealId);
    }

    @PostMapping
    @Operation(summary = "Cria uma tarefa")
    public ResponseEntity<TaskResponseDTO> create(@RequestBody @Valid TaskRequestDTO data) {
        TaskResponseDTO created = service.create(data);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma tarefa")
    public TaskResponseDTO update(@PathVariable Long id, @RequestBody @Valid TaskRequestDTO data) {
        return service.update(id, data);
    }

    @PatchMapping("/{id}/done")
    @Operation(summary = "Marca ou desmarca a tarefa como concluída")
    public TaskResponseDTO setDone(@PathVariable Long id, @RequestBody DoneRequest data) {
        return service.setDone(id, data.done());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma tarefa")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

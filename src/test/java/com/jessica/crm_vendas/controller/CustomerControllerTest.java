package com.jessica.crm_vendas.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jessica.crm_vendas.dto.CustomerResponseDTO;
import com.jessica.crm_vendas.exception.ConflictException;
import com.jessica.crm_vendas.exception.ResourceNotFoundException;
import com.jessica.crm_vendas.model.CustomerStatus;
import com.jessica.crm_vendas.service.CustomerService;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerService service;

    @Test
    void createReturns201WithLocation() throws Exception {
        Instant now = Instant.now();
        when(service.create(any())).thenReturn(new CustomerResponseDTO(
                1L, "Ana", "ana@empresa.com", null, null, CustomerStatus.LEAD, now, now));

        mvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@empresa.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/1"))
                .andExpect(jsonPath("$.status").value("LEAD"));
    }

    @Test
    void createReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors.name").value("Informe o nome"))
                .andExpect(jsonPath("$.errors.email").value("E-mail inválido"));
    }

    @Test
    void createReturns409WhenEmailExists() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("E-mail já cadastrado no sistema!"));

        mvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@empresa.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado no sistema!"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(service.getById(eq(42L))).thenThrow(new ResourceNotFoundException("Cliente não encontrado com o ID: 42"));

        mvc.perform(get("/api/v1/customers/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void invalidStatusFilterReturns400() throws Exception {
        mvc.perform(get("/api/v1/customers").param("status", "QUALQUER"))
                .andExpect(status().isBadRequest());
    }
}

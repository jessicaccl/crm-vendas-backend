package com.jessica.crm_vendas.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jessica.crm_vendas.exception.BusinessRuleException;
import com.jessica.crm_vendas.service.DealService;

@WebMvcTest(DealController.class)
class DealControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DealService service;

    @Test
    void createValidatesRequiredFields() throws Exception {
        mvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"value\":-10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Informe o título"))
                .andExpect(jsonPath("$.errors.customerId").value("Escolha o cliente"))
                .andExpect(jsonPath("$.errors.value").value("O valor não pode ser negativo"));
    }

    @Test
    void changeStageReturns422WhenRuleIsBroken() throws Exception {
        when(service.changeStage(eq(5L), any())).thenThrow(new BusinessRuleException("Informe o motivo da perda."));

        mvc.perform(patch("/api/v1/deals/5/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"LOST\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Informe o motivo da perda."));
    }

    @Test
    void changeStageRejectsUnknownStage() throws Exception {
        mvc.perform(patch("/api/v1/deals/5/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"FECHADO\"}"))
                .andExpect(status().isBadRequest());
    }
}

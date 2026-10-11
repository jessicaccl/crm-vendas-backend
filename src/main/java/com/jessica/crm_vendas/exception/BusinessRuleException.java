package com.jessica.crm_vendas.exception;

/** Pedido válido no formato, mas que quebra uma regra de negócio (responde 422). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}

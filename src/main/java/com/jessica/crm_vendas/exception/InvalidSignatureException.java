package com.jessica.crm_vendas.exception;

/** Webhook recebido sem a assinatura correta (responde 401). */
public class InvalidSignatureException extends RuntimeException {
    public InvalidSignatureException(String message) {
        super(message);
    }
}

package com.jessica.crm_vendas.exception;

/** Token sem organização (emitido antes do multiempresa): responde 401 para o frontend pedir login de novo. */
public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException() {
        super("Sua sessão expirou. Faça login novamente.");
    }
}

package com.jessica.crm_vendas.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Descobre o IP de quem fez a requisição.
 *
 * Atrás de um proxy, o IP da conexão é o do proxy. Por isso, quando CLIENT_IP_HEADER está definido,
 * usamos esse cabeçalho. No Render o certo é "CF-Connecting-IP": o Cloudflare sempre o sobrescreve,
 * então o cliente não consegue falsificá-lo (ao contrário do X-Forwarded-For, que o Render só complementa).
 * Só defina a variável quando houver um proxy confiável preenchendo o cabeçalho.
 */
@Component
public class ClientIpResolver {

    private final String header;

    public ClientIpResolver(@Value("${app.client-ip-header:}") String header) {
        this.header = header;
    }

    public String resolve(HttpServletRequest request) {
        if (StringUtils.hasText(header)) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}

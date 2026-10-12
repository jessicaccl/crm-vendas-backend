package com.jessica.crm_vendas.security;

import java.util.function.Supplier;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.jessica.crm_vendas.exception.SessionExpiredException;

/**
 * De qual organização é o pedido atual.
 * Em requisições logadas vem do token (claim "org"); em webhooks, que não têm login,
 * o código define a organização explicitamente com {@link #callAs}.
 * Todo serviço que lê ou grava dados de negócio filtra por este valor.
 */
@Component
public class TenantContext {

    public static final String ORG_CLAIM = "org";
    public static final String ROLE_CLAIM = "role";

    private static final ThreadLocal<Long> OVERRIDE = new ThreadLocal<>();

    public Long organizationId() {
        Long override = OVERRIDE.get();
        if (override != null) {
            return override;
        }
        Object claim = jwt() == null ? null : jwt().getToken().getClaim(ORG_CLAIM);
        if (claim instanceof Number number) {
            return number.longValue();
        }
        // Token antigo (anterior ao multiempresa) ou ausente: pede um login novo
        throw new SessionExpiredException();
    }

    public boolean isAdmin() {
        JwtAuthenticationToken jwt = jwt();
        return jwt != null && "ADMIN".equals(jwt.getToken().getClaimAsString(ROLE_CLAIM));
    }

    /** Nome de quem está logado (claim "name"), para assinar as atividades registradas. */
    public String userName() {
        JwtAuthenticationToken jwt = jwt();
        return jwt == null ? null : jwt.getToken().getClaimAsString("name");
    }

    /** Executa o trecho como se o pedido fosse da organização informada (ex.: webhook). */
    public <T> T callAs(Long organizationId, Supplier<T> action) {
        Long previous = OVERRIDE.get();
        OVERRIDE.set(organizationId);
        try {
            return action.get();
        } finally {
            if (previous == null) OVERRIDE.remove();
            else OVERRIDE.set(previous);
        }
    }

    private static JwtAuthenticationToken jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token ? token : null;
    }
}

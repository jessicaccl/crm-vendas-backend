package com.jessica.crm_vendas.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** Gera a assinatura X-Webhook-Signature do jeito que o Netlify faz. */
public final class NetlifyTestSupport {

    public static final String SECRET = "segredo-do-webhook-netlify-com-32-chars";

    private NetlifyTestSupport() {}

    public static String sign(String body, String secret, String issuer) throws Exception {
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().issuer(issuer).claim("sha256", hash).build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    public static String sign(String body) throws Exception {
        return sign(body, SECRET, "netlify");
    }

    /** Envio no formato do webhook do Netlify para o form do quiz da Unic. */
    public static String submission(String id, String nome, String telefone, String email) {
        return """
            {
              "id": "%s",
              "form_name": "leads-diagnostico",
              "site_url": "https://diagnostico-unic.netlify.app",
              "created_at": "2026-10-11T14:00:00.000Z",
              "data": {
                "nome": "%s",
                "telefone": "%s",
                "email": "%s",
                "resultado": "Página Profissional (One Page)",
                "placar": "Presença: 12 · Social: 4 · Automação: 2",
                "ip": "200.0.0.1"
              }
            }
            """.formatted(id, nome, telefone, email);
    }
}

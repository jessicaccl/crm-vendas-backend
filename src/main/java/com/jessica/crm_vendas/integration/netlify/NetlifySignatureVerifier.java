package com.jessica.crm_vendas.integration.netlify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;

/**
 * Confere o cabeçalho X-Webhook-Signature que o Netlify envia nos webhooks de formulário:
 * um JWT HS256 assinado com o "JWS secret token" configurado no Netlify, com
 * iss = "netlify" e sha256 = hash do corpo da requisição. Assim só o Netlify consegue criar leads.
 */
@Component
public class NetlifySignatureVerifier {

    static final int MIN_SECRET_BYTES = 32;

    private final byte[] secret;

    public NetlifySignatureVerifier(@Value("${app.integrations.netlify.secret}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length > 0 && this.secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "NETLIFY_WEBHOOK_SECRET precisa ter pelo menos " + MIN_SECRET_BYTES + " caracteres.");
        }
    }

    /** A integração só fica ativa quando o segredo está configurado. */
    public boolean isEnabled() {
        return secret.length > 0;
    }

    public boolean isValid(String signature, byte[] body) {
        if (!isEnabled() || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(signature.trim());
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                return false;
            }
            var claims = jwt.getJWTClaimsSet();
            String expectedHash = sha256Hex(body);
            return "netlify".equals(claims.getIssuer())
                    && MessageDigest.isEqual(
                            expectedHash.getBytes(StandardCharsets.US_ASCII),
                            String.valueOf(claims.getClaim("sha256")).toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (ParseException | JOSEException e) {
            return false;
        }
    }

    public static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

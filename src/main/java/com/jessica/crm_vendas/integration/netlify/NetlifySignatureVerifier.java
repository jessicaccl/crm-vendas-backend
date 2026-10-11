package com.jessica.crm_vendas.integration.netlify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;

/**
 * Confere o cabeçalho X-Webhook-Signature que o Netlify envia nos webhooks de formulário:
 * um JWT HS256 assinado com o "JWS secret token" da organização, com
 * iss = "netlify" e sha256 = hash do corpo da requisição. Assim só o Netlify consegue criar leads.
 */
@Component
public class NetlifySignatureVerifier {

    public static final int MIN_SECRET_BYTES = 32;

    public boolean isValid(String secret, String signature, byte[] body) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES
                || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(signature.trim());
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(secret.getBytes(StandardCharsets.UTF_8)))) {
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

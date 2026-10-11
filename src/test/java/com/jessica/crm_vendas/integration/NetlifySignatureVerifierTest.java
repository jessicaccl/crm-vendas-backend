package com.jessica.crm_vendas.integration;

import static com.jessica.crm_vendas.integration.NetlifyTestSupport.SECRET;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.jessica.crm_vendas.integration.netlify.NetlifySignatureVerifier;

class NetlifySignatureVerifierTest {

    private final NetlifySignatureVerifier verifier = new NetlifySignatureVerifier();
    private final String body = NetlifyTestSupport.submission("abc", "Ana", "21999990000", "");

    private byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void acceptsSignatureFromNetlify() throws Exception {
        assertThat(verifier.isValid(SECRET, NetlifyTestSupport.sign(body), bytes(body))).isTrue();
    }

    @Test
    void rejectsSecretOfAnotherOrganization() throws Exception {
        String signature = NetlifyTestSupport.sign(body, "outro-segredo-qualquer-com-32-caracteres", "netlify");
        assertThat(verifier.isValid(SECRET, signature, bytes(body))).isFalse();
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        String signature = NetlifyTestSupport.sign(body);
        assertThat(verifier.isValid(SECRET, signature, bytes(body.replace("Ana", "Hacker")))).isFalse();
    }

    @Test
    void rejectsOtherIssuerAndMissingSignature() throws Exception {
        assertThat(verifier.isValid(SECRET, NetlifyTestSupport.sign(body, SECRET, "outro"), bytes(body))).isFalse();
        assertThat(verifier.isValid(SECRET, null, bytes(body))).isFalse();
        assertThat(verifier.isValid(SECRET, "nao-e-um-jwt", bytes(body))).isFalse();
    }

    @Test
    void rejectsMissingOrShortSecret() throws Exception {
        String signature = NetlifyTestSupport.sign(body);
        assertThat(verifier.isValid(null, signature, bytes(body))).isFalse();
        assertThat(verifier.isValid("curto", signature, bytes(body))).isFalse();
    }
}

package com.jessica.crm_vendas.integration;

import static com.jessica.crm_vendas.integration.NetlifyTestSupport.SECRET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.jessica.crm_vendas.integration.netlify.NetlifySignatureVerifier;

class NetlifySignatureVerifierTest {

    private final NetlifySignatureVerifier verifier = new NetlifySignatureVerifier(SECRET);
    private final String body = NetlifyTestSupport.submission("abc", "Ana", "21999990000", "");

    private byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void acceptsSignatureFromNetlify() throws Exception {
        assertThat(verifier.isValid(NetlifyTestSupport.sign(body), bytes(body))).isTrue();
    }

    @Test
    void rejectsWrongSecret() throws Exception {
        String signature = NetlifyTestSupport.sign(body, "outro-segredo-qualquer-com-32-caracteres", "netlify");
        assertThat(verifier.isValid(signature, bytes(body))).isFalse();
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        String signature = NetlifyTestSupport.sign(body);
        assertThat(verifier.isValid(signature, bytes(body.replace("Ana", "Hacker")))).isFalse();
    }

    @Test
    void rejectsOtherIssuerAndMissingSignature() throws Exception {
        assertThat(verifier.isValid(NetlifyTestSupport.sign(body, SECRET, "outro"), bytes(body))).isFalse();
        assertThat(verifier.isValid(null, bytes(body))).isFalse();
        assertThat(verifier.isValid("nao-e-um-jwt", bytes(body))).isFalse();
    }

    @Test
    void disabledWithoutSecretAndRejectsShortSecret() {
        assertThat(new NetlifySignatureVerifier("").isEnabled()).isFalse();
        assertThatThrownBy(() -> new NetlifySignatureVerifier("curto"))
                .isInstanceOf(IllegalStateException.class);
    }
}

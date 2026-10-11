package com.jessica.crm_vendas.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.jessica.crm_vendas.model.User;

/** Gera o JWT entregue no login. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration expiration;

    public TokenService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.expiration}") Duration expiration) {
        this.encoder = encoder;
        this.clock = clock;
        this.expiration = expiration;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(expiration);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("crm-vendas")
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}

package com.jessica.crm_vendas.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.dto.LoginRequestDTO;
import com.jessica.crm_vendas.dto.LoginResponseDTO;
import com.jessica.crm_vendas.dto.UserResponseDTO;
import com.jessica.crm_vendas.exception.ResourceNotFoundException;
import com.jessica.crm_vendas.model.Organization;
import com.jessica.crm_vendas.model.User;
import com.jessica.crm_vendas.repository.OrganizationRepository;
import com.jessica.crm_vendas.repository.UserRepository;
import com.jessica.crm_vendas.security.LoginAttemptLimiter;
import com.jessica.crm_vendas.security.TokenService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TokenService tokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    /**
     * Confere e-mail e senha (lança BadCredentialsException se não baterem) e devolve um token.
     * Muitos erros seguidos do mesmo IP bloqueiam o login por um tempo (TooManyLoginAttemptsException).
     */
    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO data, String clientIp) {
        String email = data.email().trim().toLowerCase();
        loginAttemptLimiter.checkAllowed(email, clientIp);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, data.password()));
        } catch (AuthenticationException exception) {
            loginAttemptLimiter.recordFailure(email, clientIp);
            throw exception;
        }
        loginAttemptLimiter.recordSuccess(email, clientIp);

        User user = findByEmail(email);
        TokenService.IssuedToken token = tokenService.issue(user);
        return new LoginResponseDTO(token.value(), token.expiresAt(), UserResponseDTO.from(user, organizationOf(user)));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO me(String email) {
        User user = findByEmail(email);
        return UserResponseDTO.from(user, organizationOf(user));
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private Organization organizationOf(User user) {
        return organizationRepository.findById(user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organização não encontrada"));
    }
}

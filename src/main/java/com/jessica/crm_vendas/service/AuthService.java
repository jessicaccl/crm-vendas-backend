package com.jessica.crm_vendas.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.dto.LoginRequestDTO;
import com.jessica.crm_vendas.dto.LoginResponseDTO;
import com.jessica.crm_vendas.dto.UserResponseDTO;
import com.jessica.crm_vendas.exception.ResourceNotFoundException;
import com.jessica.crm_vendas.model.User;
import com.jessica.crm_vendas.repository.UserRepository;
import com.jessica.crm_vendas.security.TokenService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    /** Confere e-mail e senha (lança BadCredentialsException se não baterem) e devolve um token. */
    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO data) {
        String email = data.email().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, data.password()));

        User user = findByEmail(email);
        TokenService.IssuedToken token = tokenService.issue(user);
        return new LoginResponseDTO(token.value(), token.expiresAt(), UserResponseDTO.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO me(String email) {
        return UserResponseDTO.from(findByEmail(email));
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }
}

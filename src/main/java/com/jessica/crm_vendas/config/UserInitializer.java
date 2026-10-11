package com.jessica.crm_vendas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.jessica.crm_vendas.model.User;
import com.jessica.crm_vendas.model.UserRole;
import com.jessica.crm_vendas.repository.UserRepository;

/**
 * Garante os usuários iniciais:
 * - demonstração (DEMO_USER_*), para recrutadores testarem a demo online;
 * - administradora (ADMIN_EMAIL / ADMIN_PASSWORD), criada só se as duas variáveis existirem.
 * Usuários que já existem não são alterados.
 */
@Component
@Order(0)
public class UserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final boolean demoEnabled;
    private final String demoEmail;
    private final String demoPassword;
    private final String adminEmail;
    private final String adminPassword;

    public UserInitializer(UserRepository users, PasswordEncoder passwordEncoder,
                           @Value("${app.demo-user.enabled}") boolean demoEnabled,
                           @Value("${app.demo-user.email}") String demoEmail,
                           @Value("${app.demo-user.password}") String demoPassword,
                           @Value("${app.admin.email}") String adminEmail,
                           @Value("${app.admin.password}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.demoEnabled = demoEnabled;
        this.demoEmail = demoEmail;
        this.demoPassword = demoPassword;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoEnabled) {
            ensureUser("Usuário Demo", demoEmail, demoPassword, UserRole.DEMO);
        }
        if (!adminEmail.isBlank() && !adminPassword.isBlank()) {
            ensureUser("Administradora", adminEmail, adminPassword, UserRole.ADMIN);
        }
    }

    private void ensureUser(String name, String email, String password, UserRole role) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            return;
        }
        User user = new User();
        user.setName(name);
        user.setEmail(normalized);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        users.save(user);
        log.info("Usuário {} criado: {}", role, normalized);
    }
}

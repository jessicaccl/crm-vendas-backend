package com.jessica.crm_vendas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.model.Organization;
import com.jessica.crm_vendas.model.User;
import com.jessica.crm_vendas.model.UserRole;
import com.jessica.crm_vendas.repository.OrganizationRepository;
import com.jessica.crm_vendas.repository.UserRepository;

/**
 * Garante as organizações e os usuários iniciais:
 * - organização "demo" com o usuário público de demonstração (DEMO_USER_*);
 * - organização da administradora (ADMIN_ORG_NAME / ADMIN_ORG_SLUG) com a conta ADMIN_EMAIL / ADMIN_PASSWORD.
 * Senhas de usuários existentes não são alteradas; a administradora é movida para a organização
 * configurada se estiver em outra (ex.: contas criadas antes do multiempresa ficaram na Demo).
 */
@Component
@Order(0)
public class UserInitializer implements ApplicationRunner {

    public static final String DEMO_SLUG = "demo";

    private static final Logger log = LoggerFactory.getLogger(UserInitializer.class);

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;
    private final boolean demoEnabled;
    private final String demoEmail;
    private final String demoPassword;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminOrgName;
    private final String adminOrgSlug;

    public UserInitializer(UserRepository users, OrganizationRepository organizations, PasswordEncoder passwordEncoder,
                           @Value("${app.demo-user.enabled}") boolean demoEnabled,
                           @Value("${app.demo-user.email}") String demoEmail,
                           @Value("${app.demo-user.password}") String demoPassword,
                           @Value("${app.admin.email}") String adminEmail,
                           @Value("${app.admin.password}") String adminPassword,
                           @Value("${app.admin.org-name}") String adminOrgName,
                           @Value("${app.admin.org-slug}") String adminOrgSlug) {
        this.users = users;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.demoEnabled = demoEnabled;
        this.demoEmail = demoEmail;
        this.demoPassword = demoPassword;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminOrgName = adminOrgName;
        this.adminOrgSlug = adminOrgSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoEnabled) {
            Organization demo = ensureOrganization("Demo CRM Pro", DEMO_SLUG, true);
            ensureUser("Usuário Demo", demoEmail, demoPassword, UserRole.DEMO, demo);
        }
        if (!adminEmail.isBlank() && !adminPassword.isBlank()) {
            Organization company = ensureOrganization(adminOrgName, adminOrgSlug, false);
            ensureUser("Administradora", adminEmail, adminPassword, UserRole.ADMIN, company);
        }
    }

    private Organization ensureOrganization(String name, String slug, boolean demo) {
        return organizations.findBySlug(slug).orElseGet(() -> {
            log.info("Organização criada: {} ({})", name, slug);
            return organizations.save(new Organization(name, slug, demo));
        });
    }

    private void ensureUser(String name, String email, String password, UserRole role, Organization organization) {
        String normalized = email.trim().toLowerCase();
        users.findByEmail(normalized).ifPresentOrElse(
            existing -> {
                if (!existing.getOrganizationId().equals(organization.getId())) {
                    existing.setOrganizationId(organization.getId());
                    log.info("Usuário {} movido para a organização {}", normalized, organization.getSlug());
                }
            },
            () -> {
                User user = new User();
                user.setName(name);
                user.setEmail(normalized);
                user.setPasswordHash(passwordEncoder.encode(password));
                user.setRole(role);
                user.setOrganizationId(organization.getId());
                users.save(user);
                log.info("Usuário {} criado: {} ({})", role, normalized, organization.getSlug());
            });
    }
}

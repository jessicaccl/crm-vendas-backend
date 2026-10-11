package com.jessica.crm_vendas.dto;

import com.jessica.crm_vendas.model.Organization;
import com.jessica.crm_vendas.model.User;
import com.jessica.crm_vendas.model.UserRole;

public record UserResponseDTO(Long id, String name, String email, UserRole role, OrganizationSummary organization) {

    public record OrganizationSummary(Long id, String name, String slug, boolean demo) {
        public static OrganizationSummary from(Organization organization) {
            return new OrganizationSummary(organization.getId(), organization.getName(), organization.getSlug(), organization.isDemo());
        }
    }

    public static UserResponseDTO from(User user, Organization organization) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                OrganizationSummary.from(organization));
    }
}

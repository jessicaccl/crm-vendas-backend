package com.jessica.crm_vendas.dto;

import com.jessica.crm_vendas.model.User;
import com.jessica.crm_vendas.model.UserRole;

public record UserResponseDTO(Long id, String name, String email, UserRole role) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}

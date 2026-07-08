package com.dopamin.omok.global.security.principal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.dopamin.omok.user.domain.UserRole;

public record AuthUser(
    Long id,
    UUID publicId,
    String email,
    String nickname,
    UserRole role
) {

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isGuest() {
        return role == UserRole.GUEST;
    }
}

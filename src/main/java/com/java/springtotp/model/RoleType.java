package com.java.springtotp.model;

import java.util.Set;

public enum RoleType {
    ADMIN(Set.of("ROLE_READ", "ROLE_WRITE", "ROLE_DELETE")),
    USER(Set.of("ROLE_READ"));

    private final Set<String> permissions;

    RoleType(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}

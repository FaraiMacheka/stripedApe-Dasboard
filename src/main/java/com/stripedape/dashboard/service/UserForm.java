package com.stripedape.dashboard.service;

import com.stripedape.dashboard.domain.AppRole;
import java.util.EnumSet;
import java.util.Set;

public class UserForm {

    private Long id;
    private String username;
    private String displayName;
    private String password;
    private boolean enabled = true;
    private Set<AppRole> roles = EnumSet.of(AppRole.VIEWER);

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<AppRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<AppRole> roles) {
        this.roles = roles;
    }
}


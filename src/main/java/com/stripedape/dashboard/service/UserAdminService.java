package com.stripedape.dashboard.service;

import com.stripedape.dashboard.config.AppProperties;
import com.stripedape.dashboard.domain.AppRole;
import com.stripedape.dashboard.domain.AppUser;
import com.stripedape.dashboard.repository.AppUserRepository;
import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public UserAdminService(AppUserRepository repository, PasswordEncoder passwordEncoder, AppProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @PostConstruct
    @Transactional
    public void seedUsers() {
        if (repository.count() > 0) {
            return;
        }
        saveUser(properties.getBootstrapUsers().getAdminUsername(), properties.getBootstrapUsers().getAdminPassword(), "StripedApe Admin", true, Set.of(AppRole.ADMIN));
        saveUser(properties.getBootstrapUsers().getManagerUsername(), properties.getBootstrapUsers().getManagerPassword(), "StripedApe Manager", true, Set.of(AppRole.MANAGER));
        saveUser(properties.getBootstrapUsers().getViewerUsername(), properties.getBootstrapUsers().getViewerPassword(), "StripedApe Viewer", true, Set.of(AppRole.VIEWER));
    }

    public List<AppUser> listUsers() {
        return repository.findAll();
    }

    public AppUser getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AppUser save(UserForm form) {
        AppUser user = form.getId() == null ? new AppUser() : getRequired(form.getId());
        user.setUsername(form.getUsername());
        user.setDisplayName(form.getDisplayName());
        user.setEnabled(form.isEnabled());
        user.setRoles(form.getRoles());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        } else if (user.getPasswordHash() == null) {
            user.setPasswordHash(passwordEncoder.encode("ChangeMe123!"));
        }
        return repository.save(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private void saveUser(String username, String password, String displayName, boolean enabled, Set<AppRole> roles) {
        AppUser user = repository.findByUsername(username).orElseGet(AppUser::new);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setEnabled(enabled);
        user.setRoles(roles);
        repository.save(user);
    }
}


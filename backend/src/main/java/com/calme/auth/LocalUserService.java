package com.calme.auth;

import com.calme.user.AppUser;
import com.calme.user.AppUserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalUserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String password;

    public LocalUserService(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${calme.auth.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
    }

    /**
     * Idempotent: called at startup and before a login, so a request that races
     * the startup runner can still authenticate.
     */
    @Transactional
    public void ensureInitialized() {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "CALME_AUTH_PASSWORD est obligatoire pour le compte local. Voir .env.example.");
        }

        AppUser user = users.findByEmailIgnoreCase(LocalAccount.EMAIL)
                .orElseGet(() -> users.save(new AppUser(LocalAccount.EMAIL, "UTC")));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
    }
}

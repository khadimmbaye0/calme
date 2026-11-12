package com.calme.auth;

import com.calme.user.AppUser;
import com.calme.user.AppUserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LocalUserInitializer implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String password;

    public LocalUserInitializer(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${calme.auth.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
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

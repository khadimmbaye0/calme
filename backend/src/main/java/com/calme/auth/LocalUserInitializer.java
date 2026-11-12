package com.calme.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LocalUserInitializer implements ApplicationRunner {

    private final LocalUserService localUser;

    public LocalUserInitializer(LocalUserService localUser) {
        this.localUser = localUser;
    }

    @Override
    public void run(ApplicationArguments args) {
        localUser.ensureInitialized();
    }
}

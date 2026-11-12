package com.calme.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) {
        this.users = users;
    }

    /**
     * Returns the single local user. Authentication is not implemented yet, so the
     * application relies on the V2 migration seed instead of a session or token.
     */
    @Transactional(readOnly = true)
    public AppUser requireCurrentUser() {
        return users.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Aucun utilisateur local n'est configuré."));
    }
}

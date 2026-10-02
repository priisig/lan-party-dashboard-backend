package com.lanparty.dashboard.auth;

import java.io.Serializable;

import com.lanparty.dashboard.user.AppUser;
import com.lanparty.dashboard.user.UserRole;

/** What the session remembers about the logged-in account. */
public record UserPrincipal(Long id, String nickname, UserRole role) implements Serializable {

    public static UserPrincipal of(AppUser user) {
        return new UserPrincipal(user.getId(), user.getNickname(), user.getRole());
    }

    public boolean isOrga() {
        return role == UserRole.ORGA;
    }
}

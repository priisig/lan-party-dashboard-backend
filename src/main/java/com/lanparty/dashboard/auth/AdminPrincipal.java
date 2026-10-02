package com.lanparty.dashboard.auth;

import java.io.Serializable;

public record AdminPrincipal(Long id, String name) implements Serializable {
}

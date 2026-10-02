package com.lanparty.dashboard.admin;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** An organiser. Logs in with a personal fixed numeric code (stored as bcrypt hash). */
@Entity
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String codeHash;
    /** Last two digits of the code, shown in the admin list so people can tell codes apart. */
    private String codeHint;
    private boolean enabled = true;
    private Instant createdAt = Instant.now();

    protected Admin() {
    }

    public Admin(String name, String codeHash, String codeHint) {
        this.name = name;
        this.codeHash = codeHash;
        this.codeHint = codeHint;
    }

    public void changeCode(String codeHash, String codeHint) {
        this.codeHash = codeHash;
        this.codeHint = codeHint;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCodeHash() { return codeHash; }
    public String getCodeHint() { return codeHint; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}

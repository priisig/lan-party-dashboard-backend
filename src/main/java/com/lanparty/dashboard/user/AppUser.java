package com.lanparty.dashboard.user;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An account. Accounts are global, so the same person keeps their profile across LAN editions. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;
    private String email;
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.USER;

    private boolean enabled = true;
    private String firstName;
    private String lastName;
    private String steam;
    private String discord;
    private String team;
    private String favouriteGame;
    private boolean showOnSeatmap = true;
    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(String nickname, String email, String passwordHash, UserRole role) {
        this.nickname = nickname;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public boolean isOrga() {
        return role == UserRole.ORGA;
    }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getSteam() { return steam; }
    public void setSteam(String steam) { this.steam = steam; }
    public String getDiscord() { return discord; }
    public void setDiscord(String discord) { this.discord = discord; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public String getFavouriteGame() { return favouriteGame; }
    public void setFavouriteGame(String favouriteGame) { this.favouriteGame = favouriteGame; }
    public boolean isShowOnSeatmap() { return showOnSeatmap; }
    public void setShowOnSeatmap(boolean showOnSeatmap) { this.showOnSeatmap = showOnSeatmap; }
    public Instant getCreatedAt() { return createdAt; }
}

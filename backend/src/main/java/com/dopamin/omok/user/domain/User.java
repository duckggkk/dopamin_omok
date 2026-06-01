package com.dopamin.omok.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_nickname", columnList = "nickname"),
        @Index(name = "idx_users_public_id", columnList = "public_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "password")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 200)
    private String password;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(length = 200)
    private String providerId;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(nullable = false)
    private Integer wins = 0;

    @Column(nullable = false)
    private Integer losses = 0;

    @Column(nullable = false)
    private Integer draws = 0;

    @Column(nullable = false)
    private Integer currency = 0;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private Long tokenVersion = 0L;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private User(String email, String password, String nickname,
                 UserRole role, AuthProvider provider, String providerId,
                 String profileImageUrl, boolean emailVerified) {
        this.publicId = UUID.randomUUID();
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.profileImageUrl = profileImageUrl;
        this.emailVerified = emailVerified;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
        this.currency = 0;
        this.tokenVersion = 0L;
    }

    public static User createLocalUser(String email, String encodedPassword, String nickname) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .role(UserRole.USER)
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();
    }

    public static User createSocialUser(String email, String nickname, AuthProvider provider,
                                        String providerId, String profileImageUrl) {
        return User.builder()
                .email(email)
                .nickname(nickname)
                .role(UserRole.USER)
                .provider(provider)
                .providerId(providerId)
                .profileImageUrl(profileImageUrl)
                .emailVerified(true)
                .build();
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void incrementTokenVersion() {
        this.tokenVersion = (this.tokenVersion == null ? 0L : this.tokenVersion) + 1L;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void recordWin() {
        this.wins++;
    }

    public void recordLoss() {
        this.losses++;
    }

    public void recordDraw() {
        this.draws++;
    }

    public int getTotalGames() {
        return wins + losses + draws;
    }

    public void chargeCurrency(int amount) {
        this.currency = (this.currency == null ? 0 : this.currency) + amount;
    }

    public void spendCurrency(int amount) {
        int current = this.currency == null ? 0 : this.currency;
        if (current < amount) throw new IllegalStateException("잔액이 부족합니다.");
        this.currency = current - amount;
    }
}

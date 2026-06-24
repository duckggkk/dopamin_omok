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

    /** 일반 오목 ELO 레이팅. 신규 유저는 1000점에서 시작하며, 일반 대국 승/패/무로 가감된다. */
    @Column(nullable = false)
    private Integer classicRating = 1000;

    /** 피지컬 오목 ELO 레이팅. 일반과 별개로 1000점에서 시작해 피지컬 대국 결과로만 가감된다. */
    @Column(nullable = false)
    private Integer physicalRating = 1000;

    /** 싱글플레이 AI 사다리에서 클리어한 최고 단계(0 = 미클리어). 다음 단계 해제 기준이 된다. */
    @Column(nullable = false)
    private Integer aiClearedLevel = 0;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private boolean profilePrivate = false;

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
        this.profilePrivate = false;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
        this.currency = 0;
        this.classicRating = 1000;
        this.physicalRating = 1000;
        this.aiClearedLevel = 0;
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

    public void updateProfilePrivate(boolean profilePrivate) {
        this.profilePrivate = profilePrivate;
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

    /** 시스템 봇 계정 여부(피지컬 AI 연습 상대). 봇과의 대국은 레이팅·전적을 집계하지 않는다. */
    public boolean isBot() {
        return this.role == UserRole.BOT;
    }

    /** null-safe 싱글플레이 AI 클리어 단계 조회(과거 데이터/직렬화 경로에서도 항상 0 이상). */
    public int getAiClearedLevel() {
        return this.aiClearedLevel == null ? 0 : this.aiClearedLevel;
    }

    /**
     * 싱글플레이 AI 사다리에서 한 단계를 클리어했음을 기록한다.
     * 무결성: 정확히 "다음 단계"(현재+1)일 때만 전진한다 — 클라이언트가 단계를 건너뛰어
     * 보고해도 한 칸씩만 올라가므로 사다리 순서가 보장된다. 이미 깬 단계 재클리어는 무시.
     */
    public void recordAiClear(int level) {
        if (level == getAiClearedLevel() + 1) {
            this.aiClearedLevel = level;
        }
    }

    /** null-safe 일반 레이팅 조회 — 과거 데이터/직렬화 경로에서도 항상 유효한 점수를 보장한다. */
    public int getClassicRating() {
        return this.classicRating == null ? EloRating.DEFAULT_RATING : this.classicRating;
    }

    /** null-safe 피지컬 레이팅 조회. */
    public int getPhysicalRating() {
        return this.physicalRating == null ? EloRating.DEFAULT_RATING : this.physicalRating;
    }

    /** 모드(physical 여부)에 해당하는 레이팅을 반환한다. */
    public int getRating(boolean physical) {
        return physical ? getPhysicalRating() : getClassicRating();
    }

    /**
     * ELO 계산 결과(delta)를 해당 모드 레이팅에 더한다.
     * 하한(EloRating.MIN_RATING) 미만으로는 내려가지 않는다.
     */
    public void adjustRating(boolean physical, int delta) {
        if (physical) {
            this.physicalRating = Math.max(EloRating.MIN_RATING, getPhysicalRating() + delta);
        } else {
            this.classicRating = Math.max(EloRating.MIN_RATING, getClassicRating() + delta);
        }
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

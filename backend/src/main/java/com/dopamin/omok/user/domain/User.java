package com.dopamin.omok.user.domain;

import com.dopamin.omok.game.domain.GameType;
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

    /**
     * 탈퇴 시각. null 이면 활성 회원이다.
     * <p>
     * 탈퇴는 행 삭제가 아니라 <b>익명화</b>로 처리한다({@link #anonymize()}). 사용자를 물리 삭제하면
     * rooms→games 로 이어지는 ON DELETE CASCADE 때문에 <b>상대방의 대국 기록까지</b> 함께 지워지기
     * 때문이다(V37 마이그레이션 주석 참고).
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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

    /**
     * 비회원(게스트) 계정을 생성한다. 회원가입 없이 익명으로 발급되며 비밀번호가 없다.
     * email/nickname 은 호출부가 충돌하지 않게 생성해 넘긴다(예: email=guest_…, nickname=게스트####).
     */
    public static User createGuestUser(String email, String nickname) {
        return User.builder()
                .email(email)
                .nickname(nickname)
                .role(UserRole.GUEST)
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

    /** 비회원(게스트) 계정 여부. 멤버 전용 기능 차단·랭크전 제외 판단에 쓴다. */
    public boolean isGuest() {
        return this.role == UserRole.GUEST;
    }

    /** 탈퇴한 계정 여부. 활성 사용자 조회는 모두 이 값이 false 인 행만 돌려준다. */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    /**
     * 탈퇴 처리 — 행은 남기고 <b>개인정보만 파기</b>한다.
     * <p>
     * 이메일·닉네임은 UNIQUE 제약이 걸려 있으므로 지우는 대신 충돌하지 않는 값으로 덮어쓴다.
     * 원래 이메일이 비워지므로 <b>같은 이메일로 다시 가입할 수 있다</b>.
     * 반대로 새 닉네임({@code 탈퇴한사용자_<id>})은 유니크 검사에서 계속 '사용 중'으로 잡혀야
     * 다른 사람이 그 이름을 가져가 과거 기보의 탈퇴자를 사칭하는 일이 없다
     * ({@code CheckUserExistsPort} 가 탈퇴 행까지 포함해 검사하는 이유).
     * <p>
     * 전적·레이팅 컬럼은 남긴다 — 상대방 대국 기록의 무결성에 쓰이는 값이라 지우면 안 된다.
     * 개인을 식별하는 값이 아니므로 파기 대상도 아니다.
     */
    public void anonymize(LocalDateTime now) {
        this.email = "deleted_" + this.publicId + "@deleted.local";
        this.nickname = "탈퇴한사용자_" + this.id;
        this.password = null;
        this.profileImageUrl = null;
        this.providerId = null;
        this.emailVerified = false;   // 로그인 경로의 2차 방어선
        this.profilePrivate = true;   // 프로필 조회 경로의 2차 방어선
        this.deletedAt = now;
        incrementTokenVersion();      // 발급된 refresh token 재사용 차단
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

    /**
     * 게임 종류에 해당하는 레이팅을 반환한다.
     *
     * <p>default 절을 두지 않은 switch <b>식</b>이라, GameType 에 상수가 추가되면
     * 여기서 컴파일 에러가 난다. 새 종류가 어느 점수판을 쓸지 결정하지 않은 채로
     * 조용히 일반 레이팅이 깎이는 일을 막기 위한 의도적 장치다.</p>
     */
    public int getRating(GameType type) {
        return switch (type) {
            case CLASSIC -> getClassicRating();
            case PHYSICAL -> getPhysicalRating();
        };
    }

    /**
     * ELO 계산 결과(delta)를 해당 종류의 레이팅에 더한다.
     * 하한(EloRating.MIN_RATING) 미만으로는 내려가지 않는다.
     */
    public void adjustRating(GameType type, int delta) {
        int updated = Math.max(EloRating.MIN_RATING, getRating(type) + delta);
        switch (type) {
            case CLASSIC -> this.classicRating = updated;
            case PHYSICAL -> this.physicalRating = updated;
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

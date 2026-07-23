package com.dopamin.omok.user.adapter.out.persistence;

import com.dopamin.omok.config.JpaAuditingConfig;
import com.dopamin.omok.config.QuerydslConfig;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴(익명화)한 회원이 <b>조회에서는 사라지되 유니크 자원은 계속 점유</b>하는지 검증한다.
 * 이 구분이 어긋나면 (1) 탈퇴자가 로그인·랭킹에 다시 나타나거나
 * (2) 익명 닉네임을 다른 사람이 가져가 UNIQUE 제약 위반으로 가입이 터진다.
 */
// QuerydslConfig 는 방 검색 리포지토리(RoomRepositoryCustomImpl)가 JPAQueryFactory 를 요구해서 필요하다.
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, UserPersistenceAdapter.class})
class UserSoftDeleteQueryTest {

    private static final String EMAIL = "quit@example.com";
    private static final String NICKNAME = "떠나는사람";

    @Autowired private TestEntityManager entityManager;
    @Autowired private UserPersistenceAdapter userAdapter;

    private User withdrawn;
    private UUID withdrawnPublicId;
    private Long withdrawnId;
    private String anonymizedNickname;

    @BeforeEach
    void setUp() {
        withdrawn = User.createLocalUser(EMAIL, "encoded", NICKNAME);
        withdrawn.verifyEmail();
        withdrawn.recordWin(); // 랭킹 대상이 되려면 한 판 이상 필요
        entityManager.persist(withdrawn);
        entityManager.flush();

        withdrawnId = withdrawn.getId();
        withdrawnPublicId = withdrawn.getPublicId();

        withdrawn.anonymize(LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        anonymizedNickname = "탈퇴한사용자_" + withdrawnId;
    }

    @Test
    @DisplayName("탈퇴 회원은 id·이메일·닉네임·publicId 어느 것으로도 조회되지 않는다")
    void withdrawnUserIsInvisibleToAllLookups() {
        assertThat(userAdapter.findById(withdrawnId)).isEmpty();
        assertThat(userAdapter.findByEmail(EMAIL)).isEmpty();
        assertThat(userAdapter.findByNickname(NICKNAME)).isEmpty();
        assertThat(userAdapter.findByPublicId(withdrawnPublicId)).isEmpty();
        // 익명화된 값으로도 조회되면 안 된다(로그인 우회 방지)
        assertThat(userAdapter.findByNickname(anonymizedNickname)).isEmpty();
    }

    @Test
    @DisplayName("탈퇴 회원은 랭킹에서 빠진다")
    void withdrawnUserIsExcludedFromRanking() {
        User active = User.createLocalUser("active@example.com", "encoded", "현역");
        active.verifyEmail();
        active.recordWin();
        entityManager.persist(active);
        entityManager.flush();

        assertThat(userAdapter.findTopRanked(10)).extracting(User::getId).containsExactly(active.getId());
        assertThat(userAdapter.findTopRankedByClassicRating(10)).extracting(User::getId)
                .doesNotContain(withdrawnId);
        assertThat(userAdapter.findTopRankedByPhysicalRating(10)).extracting(User::getId)
                .doesNotContain(withdrawnId);
    }

    @Test
    @DisplayName("익명화된 닉네임은 계속 '사용 중'이라 다른 사람이 가져갈 수 없다")
    void anonymizedNicknameStaysReserved() {
        assertThat(userAdapter.existsByNickname(anonymizedNickname)).isTrue();
    }

    @Test
    @DisplayName("원래 닉네임은 풀려서 다시 쓸 수 있다")
    void originalNicknameIsReleased() {
        assertThat(userAdapter.existsByNickname(NICKNAME)).isFalse();
    }

    @Test
    @DisplayName("같은 이메일로 다시 가입할 수 있다 — 원래 이메일이 소멸했기 때문")
    void sameEmailCanRegisterAgain() {
        assertThat(userAdapter.existsByEmail(EMAIL)).isFalse();

        User rejoined = User.createLocalUser(EMAIL, "encoded", NICKNAME);
        rejoined.verifyEmail();
        entityManager.persist(rejoined);
        entityManager.flush(); // UNIQUE 제약 위반이 나면 여기서 터진다
        entityManager.clear();

        assertThat(userAdapter.findByEmail(EMAIL))
                .isPresent()
                .get()
                .extracting(User::getId)
                .isNotEqualTo(withdrawnId);
    }

    @Test
    @DisplayName("탈퇴해도 대국 기록용 전적·레이팅 값은 남는다")
    void gameRecordColumnsSurvive() {
        User raw = entityManager.find(User.class, withdrawnId);

        assertThat(raw).isNotNull();
        assertThat(raw.isDeleted()).isTrue();
        assertThat(raw.getWins()).isEqualTo(1);
        assertThat(raw.getClassicRating()).isEqualTo(1000);
        assertThat(raw.getNickname()).isEqualTo(anonymizedNickname);
    }
}

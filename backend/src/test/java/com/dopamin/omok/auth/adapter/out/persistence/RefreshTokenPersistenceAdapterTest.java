package com.dopamin.omok.auth.adapter.out.persistence;

import com.dopamin.omok.auth.domain.RefreshToken;
import com.dopamin.omok.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class RefreshTokenPersistenceAdapterTest {

    @Autowired RefreshTokenJpaRepository refreshTokenJpaRepository;

    private RefreshTokenPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RefreshTokenPersistenceAdapter(refreshTokenJpaRepository);
    }

    private RefreshToken savedToken(Long userId, String token) {
        return adapter.save(RefreshToken.of(userId, token, LocalDateTime.now().plusDays(7)));
    }

    @Test
    @DisplayName("토큰 저장 후 조회")
    void save_and_findByToken() {
        savedToken(1L, "tok-abc");

        Optional<RefreshToken> found = adapter.findByToken("tok-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 토큰은 빈 Optional")
    void findByToken_notFound() {
        assertThat(adapter.findByToken("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("토큰 삭제")
    void delete_token() {
        RefreshToken token = savedToken(2L, "tok-del");

        adapter.delete(token);

        assertThat(adapter.findByToken("tok-del")).isEmpty();
    }

    @Test
    @DisplayName("userId로 토큰 전체 삭제")
    void deleteByUserId() {
        savedToken(3L, "tok-user3-a");
        savedToken(3L, "tok-user3-b");

        adapter.deleteByUserId(3L);

        assertThat(refreshTokenJpaRepository.findByUserId(3L)).isEmpty();
    }

    @Test
    @DisplayName("만료 여부 확인 - 미래 만료일은 false")
    void isExpired_future_false() {
        RefreshToken token = savedToken(4L, "tok-valid");

        assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("만료 여부 확인 - 과거 만료일은 true")
    void isExpired_past_true() {
        RefreshToken expired = adapter.save(
                RefreshToken.of(5L, "tok-expired", LocalDateTime.now().minusSeconds(1)));

        assertThat(expired.isExpired()).isTrue();
    }
}

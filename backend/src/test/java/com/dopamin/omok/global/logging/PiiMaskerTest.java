package com.dopamin.omok.global.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    @DisplayName("이메일은 첫 글자와 도메인만 남기고 가린다")
    void masksEmail() {
        assertThat(PiiMasker.mask("이메일 발송 완료: hong@gmail.com"))
                .isEqualTo("이메일 발송 완료: h***@gmail.com");
    }

    @Test
    @DisplayName("문장 속 여러 이메일도 모두 마스킹된다")
    void masksMultipleEmails() {
        assertThat(PiiMasker.mask("from=admin@daum.net to=user.kim@naver.com"))
                .isEqualTo("from=a***@daum.net to=u***@naver.com");
    }

    @Test
    @DisplayName("JWT 토큰은 통째로 가린다")
    void masksJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.abc-DEF_123";
        assertThat(PiiMasker.mask("token=" + jwt)).isEqualTo("token=***JWT***");
    }

    @Test
    @DisplayName("Bearer 토큰 값은 가리고 접두어는 남긴다")
    void masksBearer() {
        assertThat(PiiMasker.mask("Authorization: Bearer abc123.def456"))
                .isEqualTo("Authorization: Bearer ***");
    }

    @Test
    @DisplayName("민감정보가 없으면 원문 그대로 둔다")
    void leavesPlainTextUntouched() {
        String plain = "POST /api/auth/login -> 200 (45ms)";
        assertThat(PiiMasker.mask(plain)).isEqualTo(plain);
    }

    @Test
    @DisplayName("null/빈 문자열은 그대로 반환한다")
    void handlesNullAndEmpty() {
        assertThat(PiiMasker.mask(null)).isNull();
        assertThat(PiiMasker.mask("")).isEmpty();
    }
}

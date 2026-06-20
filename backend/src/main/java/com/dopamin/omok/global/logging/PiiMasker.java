package com.dopamin.omok.global.logging;

import java.util.regex.Pattern;

/**
 * 로그에 섞여 나갈 수 있는 개인정보(PII)·비밀값을 가린다.
 *
 * <p>로그는 운영 중 디버깅에 꼭 필요하지만, 그대로 두면 이메일·토큰 같은
 * 민감정보가 파일/수집서버(Loki)에 영구히 남는다. 이 클래스는 메시지 문자열을
 * 정규식으로 훑어 그런 값만 골라 마스킹한다.
 *
 * <p>마스킹 대상:
 * <ul>
 *   <li>이메일 — {@code dopaminomok@gmail.com} → {@code d***@gmail.com}
 *       (도메인은 남긴다 — 어느 메일 제공자였는지는 디버깅에 유용하고 식별정보는 아님)</li>
 *   <li>JWT — {@code eyJ...} 3토막 구조 → {@code ***JWT***}</li>
 *   <li>Bearer 토큰 — {@code Bearer abc...} → {@code Bearer ***}</li>
 * </ul>
 *
 * <p>이 유틸은 {@link MaskingMessageConverter} 를 통해 <b>모든 로그</b>에 자동 적용된다.
 * 즉 개발자가 호출부마다 마스킹을 기억할 필요가 없고, 서드파티 라이브러리가 무심코
 * 찍는 로그까지 같은 안전망이 덮는다.
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    // 이메일: 로컬파트 첫 글자만 남기고 가린다. 도메인은 보존.
    private static final Pattern EMAIL = Pattern.compile(
            "([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    // JWT: base64url 3토막(헤더.페이로드.서명). 헤더는 거의 항상 "eyJ"로 시작.
    private static final Pattern JWT = Pattern.compile(
            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    // Authorization 헤더 등에 실리는 Bearer 토큰.
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9._\\-]+");

    /**
     * 메시지 안의 민감정보를 마스킹한 새 문자열을 돌려준다.
     * null/빈 문자열은 그대로 반환한다.
     */
    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = JWT.matcher(message).replaceAll("***JWT***");
        masked = BEARER.matcher(masked).replaceAll("$1***");
        masked = EMAIL.matcher(masked).replaceAll("$1***@$2");
        return masked;
    }
}

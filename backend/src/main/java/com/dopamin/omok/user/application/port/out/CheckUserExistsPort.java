package com.dopamin.omok.user.application.port.out;

/**
 * 이메일·닉네임 <b>중복 검사</b> 전용 포트.
 * <p>
 * {@link LoadUserPort} 와 달리 <b>탈퇴한 회원까지 포함해서</b> 검사한다. 탈퇴 시 닉네임은
 * {@code 탈퇴한사용자_<id>} 로 익명화되는데, 이 값이 '사용 가능'으로 보이면 다른 사람이 그
 * 이름으로 가입해 UNIQUE 제약을 깨거나 과거 기보에 남은 탈퇴자를 사칭할 수 있다.
 * <p>
 * 반대로 <b>원래 이메일은 탈퇴와 동시에 소멸</b>하므로(익명 주소로 덮어씀) 같은 이메일로
 * 다시 가입하는 것은 정상적으로 허용된다.
 */
public interface CheckUserExistsPort {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
}

package com.dopamin.omok.user.application.service;

import com.dopamin.omok.auth.application.port.out.DeleteRefreshTokenPort;
import com.dopamin.omok.game.application.port.out.LoadGamePlayerPort;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.user.application.port.in.WithdrawUseCase;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 회원 탈퇴 — <b>익명화(soft delete)</b>.
 *
 * <p>계정 행을 지우지 않는 이유는 {@code V37__add_user_deleted_at.sql} 주석에 정리돼 있다.
 * 요약하면 {@code rooms.host_id → games} 로 이어지는 ON DELETE CASCADE 때문에 물리 삭제 시
 * <b>상대방의 대국 기록까지</b> 함께 사라진다.
 *
 * <p>탈퇴 후 상태:
 * <ul>
 *   <li>개인정보(이메일·비밀번호·프로필 이미지·소셜 식별자) 파기 → {@link User#anonymize}</li>
 *   <li>{@code LoadUserPort} 의 모든 조회에서 제외 → 로그인·랭킹·친구찾기·방 참가가 전부 막힌다</li>
 *   <li>refresh token 폐기 → 새 access token 을 받을 수 없다</li>
 *   <li>과거 대국·기보는 그대로 남고, 상대방 화면에는 익명 닉네임으로 표시된다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithdrawService implements WithdrawUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final LoadGamePlayerPort loadGamePlayerPort;
    private final DeleteRefreshTokenPort deleteRefreshTokenPort;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void withdraw(Long userId, String rawPassword) {
        // 이미 탈퇴한 계정은 findById 에서 걸러지므로 여기서 USER_NOT_FOUND 로 끝난다(중복 탈퇴 방지).
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));

        if (user.isGuest()) {
            // 게스트는 애초에 개인정보가 없는 일회성 계정이고, 오래되면 스케줄러가 정리한다.
            throw new OmokException(ErrorCode.WITHDRAW_GUEST_NOT_ALLOWED);
        }

        // 대기·진행 중인 방에 남아 있으면 막는다. 탈퇴 즉시 조회에서 사라지므로,
        // 그대로 두면 상대방이 응답 없는 판에 갇힌다.
        if (loadGamePlayerPort.existsInActiveRoom(userId)) {
            throw new OmokException(ErrorCode.WITHDRAW_IN_ACTIVE_GAME);
        }

        // 비밀번호가 있는 계정(일반 가입)은 본인 확인을 거친다.
        // 소셜 전용 계정은 확인할 비밀번호가 없으므로 생략한다(프론트에서 문구 입력으로 대체).
        if (StringUtils.hasText(user.getPassword())) {
            if (!StringUtils.hasText(rawPassword)
                    || !passwordEncoder.matches(rawPassword, user.getPassword())) {
                throw new OmokException(ErrorCode.WITHDRAW_PASSWORD_MISMATCH);
            }
        }

        user.anonymize(LocalDateTime.now());
        saveUserPort.save(user);

        // 남아 있는 refresh token 폐기 — 이미 발급된 access token 은 만료(30분)까지 유효하지만,
        // 그 토큰으로 하는 요청도 사용자 조회 단계에서 전부 USER_NOT_FOUND 로 막힌다.
        deleteRefreshTokenPort.deleteByUserId(userId);

        log.info("회원 탈퇴 처리(익명화) 완료 - userId={}", userId);
    }
}

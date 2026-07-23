package com.dopamin.omok.user.adapter.out.persistence;

import com.dopamin.omok.user.application.port.out.CheckUserExistsPort;
import com.dopamin.omok.user.application.port.out.DeleteUserPort;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements LoadUserPort, SaveUserPort, CheckUserExistsPort, DeleteUserPort {

    private final UserJpaRepository userJpaRepository;

    // 아래 조회는 모두 '활성 사용자'만 돌려준다 — 탈퇴(deleted_at) 행은 보이지 않는다.
    // 앱의 사용자 조회가 전부 이 어댑터를 지나므로, 여기서 한 번 막으면 로그인·방 참가·상점·
    // 친구 등 모든 경로가 자동으로 탈퇴 계정을 거부한다(각 서비스는 USER_NOT_FOUND 로 실패).

    @Override
    public Optional<User> findById(Long userId) {
        return userJpaRepository.findActiveById(userId);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findActiveByEmail(email);
    }

    @Override
    public Optional<User> findByNickname(String nickname) {
        return userJpaRepository.findActiveByNickname(nickname);
    }

    @Override
    public Optional<User> findByPublicId(UUID publicId) {
        return userJpaRepository.findActiveByPublicId(publicId);
    }

    @Override
    public List<User> findTopRanked(int limit) {
        return userJpaRepository.findRanked(PageRequest.of(0, limit));
    }

    @Override
    public List<User> findTopRankedByClassicRating(int limit) {
        return userJpaRepository.findRankedByClassicRating(PageRequest.of(0, limit));
    }

    @Override
    public List<User> findTopRankedByPhysicalRating(int limit) {
        return userJpaRepository.findRankedByPhysicalRating(PageRequest.of(0, limit));
    }

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public int deductCurrency(Long userId, int amount) {
        return userJpaRepository.deductCurrency(userId, amount);
    }

    @Override
    public void deleteById(Long userId) {
        userJpaRepository.deleteByIdImmediately(userId);
    }

    @Override
    public int deleteGuestsCreatedBefore(java.time.LocalDateTime cutoff) {
        return userJpaRepository.deleteGuestsCreatedBefore(cutoff);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return userJpaRepository.existsByNickname(nickname);
    }
}

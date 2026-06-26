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

    @Override
    public Optional<User> findById(Long userId) {
        return userJpaRepository.findById(userId);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByNickname(String nickname) {
        return userJpaRepository.findByNickname(nickname);
    }

    @Override
    public Optional<User> findByPublicId(UUID publicId) {
        return userJpaRepository.findByPublicId(publicId);
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

package com.dopamin.omok.auth.adapter.out.persistence;

import com.dopamin.omok.auth.application.port.out.DeleteEmailVerificationTokenPort;
import com.dopamin.omok.auth.application.port.out.LoadEmailVerificationTokenPort;
import com.dopamin.omok.auth.application.port.out.SaveEmailVerificationTokenPort;
import com.dopamin.omok.auth.domain.EmailVerificationToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmailVerificationTokenPersistenceAdapter
        implements SaveEmailVerificationTokenPort, LoadEmailVerificationTokenPort, DeleteEmailVerificationTokenPort {

    private final EmailVerificationTokenJpaRepository repository;

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        return repository.save(token);
    }

    @Override
    public Optional<EmailVerificationToken> findByToken(String token) {
        return repository.findByToken(token);
    }

    @Override
    public Optional<EmailVerificationToken> findByUserIdAndToken(Long userId, String token) {
        return repository.findByUserIdAndToken(userId, token);
    }

    @Override
    public void delete(EmailVerificationToken token) {
        repository.delete(token);
    }

    @Override
    public void deleteByUserId(Long userId) {
        repository.deleteByUserId(userId);
    }
}

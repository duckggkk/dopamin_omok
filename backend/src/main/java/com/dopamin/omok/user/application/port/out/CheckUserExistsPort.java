package com.dopamin.omok.user.application.port.out;

public interface CheckUserExistsPort {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
}

package com.dopamin.omok.user.application.port.out;

import com.dopamin.omok.user.domain.User;

public interface SaveUserPort {
    User save(User user);

    /**
     * 잔액이 충분할 때에만 재화를 원자적으로 차감한다(동시성 안전).
     * 단일 UPDATE ... WHERE currency >= amount 로 처리되어 동시 요청에도
     * 잔액 이상 차감/중복 차감이 발생하지 않는다.
     *
     * @return 실제로 차감된 행 수(1=성공, 0=잔액 부족)
     */
    int deductCurrency(Long userId, int amount);
}

package com.dopamin.omok.shop.application.service;

import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.shop.application.port.out.LoadItemPort;
import com.dopamin.omok.shop.application.port.out.LoadUserActiveItemPort;
import com.dopamin.omok.shop.application.port.out.LoadUserItemPort;
import com.dopamin.omok.shop.application.port.out.SaveUserActiveItemPort;
import com.dopamin.omok.shop.application.port.out.SaveUserItemPort;
import com.dopamin.omok.shop.config.ShopProperties;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private LoadItemPort loadItemPort;
    @Mock private LoadUserItemPort loadUserItemPort;
    @Mock private SaveUserItemPort saveUserItemPort;
    @Mock private LoadUserActiveItemPort loadUserActiveItemPort;
    @Mock private SaveUserActiveItemPort saveUserActiveItemPort;

    private ShopService shopService(boolean directChargeEnabled) {
        ShopProperties properties = new ShopProperties(
                List.of(new ShopProperties.CurrencyPackage("SMALL", 50, 5000)),
                List.of(),
                directChargeEnabled);
        return new ShopService(properties, loadUserPort, saveUserPort, loadItemPort,
                loadUserItemPort, saveUserItemPort, loadUserActiveItemPort, saveUserActiveItemPort);
    }

    @Test
    @DisplayName("직접 충전이 비활성(운영)이면 차단되고 사용자 조회조차 하지 않는다")
    void chargeDisabledIsBlocked() {
        assertThatThrownBy(() -> shopService(false).chargeCurrency(1L, "SMALL"))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DIRECT_CHARGE_DISABLED));

        verify(loadUserPort, never()).findById(any());
    }

    @Test
    @DisplayName("직접 충전이 활성이면 재화가 증가한다")
    void chargeEnabledAddsCurrency() {
        User user = User.createLocalUser("u@x.com", "pw", "nick"); // currency 0
        when(loadUserPort.findById(1L)).thenReturn(Optional.of(user));
        when(saveUserPort.save(any())).thenReturn(user);

        int balance = shopService(true).chargeCurrency(1L, "SMALL");

        assertThat(balance).isEqualTo(50);
        assertThat(user.getCurrency()).isEqualTo(50);
    }

    @Test
    @DisplayName("존재하지 않는 패키지는 INVALID_REQUEST")
    void unknownPackageIsRejected() {
        assertThatThrownBy(() -> shopService(true).chargeCurrency(1L, "NOPE"))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}

package com.dopamin.omok.plaza.application;

import com.dopamin.omok.plaza.application.dto.PlazaJoinResponse;
import com.dopamin.omok.plaza.application.port.out.PlazaEventPublisherPort;
import com.dopamin.omok.plaza.config.PlazaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REST allocate() 는 채널을 먼저 만들고, 실제 입장은 뒤이은 WS join() 이 채운다.
 * join() 이 끝내 안 오면(새로고침·연결 끊김 등) 빈 채널이 영영 안 지워지는 문제가 있었다.
 * tickSafely() 가 이런 좀비 채널을 joinTimeoutMs 경과 후 정리하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PlazaSessionManagerTest {

    @Mock
    private PlazaEventPublisherPort eventPublisher;

    @SuppressWarnings("unchecked")
    private Map<String, Object> channelsOf(PlazaSessionManager manager) {
        return (Map<String, Object>) ReflectionTestUtils.getField(manager, "channels");
    }

    private PlazaSessionManager newManager(long joinTimeoutMs) {
        PlazaProperties props = new PlazaProperties(1600, 1200, 30, 100, 12, 120, joinTimeoutMs);
        return new PlazaSessionManager(props, eventPublisher);
    }

    @Test
    @DisplayName("allocate() 만 되고 join() 이 안 온 채널은 타임아웃 경과 후 틱에서 제거된다")
    void removesChannelNeverJoinedAfterTimeout() throws InterruptedException {
        PlazaSessionManager manager = newManager(1L); // 1ms — 곧바로 타임아웃되도록
        PlazaJoinResponse res = manager.allocate();
        assertThat(channelsOf(manager)).containsKey(res.channelId());

        Thread.sleep(5); // joinTimeoutMs(1ms) 를 확실히 넘긴다

        manager.tickSafely();

        assertThat(channelsOf(manager)).doesNotContainKey(res.channelId());
    }

    @Test
    @DisplayName("타임아웃이 지나기 전에는 join() 을 기다리는 빈 채널을 지우지 않는다")
    void keepsFreshEmptyChannelBeforeTimeout() {
        PlazaSessionManager manager = newManager(30_000L); // 기본값 수준 — 곧바로는 안 지워짐
        PlazaJoinResponse res = manager.allocate();

        manager.tickSafely();

        assertThat(channelsOf(manager)).containsKey(res.channelId());
    }

    @Test
    @DisplayName("플레이어가 입장한 채널은 오래됐어도 지우지 않는다")
    void keepsChannelWithPlayerEvenAfterTimeout() throws InterruptedException {
        PlazaSessionManager manager = newManager(1L); // 1ms
        PlazaJoinResponse res = manager.allocate();
        manager.join(res.channelId(), "session-1", 1L, "public-1", "닉네임", null);

        Thread.sleep(5);

        manager.tickSafely();

        assertThat(channelsOf(manager)).containsKey(res.channelId());
    }
}

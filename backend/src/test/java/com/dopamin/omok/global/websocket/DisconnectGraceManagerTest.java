package com.dopamin.omok.global.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DisconnectGraceManagerTest {

    private static final String ROOM_CODE = "ROOM000001";
    private static final String OTHER_ROOM_CODE = "ROOM000002";
    private static final Long USER_ID = 1L;

    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ScheduledExecutorService scheduler;

    private DisconnectGraceManager manager;

    @BeforeEach
    void setUp() {
        manager = new DisconnectGraceManager(transactionTemplate, scheduler);
    }

    @Test
    @DisplayName("한 판에서 세 번째 연결 끊김은 유예 없이 즉시 처리한다")
    void thirdDisconnectInSameGameRunsImmediately() {
        Runnable graceAction = mock(Runnable.class);
        stubScheduledFutures(2);
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());

        scheduleAndReconnect(graceAction);
        scheduleAndReconnect(graceAction);
        manager.scheduleGrace(ROOM_CODE, USER_ID, graceAction);

        verify(scheduler, times(2))
                .schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
        verify(graceAction).run();
    }

    @Test
    @DisplayName("새 판 시작 시 방 상태를 지우면 첫 연결 끊김에 다시 유예를 부여한다")
    void clearRoomResetsDisconnectCountForNextGame() {
        Runnable graceAction = mock(Runnable.class);
        stubScheduledFutures(3);

        scheduleAndReconnect(graceAction);
        scheduleAndReconnect(graceAction);

        manager.clearRoom(ROOM_CODE);
        manager.scheduleGrace(ROOM_CODE, USER_ID, graceAction);

        verify(scheduler, times(3))
                .schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
        verify(graceAction, never()).run();
    }

    @Test
    @DisplayName("방 상태를 지우면 그 방의 모든 타이머만 취소한다")
    void clearRoomCancelsOnlyTimersBelongingToRoom() {
        Runnable graceAction = mock(Runnable.class);
        ScheduledFuture<?> firstPlayerTimer = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondPlayerTimer = mock(ScheduledFuture.class);
        ScheduledFuture<?> otherRoomTimer = mock(ScheduledFuture.class);
        stubScheduledFutures(firstPlayerTimer, secondPlayerTimer, otherRoomTimer);

        manager.scheduleGrace(ROOM_CODE, 1L, graceAction);
        manager.scheduleGrace(ROOM_CODE, 2L, graceAction);
        manager.scheduleGrace(OTHER_ROOM_CODE, 3L, graceAction);

        manager.clearRoom(ROOM_CODE);

        verify(firstPlayerTimer).cancel(false);
        verify(secondPlayerTimer).cancel(false);
        verify(otherRoomTimer, never()).cancel(false);
    }

    @Test
    @DisplayName("Spring 빈 종료 시 유예 작업용 스케줄러도 종료한다")
    void shutdownStopsScheduler() {
        manager.shutdown();

        verify(scheduler).shutdownNow();
    }

    private void scheduleAndReconnect(Runnable graceAction) {
        manager.scheduleGrace(ROOM_CODE, USER_ID, graceAction);
        manager.cancelGrace(ROOM_CODE, USER_ID);
    }

    private void stubScheduledFutures(int count) {
        ScheduledFuture<?>[] futures = new ScheduledFuture<?>[count];
        for (int i = 0; i < count; i++) {
            futures[i] = mock(ScheduledFuture.class);
        }
        stubScheduledFutures(futures);
    }

    private void stubScheduledFutures(ScheduledFuture<?>... futures) {
        Deque<ScheduledFuture<?>> results = new ArrayDeque<>();
        for (ScheduledFuture<?> future : futures) {
            results.addLast(future);
        }
        doAnswer(invocation -> results.removeFirst())
                .when(scheduler)
                .schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
    }
}

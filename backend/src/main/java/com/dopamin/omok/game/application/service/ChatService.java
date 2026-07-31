package com.dopamin.omok.game.application.service;

import com.dopamin.omok.game.application.dto.ChatMessageResponse;
import com.dopamin.omok.game.application.port.in.SendChatMessageUseCase;
import com.dopamin.omok.game.application.port.out.LoadGamePlayerPort;
import com.dopamin.omok.game.application.port.out.LoadRoomPort;
import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService implements SendChatMessageUseCase {

    private final LoadRoomPort loadRoomPort;
    private final LoadGamePlayerPort loadGamePlayerPort;

    @Override
    public ChatMessageResponse sendChatMessage(String roomCode, Long userId, String senderNickname, String content) {
        Room room = loadRoomPort.findByRoomCode(roomCode)
                .orElseThrow(() -> new OmokException(ErrorCode.ROOM_NOT_FOUND));

        // 방에 들어와 있지 않은 사용자는 채팅을 보낼 수 없다 — roomCode만 알면
        // 아무나 토픽에 메시지를 밀어넣을 수 있으므로 여기서 반드시 막는다.
        GamePlayer sender = loadGamePlayerPort.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new OmokException(ErrorCode.NOT_IN_ROOM));

        // 관전자는 흑/백이 없으므로 색을 비운다. 프론트는 이 값으로 말풍선 색을 정한다.
        return new ChatMessageResponse(
                senderNickname,
                sender.isSpectator() ? null : sender.getColor(),
                sender.isSpectator(),
                content,
                LocalDateTime.now()
        );
    }
}

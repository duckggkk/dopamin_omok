package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.adapter.in.web.dto.GameMoveRequest;
import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameRoomResponse;
import com.dopamin.omok.game.application.port.in.*;
import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.global.security.jwt.JwtProvider;
import com.dopamin.omok.global.security.userdetails.CustomUserDetailsService;
import com.dopamin.omok.support.GameFixture;
import com.dopamin.omok.support.TestSecurityConfig;
import com.dopamin.omok.support.UserFixture;
import com.dopamin.omok.support.WithCustomUser;
import com.dopamin.omok.user.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
@Import(TestSecurityConfig.class)
class GameControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean CreateGameRoomUseCase createGameRoomUseCase;
    @MockitoBean JoinGameRoomUseCase joinGameRoomUseCase;
    @MockitoBean PlaceStoneUseCase placeStoneUseCase;
    @MockitoBean SurrenderUseCase surrenderUseCase;
    @MockitoBean GetGameUseCase getGameUseCase;
    @MockitoBean GetGameMovesUseCase getGameMovesUseCase;
    @MockitoBean GetWaitingGamesUseCase getWaitingGamesUseCase;
    @MockitoBean GetMyGamesUseCase getMyGamesUseCase;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("게임 방 생성 성공 - 201")
    @WithCustomUser(id = 1L)
    void createRoom_success() throws Exception {
        User black = UserFixture.create(1L);
        GameRoomResponse response = GameRoomResponse.from(GameFixture.createWaiting(black));
        given(createGameRoomUseCase.createRoom(1L)).willReturn(response);

        mockMvc.perform(post("/games/rooms"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("WAITING"));
    }

    @Test
    @DisplayName("게임 방 참가 성공 - 200")
    @WithCustomUser(id = 2L)
    void joinRoom_success() throws Exception {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        GameRoomResponse response = GameRoomResponse.from(GameFixture.createInProgress(black, white));
        given(joinGameRoomUseCase.joinRoom("ROOM01", 2L)).willReturn(response);

        mockMvc.perform(post("/games/rooms/{roomCode}/join", "ROOM01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("대기 중인 게임 목록 조회")
    void getWaitingRooms_success() throws Exception {
        User black = UserFixture.create(1L);
        GameRoomResponse response = GameRoomResponse.from(GameFixture.createWaiting(black));
        given(getWaitingGamesUseCase.getWaitingGames(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/games/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("WAITING"));
    }

    @Test
    @DisplayName("게임 상세 조회")
    void getGame_success() throws Exception {
        User black = UserFixture.create(1L);
        GameRoomResponse response = GameRoomResponse.from(GameFixture.createWaiting(black));
        given(getGameUseCase.getGame("ROOM01")).willReturn(response);

        mockMvc.perform(get("/games/{roomCode}", "ROOM01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomCode").value("ROOM1"));
    }

    @Test
    @DisplayName("게임 이동 목록 조회")
    void getGameMoves_success() throws Exception {
        given(getGameMovesUseCase.getGameMoves("ROOM01")).willReturn(List.of());

        mockMvc.perform(get("/games/{roomCode}/moves", "ROOM01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("돌 놓기 성공 - 201")
    @WithCustomUser(id = 1L)
    void placeStone_success() throws Exception {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        GameMoveResponse response = GameMoveResponse.from(
                GameFixture.createMove(GameFixture.createInProgress(black, white), black, StoneColor.BLACK, 7, 7, 1));
        given(placeStoneUseCase.placeStone(eq("ROOM01"), eq(1L), eq(7), eq(7))).willReturn(response);

        mockMvc.perform(post("/games/{roomCode}/moves", "ROOM01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("row", 7, "col", 7))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.row").value(7))
                .andExpect(jsonPath("$.data.col").value(7));
    }

    @Test
    @DisplayName("돌 놓기 - 범위 초과 시 400")
    @WithCustomUser(id = 1L)
    void placeStone_outOfRange_returns400() throws Exception {
        mockMvc.perform(post("/games/{roomCode}/moves", "ROOM01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("row", 15, "col", 7))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기권 성공 - 200")
    @WithCustomUser(id = 1L)
    void surrender_success() throws Exception {
        User black = UserFixture.create(1L);
        User white = UserFixture.create(2L);
        GameRoomResponse response = GameRoomResponse.from(GameFixture.createInProgress(black, white));
        given(surrenderUseCase.surrender("ROOM01", 1L)).willReturn(response);

        mockMvc.perform(post("/games/{roomCode}/surrender", "ROOM01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("내 게임 목록 조회 성공 - 200")
    @WithCustomUser(id = 1L)
    void getMyGames_success() throws Exception {
        given(getMyGamesUseCase.getMyGames(eq(1L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/games/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }
}

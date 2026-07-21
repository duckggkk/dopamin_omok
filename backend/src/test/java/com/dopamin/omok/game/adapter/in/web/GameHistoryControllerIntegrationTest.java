package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.application.dto.GameSummaryResponse;
import com.dopamin.omok.game.application.port.in.GetGameMovesUseCase;
import com.dopamin.omok.game.application.port.in.GetMyGamesUseCase;
import com.dopamin.omok.game.application.port.in.GetPhysicalReplayUseCase;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.global.security.jwt.JwtAuthenticator;
import com.dopamin.omok.global.security.principal.AuthUser;
import com.dopamin.omok.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameHistoryController.class)
class GameHistoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMyGamesUseCase getMyGamesUseCase;

    @MockitoBean
    private GetGameMovesUseCase getGameMovesUseCase;

    @MockitoBean
    private GetPhysicalReplayUseCase getPhysicalReplayUseCase;

    @MockitoBean
    private JwtAuthenticator jwtAuthenticator;

    @Test
    void getPublicGamesReturnsSummaryPageAndUsesDefaultPageable() throws Exception {
        UUID targetPublicId = UUID.randomUUID();
        AuthUser viewer = authUser(7L);
        UUID blackId = UUID.randomUUID();
        UUID whiteId = UUID.randomUUID();
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 20, 14, 0);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 7, 20, 14, 15);
        GameSummaryResponse summary = new GameSummaryResponse(
                31L,
                2,
                GameStatus.FINISHED,
                GameType.CLASSIC,
                true,
                new GameSummaryResponse.PlayerSummary(blackId, "black", "/black.png"),
                new GameSummaryResponse.PlayerSummary(whiteId, "white", null),
                new GameSummaryResponse.PlayerSummary(blackId, "black", "/black.png"),
                startedAt,
                finishedAt
        );
        Pageable expectedPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        given(getMyGamesUseCase.getPublicGames(targetPublicId, viewer.id(), expectedPageable))
                .willReturn(new PageImpl<>(List.of(summary), expectedPageable, 1));

        mockMvc.perform(get("/users/{publicId}/games", targetPublicId)
                        .with(authentication(tokenFor(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(31))
                .andExpect(jsonPath("$.data.content[0].gameNumber").value(2))
                .andExpect(jsonPath("$.data.content[0].status").value("FINISHED"))
                .andExpect(jsonPath("$.data.content[0].gameType").value("CLASSIC"))
                .andExpect(jsonPath("$.data.content[0].ranked").value(true))
                .andExpect(jsonPath("$.data.content[0].blackPlayer.id").value(blackId.toString()))
                .andExpect(jsonPath("$.data.content[0].blackPlayer.nickname").value("black"))
                .andExpect(jsonPath("$.data.content[0].whitePlayer.id").value(whiteId.toString()))
                .andExpect(jsonPath("$.data.content[0].winner.id").value(blackId.toString()))
                .andExpect(jsonPath("$.data.content[0].startedAt").value("2026-07-20T14:00:00"))
                .andExpect(jsonPath("$.data.content[0].finishedAt").value("2026-07-20T14:15:00"))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        then(getMyGamesUseCase).should().getPublicGames(targetPublicId, viewer.id(), expectedPageable);
    }

    @Test
    void getPublicGamesBindsCustomPaginationAndSort() throws Exception {
        reset(getMyGamesUseCase);
        UUID targetPublicId = UUID.randomUUID();
        AuthUser viewer = authUser(11L);
        Pageable expectedPageable = PageRequest.of(2, 3, Sort.by(Sort.Direction.ASC, "finishedAt"));
        given(getMyGamesUseCase.getPublicGames(targetPublicId, viewer.id(), expectedPageable))
                .willReturn(new PageImpl<>(List.of(), expectedPageable, 6));

        mockMvc.perform(get("/users/{publicId}/games", targetPublicId)
                        .param("page", "2")
                        .param("size", "3")
                        .param("sort", "finishedAt,asc")
                        .with(authentication(tokenFor(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.size").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(6));

        then(getMyGamesUseCase).should().getPublicGames(targetPublicId, viewer.id(), expectedPageable);
    }

    @Test
    void getPublicGamesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/users/{publicId}/games", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken tokenFor(AuthUser authUser) {
        return UsernamePasswordAuthenticationToken.authenticated(
                authUser,
                null,
                authUser.getAuthorities()
        );
    }

    private AuthUser authUser(Long id) {
        return new AuthUser(id, UUID.randomUUID(), "viewer@example.com", "viewer", UserRole.USER);
    }
}

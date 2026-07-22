package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.application.dto.GameSummaryResponse;
import com.dopamin.omok.game.application.port.in.GetGameMovesUseCase;
import com.dopamin.omok.game.application.port.in.GetMyGamesUseCase;
import com.dopamin.omok.game.application.port.in.GetPhysicalReplayUseCase;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.principal.AuthUser;
import com.dopamin.omok.user.domain.UserRole;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

//Mockito기반 테스트 선언
@ExtendWith(MockitoExtension.class)
class GameHistoryControllerTest {

    @Mock
    private GetMyGamesUseCase getMyGamesUseCase;

    @Mock
    private GetGameMovesUseCase getGameMovesUseCase;

    @Mock
    private GetPhysicalReplayUseCase getPhysicalReplayUseCase;

    @Test
    @DisplayName("공개 전적 조회: 받은 페이징 조건을 그대로 위임하고 결과를 감싸서 반환한다")
    void getPublicGamesDelegatesRequestValuesAndWrapsPage() {
        GameHistoryController controller = new GameHistoryController(
                getMyGamesUseCase,
                getGameMovesUseCase,
                getPhysicalReplayUseCase
        );
        UUID targetPublicId = UUID.randomUUID();
        AuthUser viewer = new AuthUser(7L, UUID.randomUUID(), "viewer@example.com", "viewer", UserRole.USER);
        PageRequest pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "finishedAt"));
        Page<GameSummaryResponse> expected = new PageImpl<>(List.of(), pageable, 0);
        given(getMyGamesUseCase.getPublicGames(targetPublicId, viewer.id(), pageable)).willReturn(expected);

        ResponseEntity<ApiResponse<Page<GameSummaryResponse>>> result =
                controller.getPublicGames(viewer, targetPublicId, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isSameAs(expected);
        then(getMyGamesUseCase).should().getPublicGames(targetPublicId, viewer.id(), pageable);
    }
}

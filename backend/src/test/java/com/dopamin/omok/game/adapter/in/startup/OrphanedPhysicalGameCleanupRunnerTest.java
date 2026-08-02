package com.dopamin.omok.game.adapter.in.startup;

import com.dopamin.omok.game.application.port.in.CleanupOrphanedPhysicalGamesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrphanedPhysicalGameCleanupRunnerTest {

    @Test
    void runsCleanupDuringApplicationStartup() throws Exception {
        CleanupOrphanedPhysicalGamesUseCase useCase = mock(CleanupOrphanedPhysicalGamesUseCase.class);
        OrphanedPhysicalGameCleanupRunner runner = new OrphanedPhysicalGameCleanupRunner(useCase);

        runner.run(new DefaultApplicationArguments());

        verify(useCase).cleanupOrphanedPhysicalGames();
    }
}

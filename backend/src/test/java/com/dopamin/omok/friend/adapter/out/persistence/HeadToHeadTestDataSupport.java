package com.dopamin.omok.friend.adapter.out.persistence;

import com.dopamin.omok.friend.application.dto.HeadToHead;
import com.dopamin.omok.friend.domain.Friendship;
import com.dopamin.omok.game.domain.ByoyomiOption;
import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.OmokRule;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.TimeLimit;
import com.dopamin.omok.user.domain.User;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.ArrayList;
import java.util.List;

final class HeadToHeadTestDataSupport {

    private final TestEntityManager entityManager;
    private int userSeq = 1;
    private int roomSeq = 1;

    HeadToHeadTestDataSupport(TestEntityManager entityManager) {
        this.entityManager = entityManager;
    }

    User user(String prefix) {
        int seq = userSeq++;
        User user = User.createLocalUser(prefix + seq + "@test.local", "password", prefix + seq);
        user.verifyEmail();
        entityManager.persist(user);
        return user;
    }

    List<FriendCase> acceptedFriendsWithGames(User me, int count, int wins, int losses, int draws) {
        List<FriendCase> friends = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            friends.add(acceptedFriendWithGames(me, wins, losses, draws));
        }
        return friends;
    }

    FriendCase acceptedFriendWithGames(User me, int wins, int losses, int draws) {
        User friend = user("friend");
        Friendship friendship = Friendship.request(me, friend);
        friendship.accept();
        entityManager.persist(friendship);

        for (int i = 0; i < wins; i++) {
            finishedGame(me, friend, me, i);
        }
        for (int i = 0; i < losses; i++) {
            finishedGame(me, friend, friend, i);
        }
        for (int i = 0; i < draws; i++) {
            drawGame(me, friend, i);
        }
        return new FriendCase(friend, new HeadToHead(wins, losses, draws));
    }

    void unrelatedFinishedGame(User winner, User loser) {
        finishedGame(winner, loser, winner, roomSeq);
    }

    private void finishedGame(User me, User friend, User winner, int offset) {
        Game game = game(me, friend, offset);
        game.finish(winner);
        entityManager.persist(game);
    }

    private void drawGame(User me, User friend, int offset) {
        Game game = game(me, friend, offset);
        game.draw();
        entityManager.persist(game);
    }

    private Game game(User me, User friend, int offset) {
        Room room = Room.create(
                me,
                "R" + String.format("%09d", roomSeq++),
                GameType.CLASSIC,
                OmokRule.FREESTYLE,
                TimeLimit.UNLIMITED,
                ByoyomiOption.NONE,
                true);
        room.startGame();
        entityManager.persist(room);

        boolean swap = offset % 2 == 1;
        return Game.start(room, swap ? friend : me, swap ? me : friend);
    }

    record FriendCase(User user, HeadToHead expected) {
    }
}

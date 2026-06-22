package com.dopamin.omok.friend.adapter.out.persistence;

import com.dopamin.omok.friend.application.port.out.DeleteFriendshipPort;
import com.dopamin.omok.friend.application.port.out.LoadFriendshipPort;
import com.dopamin.omok.friend.application.port.out.SaveFriendshipPort;
import com.dopamin.omok.friend.domain.Friendship;
import com.dopamin.omok.friend.domain.FriendshipStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FriendshipPersistenceAdapter
        implements SaveFriendshipPort, LoadFriendshipPort, DeleteFriendshipPort {

    private final FriendshipJpaRepository repository;

    @Override
    public Friendship save(Friendship friendship) {
        return repository.save(friendship);
    }

    @Override
    public Optional<Friendship> findBetween(Long userIdA, Long userIdB) {
        return repository.findBetween(userIdA, userIdB);
    }

    @Override
    public List<Friendship> findAcceptedOf(Long userId) {
        return repository.findInvolving(userId, FriendshipStatus.ACCEPTED);
    }

    @Override
    public List<Friendship> findIncomingPendingOf(Long userId) {
        return repository.findByAddressee(userId, FriendshipStatus.PENDING);
    }

    @Override
    public void delete(Friendship friendship) {
        repository.delete(friendship);
    }
}

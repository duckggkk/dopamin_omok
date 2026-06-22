package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 방 검색 동적 쿼리 프래그먼트(QueryDSL 구현). */
public interface RoomRepositoryCustom {
    Page<Room> search(RoomSearchCondition condition, Pageable pageable);
}

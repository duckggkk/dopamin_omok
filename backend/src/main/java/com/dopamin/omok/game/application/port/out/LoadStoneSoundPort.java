package com.dopamin.omok.game.application.port.out;

import java.util.Optional;

/**
 * 특정 사용자가 장착한 착수음(STONE_SOUND)의 assetKey 를 조회한다.
 * 착수 브로드캐스트에 "둔 사람의 착수음"을 실어, 상대/관전자도 같은 소리를 듣게 하기 위함.
 */
public interface LoadStoneSoundPort {
    Optional<String> findEquippedStoneSoundKey(Long userId);
}

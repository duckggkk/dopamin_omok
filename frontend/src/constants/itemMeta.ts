import { ItemType } from '@/types';

// 타입별 표시 메타 (아이콘/카테고리 라벨). 새 코스메틱 타입 추가 시 여기 한 줄만 추가.
// 상점과 프로필의 '내 아이템'(종류별 묶음)이 같은 라벨을 공유한다.
export const ITEM_TYPE_META: Record<ItemType, { icon: string; label: string }> = {
  DEFEAT_MESSAGE: { icon: '💬', label: '패배 문구' },
  DEFEAT_EFFECT: { icon: '🔥', label: '승패 이펙트' },
  BOARD_SKIN: { icon: '🎨', label: '바둑판 스킨' },
  STONE_SOUND: { icon: '🔊', label: '착수음' },
  STONE_SKIN: { icon: '⚫', label: '바둑알 스킨' },
  STONE_EFFECT: { icon: '✨', label: '착수 효과' },
  CHARACTER_SKIN: { icon: '🧙', label: '피지컬 캐릭터' },
};

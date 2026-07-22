import styles from '@/pages/GamePage.module.css';

// 이펙트 키(승자가 장착한 1개) → 화면 연출 클래스.
// 같은 테마를 승자는 '승리 연출', 패자는 '패배 연출'로 렌더한다(세트). 백엔드 화이트리스트와 합의.
export const WIN_EFFECT_CLASS: Record<string, string> = {
  flame: styles.fxWinFlame,
  shatter: styles.fxWinShatter,
  storm: styles.fxWinStorm,
  tears: styles.fxWinTears,
};
export const LOSS_EFFECT_CLASS: Record<string, string> = {
  flame: styles.fxFlame,
  shatter: styles.fxShatter,
  storm: styles.fxStorm,
  tears: styles.fxTears,
};

/** 승리 이펙트 키 → CSS 클래스(없으면 null). 다른 화면(AI전 등)에서 같은 연출을 재사용한다. */
export const winEffectClass = (effect: string | null | undefined): string | null =>
  effect ? WIN_EFFECT_CLASS[effect] ?? null : null;

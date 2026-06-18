import type { CSSProperties } from 'react';
import type { StoneStyle } from '@/types';

// 기본 바둑알 스타일 — 스킨 미장착(기본 스킨) 시 폴백. OmokBoard 폴백과 동일.
export const DEFAULT_BLACK_STONE: StoneStyle = { fill: '#1a1a1a', stroke: '#000000', shine: '#666666' };
export const DEFAULT_WHITE_STONE: StoneStyle = { fill: '#f5f5f0', stroke: '#bbbbbb', shine: '#ffffff' };

export const areSameStoneSkin = (
  left: StoneStyle | null | undefined,
  right: StoneStyle | null | undefined,
) => !!left && !!right
  && left.fill === right.fill
  && left.stroke === right.stroke
  && left.shine === right.shine;

export const stonePreviewStyle = (skin: StoneStyle | null | undefined): CSSProperties | undefined => {
  if (!skin) return undefined;
  return {
    background: `radial-gradient(circle at 35% 30%, ${skin.shine}, ${skin.fill} 68%)`,
    border: `1px solid ${skin.stroke}`,
    boxShadow: 'inset 0 1px 2px rgba(255,255,255,0.2), 0 2px 5px rgba(0,0,0,0.55)',
  };
};

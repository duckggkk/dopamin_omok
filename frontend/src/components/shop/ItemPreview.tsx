import { useId } from 'react';
import { ShopItem, StoneStyle, SkinColors, SkinFilter } from '@/types';
import { useProtectedAsset } from '@/hooks/useProtectedAsset';
import styles from './ItemPreview.module.css';

// 기본 바둑판/바둑알 — OmokBoard 폴백과 동일 (미리보기 대비용)
const DEFAULT_COLORS: SkinColors = { bg: '#dcb95b', lines: '#8b6914', dots: '#8b6914' };
const DEFAULT_FILTER: SkinFilter = { type: 'fractalNoise', freqX: 0.65, freqY: 0.06, octaves: 4, seed: 3, blend: 'overlay' };
const BLACK_STONE: StoneStyle = { fill: '#1a1a1a', stroke: '#000000', shine: '#777777' };
const WHITE_STONE: StoneStyle = { fill: '#f5f5f0', stroke: '#bbbbbb', shine: '#ffffff' };

// face 키워드 → 이모지 (PhysicalGamePage 와 동일 매핑)
const FACE_EMOJI: Record<string, string> = { robot: '🤖', rabbit: '🐰', ghost: '👻', cat: '🐱', fox: '🦊', bear: '🐻' };

// 패배 이펙트 키 → 아이콘 + 타일 연출 클래스 (백엔드 화이트리스트와 합의)
const DEFEAT_FX: Record<string, { icon: string; cls: string }> = {
  flame: { icon: '🔥', cls: styles.fxFlame },
  shatter: { icon: '💥', cls: styles.fxShatter },
  storm: { icon: '⛈️', cls: styles.fxStorm },
  tears: { icon: '😭', cls: styles.fxTears },
};

// 미리보기 3x3 바둑판 좌표계
const N = 3;
const CELL = 26;
const PAD = 16;
const PX = PAD * 2 + CELL * (N - 1); // 84

interface Stone { r: number; c: number; style: StoneStyle; pop?: boolean }

interface MiniBoardProps {
  colors?: SkinColors;
  filter?: SkinFilter | null;
  imageUrl?: string | null;
  stones?: Stone[];
}

const MiniBoard = ({ colors = DEFAULT_COLORS, filter = DEFAULT_FILTER, imageUrl = null, stones = [] }: MiniBoardProps) => {
  const uid = useId().replace(/:/g, '');
  const x = (c: number) => PAD + c * CELL;
  const y = (r: number) => PAD + r * CELL;
  const radius = CELL / 2 - 2;

  return (
    <svg width={PX} height={PX} viewBox={`0 0 ${PX} ${PX}`} className={styles.boardSvg} aria-hidden="true">
      <defs>
        {filter && (
          <filter id={`tex-${uid}`} x="-2%" y="-2%" width="104%" height="104%" colorInterpolationFilters="sRGB">
            <feTurbulence type={filter.type as 'fractalNoise' | 'turbulence'}
              baseFrequency={`${filter.freqX} ${filter.freqY}`} numOctaves={filter.octaves} seed={filter.seed} result="noise" />
            <feColorMatrix type="saturate" values="0" in="noise" result="grain" />
            <feBlend in="SourceGraphic" in2="grain" mode={filter.blend as 'overlay'} />
          </filter>
        )}
      </defs>

      {imageUrl ? (
        <image href={imageUrl} width={PX} height={PX} preserveAspectRatio="xMidYMid slice" />
      ) : (
        <rect width={PX} height={PX} fill={colors.bg} rx={4} filter={filter ? `url(#tex-${uid})` : undefined} />
      )}

      {Array.from({ length: N }, (_, i) => (
        <g key={i} stroke={colors.lines} strokeWidth={1}>
          <line x1={x(i)} y1={PAD} x2={x(i)} y2={PAD + (N - 1) * CELL} />
          <line x1={PAD} y1={y(i)} x2={PAD + (N - 1) * CELL} y2={y(i)} />
        </g>
      ))}
      <circle cx={x(1)} cy={y(1)} r={2.2} fill={colors.dots} />

      {stones.map((s, i) => (
        <g key={i} className={s.pop ? styles.popStone : undefined}>
          <circle cx={x(s.c)} cy={y(s.r)} r={radius} fill={s.style.fill} stroke={s.style.stroke} strokeWidth={1}
            style={{ filter: 'drop-shadow(0.5px 1px 1.5px rgba(0,0,0,0.45))' }} />
          <circle cx={x(s.c) - radius * 0.32} cy={y(s.r) - radius * 0.34} r={radius * 0.42} fill={s.style.shine} opacity={0.55} />
        </g>
      ))}
    </svg>
  );
};

/** 바둑판 한 칸 위에 바둑알을 '크게 1개' 올려 보여주는 미리보기 (착수효과·뽑기상자용). */
const LargeStonePreview = ({ style, pop = false }: { style: StoneStyle; pop?: boolean }) => {
  const r = 27;
  const cx = PX / 2;
  return (
    <svg width={PX} height={PX} viewBox={`0 0 ${PX} ${PX}`} className={styles.boardSvg} aria-hidden="true">
      <rect width={PX} height={PX} rx={8} fill={DEFAULT_COLORS.bg} />
      <line x1={cx} y1={6} x2={cx} y2={PX - 6} stroke={DEFAULT_COLORS.lines} strokeWidth={1} />
      <line x1={6} y1={cx} x2={PX - 6} y2={cx} stroke={DEFAULT_COLORS.lines} strokeWidth={1} />
      <g className={pop ? styles.popStone : undefined}>
        <circle cx={cx} cy={cx} r={r} fill={style.fill} stroke={style.stroke} strokeWidth={1.5}
          style={{ filter: 'drop-shadow(1px 2px 3px rgba(0,0,0,0.5))' }} />
        <circle cx={cx - r * 0.32} cy={cx - r * 0.34} r={r * 0.42} fill={style.shine} opacity={0.55} />
      </g>
    </svg>
  );
};

interface Props {
  item: ShopItem;
  /** 뽑기 상자 안 미리보기 — 바둑알 스킨은 크게 1개, 바둑판 스킨은 돌 1개만 둔다. */
  gacha?: boolean;
}

/** 상점/인벤토리에서 아이템(스킨·효과·착수음·캐릭터·문구)을 3x3 바둑판 등으로 미리 보여준다. */
const ItemPreview = ({ item, gacha = false }: Props) => {
  const cfg = item.itemConfig ?? null;
  const boardImg = useProtectedAsset('BOARD_SKIN', item.itemType === 'BOARD_SKIN' ? cfg?.assetKey ?? null : null);

  switch (item.itemType) {
    case 'STONE_SKIN': {
      const skin = cfg?.stone ?? BLACK_STONE;
      // 뽑기 상자: 바둑알을 크게 1개만 둬 스킨 자체가 도드라지게 한다.
      if (gacha) {
        return <LargeStonePreview style={skin} />;
      }
      return (
        <MiniBoard stones={[
          { r: 0, c: 0, style: skin }, { r: 1, c: 1, style: skin }, { r: 2, c: 2, style: skin },
          { r: 0, c: 2, style: WHITE_STONE }, { r: 2, c: 0, style: WHITE_STONE },
        ]} />
      );
    }
    case 'STONE_EFFECT':
      // 착수 효과는 한 돌의 등장 모션 — 바둑알 1개를 크게, 반복 재생해 보여준다.
      // 효과가 없는 '일반 착수'(기본)는 모션 없이 정적으로 보여준다.
      return <LargeStonePreview style={BLACK_STONE} pop={!!cfg?.effect} />;
    case 'BOARD_SKIN':
      return (
        <MiniBoard
          colors={cfg?.colors ?? DEFAULT_COLORS}
          filter={cfg?.assetKey ? cfg?.filter ?? null : cfg?.filter ?? DEFAULT_FILTER}
          imageUrl={boardImg}
          // 뽑기 상자: 바둑판 스킨이 주인공이므로 돌은 1개만(중앙) 둬 배경이 잘 보이게 한다.
          stones={gacha
            ? [{ r: 1, c: 1, style: BLACK_STONE }]
            : [{ r: 0, c: 1, style: BLACK_STONE }, { r: 1, c: 1, style: WHITE_STONE }, { r: 2, c: 1, style: BLACK_STONE }]}
        />
      );
    case 'STONE_SOUND':
      // 소리 재생은 클릭→미리보기 바둑판의 '착수음 듣기'에서. 여기선 정적 표시만.
      return (
        <div className={styles.soundWrap}>
          <MiniBoard stones={[{ r: 1, c: 1, style: BLACK_STONE }]} />
          <span className={styles.soundIcon}>🔊</span>
        </div>
      );
    case 'CHARACTER_SKIN': {
      const ch = cfg?.character;
      const body = ch?.body ?? '#7fb3d5';
      const accent = ch?.accent ?? '#2c3e50';
      const face = ch ? FACE_EMOJI[ch.face] ?? '🙂' : '🙂';
      return (
        <svg width={PX} height={PX} viewBox={`0 0 ${PX} ${PX}`} className={styles.boardSvg} aria-hidden="true">
          <rect width={PX} height={PX} rx={10} fill="#7fb069" />
          <ellipse cx={PX / 2} cy={PX - 14} rx={18} ry={5} fill="rgba(0,0,0,0.18)" />
          <rect x={PX / 2 - 16} y={26} width={32} height={34} rx={11} fill={body} stroke={accent} strokeWidth={2.5} />
          <text x={PX / 2} y={46} textAnchor="middle" dominantBaseline="central" fontSize="20">{face}</text>
        </svg>
      );
    }
    case 'DEFEAT_MESSAGE':
      return (
        <div className={styles.defeatPreview}>
          <span className={styles.defeatText}>{item.displayName || item.name}</span>
        </div>
      );
    case 'DEFEAT_EFFECT': {
      const fx = cfg?.effect ? DEFEAT_FX[cfg.effect] : undefined;
      return (
        <div className={`${styles.defeatEffectPreview} ${fx?.cls ?? ''}`}>
          <span className={styles.defeatEffectIcon}>{fx?.icon ?? '🔥'}</span>
          <span className={styles.defeatEffectName}>{item.displayName || item.name}</span>
        </div>
      );
    }
    default:
      return null;
  }
};

export default ItemPreview;

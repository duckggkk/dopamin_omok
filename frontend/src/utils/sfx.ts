/**
 * 피지컬 오목용 경량 효과음. 에셋 파일/백엔드 없이 Web Audio API 로 즉석 합성한다.
 * 착수음(STONE_SOUND)은 기존 useStoneSoundPlayer(에셋 기반)를 쓰고, 여기선 착수 폴백·아이템 SFX만 담당한다.
 */
export type SfxName =
  | 'place'        // 착수(장착 착수음 없을 때 폴백)
  | 'pickup'       // 아이템 획득
  | 'use_speed'    // 이동 부스트
  | 'use_crater'   // 바둑판 붕괴
  | 'use_bomb'     // 광역 폭탄
  | 'use_remove'   // 상대 돌 제거
  | 'score';       // 오목 완성 득점(라인 제거)

type AudioCtx = AudioContext;

let ctx: AudioCtx | null = null;

const getCtx = (): AudioCtx | null => {
  try {
    if (!ctx) {
      const Ctor = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) return null;
      ctx = new Ctor();
    }
    if (ctx.state === 'suspended') ctx.resume().catch(() => {});
    return ctx;
  } catch {
    return null;
  }
};

const tone = (
  freq: number,
  dur: number,
  type: OscillatorType = 'sine',
  gain = 0.18,
  slideTo?: number,
) => {
  const c = getCtx();
  if (!c) return;
  const t0 = c.currentTime;
  const osc = c.createOscillator();
  const g = c.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t0);
  if (slideTo) osc.frequency.exponentialRampToValueAtTime(Math.max(1, slideTo), t0 + dur);
  g.gain.setValueAtTime(gain, t0);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  osc.connect(g).connect(c.destination);
  osc.start(t0);
  osc.stop(t0 + dur);
};

const noiseBurst = (dur: number, gain = 0.3, cutoff = 1400) => {
  const c = getCtx();
  if (!c) return;
  const t0 = c.currentTime;
  const buffer = c.createBuffer(1, Math.ceil(c.sampleRate * dur), c.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < data.length; i++) {
    data[i] = (Math.random() * 2 - 1) * (1 - i / data.length); // 감쇠 노이즈
  }
  const src = c.createBufferSource();
  src.buffer = buffer;
  const lp = c.createBiquadFilter();
  lp.type = 'lowpass';
  lp.frequency.setValueAtTime(cutoff, t0);
  const g = c.createGain();
  g.gain.setValueAtTime(gain, t0);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  src.connect(lp).connect(g).connect(c.destination);
  src.start(t0);
};

export const playSfx = (name: SfxName): void => {
  switch (name) {
    case 'place':
      tone(680, 0.06, 'triangle', 0.16);
      break;
    case 'pickup':
      tone(880, 0.08, 'sine', 0.18);
      window.setTimeout(() => tone(1320, 0.1, 'sine', 0.16), 55);
      break;
    case 'use_speed':
      tone(420, 0.25, 'sawtooth', 0.13, 1200);
      break;
    case 'use_crater':
      tone(200, 0.32, 'sine', 0.22, 60);
      noiseBurst(0.18, 0.12, 800);
      break;
    case 'use_bomb':
      noiseBurst(0.42, 0.32, 1600);
      tone(90, 0.42, 'sine', 0.2, 40);
      break;
    case 'use_remove':
      tone(1000, 0.18, 'square', 0.14, 220);
      break;
    case 'score':
      // 상승 아르페지오 — 득점의 쾌감(완성·소멸을 알리는 팡파르)
      tone(660, 0.12, 'triangle', 0.18);
      window.setTimeout(() => tone(880, 0.12, 'triangle', 0.18), 90);
      window.setTimeout(() => tone(1320, 0.2, 'triangle', 0.2), 190);
      noiseBurst(0.22, 0.1, 2200);
      break;
  }
};

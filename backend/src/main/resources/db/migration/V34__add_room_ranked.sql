-- 방에 랭크/캐주얼 구분을 추가한다.
-- ranked=true  : 랭크전(회원 전용) — 레이팅·전적에 반영
-- ranked=false : 캐주얼(일반) — 비회원도 참가, 레이팅·전적 미반영(대국 기록은 저장)
-- 기존 방/과거 기록은 모두 랭크전으로 간주(레이팅을 적용해 오던 동작 보존)하도록 기본값 TRUE.
ALTER TABLE rooms ADD COLUMN ranked BOOLEAN NOT NULL DEFAULT TRUE;

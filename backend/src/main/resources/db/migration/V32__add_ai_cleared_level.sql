-- 싱글플레이(AI 대국) 사다리 진척: 클리어한 최고 단계(0 = 아직 1단계도 못 깸).
-- 9단계 사다리에서 다음 단계 해제 조건(clearedLevel+1)을 서버가 계정에 영구 저장한다.
-- 로컬스토리지가 아닌 서버 저장이라 기기를 바꿔도 진행도가 계정에 따라온다.
ALTER TABLE users ADD COLUMN ai_cleared_level INT NOT NULL DEFAULT 0;

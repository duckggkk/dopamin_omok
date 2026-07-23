-- 회원 탈퇴를 '소프트 삭제(익명화)' 로 구현하기 위한 컬럼 추가.
--
-- 왜 하드 삭제(DELETE FROM users)를 쓰지 않는가:
--   1) 탈퇴 회원의 전적·레이팅은 '상대방 대국 기록'의 일부다. 행을 지우면 games 의
--      플레이어 칸이 NULL 이 되어(SET NULL, V1·V38) 상대 화면에서 누구와 뒀는지,
--      상대 전적이 얼마였는지가 사라진다. 행을 남기고 익명화하면 '탈퇴한사용자_<id>'로
--      계속 표시되고 전적 집계도 그대로 유지된다.
--   2) 익명화된 닉네임이 UNIQUE 자원을 계속 점유해야 다른 사람이 그 이름으로 가입해
--      과거 기보의 탈퇴자를 사칭하는 것을 막을 수 있다(행이 없으면 불가능).
-- (한때 rooms.host_id 의 ON DELETE CASCADE 가 방장 삭제 시 상대방의 대국까지 연쇄
--  삭제하는 문제도 있었으나, 이는 V38 에서 SET NULL 로 별도 해결했다.)
--
-- 그래서 users 행은 남기고 개인정보만 파기(익명화)한다.
--   email    → deleted_<publicId>@deleted.local  (원래 이메일 소멸 = 같은 이메일로 재가입 가능)
--   nickname → 탈퇴한사용자_<id>                  (UNIQUE 라 id 로 유일성 보장)
--   password / profile_image_url / provider_id → NULL
-- deleted_at 이 채워진 행은 로그인·랭킹·친구 등 모든 '활성 사용자' 조회에서 제외된다.

ALTER TABLE users ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at;

-- 활성 사용자 조회(로그인·랭킹)가 항상 deleted_at IS NULL 을 함께 보므로 인덱스를 둔다.
CREATE INDEX idx_users_deleted_at ON users (deleted_at);

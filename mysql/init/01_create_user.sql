-- Docker 전용 앱 계정 생성 (root 대신 사용)
--
-- ⚠️ 아래 비밀번호는 "로컬 개발 컨테이너 전용" 고정값이다. 이 스크립트는 docker-compose.dev.yml
--    의 MySQL 초기화에만 쓰이며, 외부에 포트를 열지 않는 로컬 네트워크 안에서만 동작한다.
--    운영(prod)은 이 파일을 사용하지 않고 .env.prod 의 DB_USERNAME/DB_PASSWORD 를 주입받는다.
CREATE USER IF NOT EXISTS 'omok_user'@'%' IDENTIFIED BY 'omok_pass';
GRANT ALL PRIVILEGES ON dopamin_omok.* TO 'omok_user'@'%';
FLUSH PRIVILEGES;

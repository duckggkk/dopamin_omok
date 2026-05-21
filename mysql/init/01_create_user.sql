-- Docker 전용 앱 계정 생성 (root 대신 사용)
CREATE USER IF NOT EXISTS 'omok_user'@'%' IDENTIFIED BY 'omok_pass';
GRANT ALL PRIVILEGES ON dopamin_omok.* TO 'omok_user'@'%';
FLUSH PRIVILEGES;

-- 개발 DB 전용 시드. 운영 DB 에는 적용하지 않는다.
-- 테스트 계정 user01 / admin01, 비밀번호 Passw0rd! (BCrypt 해시만 저장)
insert into user_roles(role_id, role_name) values
  ('ROLE_USER', '일반회원'),
  ('ROLE_ADMIN', '관리자')
on conflict (role_id) do nothing;

insert into tb_users(user_id, passwd, user_name, gender, birth, email, phone, zip_code, addr, addr_dtl, role_id) values
  ('user01', '$2a$10$tcv4Iy06xDC6eHrt9g3v2OaYe1uKAcT1ZnC1OrhpFLuDhYX/mV5fe', '홍길동', 'M', '900101', 'user01@example.com', '010-1234-5678', '04524',
   '서울특별시 중구 세종대로 110', '101호', 'ROLE_USER'),
  ('admin01', '$2a$10$tcv4Iy06xDC6eHrt9g3v2OaYe1uKAcT1ZnC1OrhpFLuDhYX/mV5fe', '관리자', 'M', '900101', 'admin01@example.com', '010-1234-5678', '04524',
   '서울특별시 중구 세종대로 110', '101호', 'ROLE_ADMIN')
on conflict (user_id) do nothing;

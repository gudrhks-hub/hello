-- local/test 전용 시드. 비밀번호(평문)는 Passw0rd! (BCrypt 해시만 저장)
insert into user_roles(role_id, role_name) values ('ROLE_USER', '일반회원');
insert into user_roles(role_id, role_name) values ('ROLE_ADMIN', '관리자');

insert into tb_users(user_id, passwd, user_name, gender, birth, email, phone, zip_code, addr, addr_dtl, role_id, create_at)
values ('user01', '$2a$10$tcv4Iy06xDC6eHrt9g3v2OaYe1uKAcT1ZnC1OrhpFLuDhYX/mV5fe', '홍길동', 'M', '900101', 'user01@example.com', '010-1234-5678', '04524',
        '서울특별시 중구 세종대로 110', '101호', 'ROLE_USER', '2026-10-28 10:00:00');
insert into tb_users(user_id, passwd, user_name, gender, birth, email, phone, zip_code, addr, addr_dtl, role_id, create_at)
values ('admin01', '$2a$10$tcv4Iy06xDC6eHrt9g3v2OaYe1uKAcT1ZnC1OrhpFLuDhYX/mV5fe', '관리자', 'M', '900101', 'admin01@example.com', '010-1234-5678', '04524',
        '서울특별시 중구 세종대로 110', '101호', 'ROLE_ADMIN', '2026-10-28 10:00:00');

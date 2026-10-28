-- local/test (H2, MySQL 모드) 전용. 운영은 db/mysql/schema-mysql.sql 을 수동 적용한다.
-- tb_users, user_roles 는 사용자 제공 원본 DDL(ddl-original.sql) 그대로이다.
drop table if exists tb_refresh_tokens;
drop table if exists tb_users;
drop table if exists user_roles;

create table tb_users(
 user_id    varchar(50)   not null, /*필수 값 지정 */
 passwd     varchar(200)  not null,
 user_name  varchar(100)  not null,
 gender     varchar(10)   not null,
 birth      char(6)       not null,
 email      varchar(100)  not null,
 phone      varchar(15)   not null,
 addr       varchar(200)  not null,
 zip_code   varchar(20)   not null,
 addr_dtl   varchar(200)  default '',
 role_id varchar(30),
 create_at  timestamp     default current_timestamp ,
 update_at  timestamp     default null,
 primary key(user_id)
);

create table user_roles(
   role_id   varchar(30)  not null,
   role_name varchar(50)  not null,
   create_at  timestamp  default current_timestamp ,
   update_at  timestamp  default null,
   primary key(role_id)
);

create table tb_refresh_tokens(
  token_hash  char(64)     not null,
  user_id     varchar(50)  not null,
  expires_at  timestamp    not null,
  create_at   timestamp    default current_timestamp,
  primary key(token_hash)
);
create index idx_refresh_tokens_user_id on tb_refresh_tokens(user_id);

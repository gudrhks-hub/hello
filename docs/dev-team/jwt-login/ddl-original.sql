-- 사용자가 제공한 원본 DDL (컬럼, 타입, 제약을 변경하지 않는다)

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

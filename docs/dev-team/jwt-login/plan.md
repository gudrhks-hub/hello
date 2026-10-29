# JWT 로그인 기능 - 계획서 (plan.md)

작업 슬러그: `jwt-login`
작성: PM(pm-lead), 2026-10-28

## 1. 사용자 요청 요약

- 로그인 기능을 프런트(`frontend/`)와 백엔드(`backend/`)에서 동시에 개발
- 인증 방식: JWT
- JWT 클레임에 사용자 아이디, 이름, 권한 포함
- 액세스 토큰 유효시간 3시간
- 토큰 갱신(refresh) 기능 제공
- 프런트: zustand로 인증 상태 저장/처리
- 백엔드: 최신 JWT 라이브러리 사용
- **로그인 필요**: 사용자 정보, 장바구니, 구매조회, 구매. **그 외 전부 공개**

## 2. 기능 목록 (In scope)

| # | 기능 | 백엔드 | 프런트 |
|---|------|--------|--------|
| F1 | 로그인 (아이디/비밀번호) | `POST /api/auth/login` | 로그인 화면, zustand 저장 |
| F2 | 토큰 갱신 | `POST /api/auth/refresh` (HttpOnly 쿠키) | axios 인터셉터 자동 갱신, 앱 시작 시 세션 복구 |
| F3 | 로그아웃 | `POST /api/auth/logout` | 헤더 로그아웃 버튼, 상태 초기화 |
| F4 | 내 정보 조회 (사용자 정보) | `GET /api/users/me` (보호) | `/mypage` (보호 라우트) |
| F5 | 장바구니 보호 경로 (placeholder) | `GET /api/cart` (보호, 빈 목록) | `/cart` (보호 라우트, 빈 상태 표시) |
| F6 | 구매조회 보호 경로 (placeholder) | `GET /api/orders` (보호, 빈 목록) | `/orders` (보호 라우트, 빈 상태 표시) |
| F7 | 구매 보호 경로 (placeholder) | `POST /api/orders` (보호, 501 응답) | `/checkout` (보호 라우트, "준비 중" 안내) |
| F8 | 공통 에러 포맷, 인증/인가 실패 응답 | `@RestControllerAdvice`, `AuthenticationEntryPoint`, `AccessDeniedHandler` | 에러 메시지 표시 |
| F9 | 테스트용 시드 데이터 | 역할 2건, 사용자 2건 (local/test 프로필) | MSW mock (계약 예시 값) |

## 3. 범위 밖 (Out of scope)

- **회원가입**: tb_users 필수 컬럼이 10개(성별, 생년월일, 주소 등)라 별도 기능으로 분리. 로그인 테스트는 시드 계정으로 수행. (다음 단계 제안)
- 장바구니/구매/구매조회의 **실제 비즈니스 로직**과 관련 테이블. 이번에는 "로그인 필요 경로가 실제로 보호되는지" 확인하는 placeholder만 만든다.
- 비밀번호 찾기/변경, 아이디 찾기, 회원정보 수정, 회원탈퇴
- 소셜 로그인, 2단계 인증, 로그인 실패 횟수 잠금
- 관리자(ADMIN) 전용 화면/API (권한 값은 토큰에 담고 프런트 store에 보관만 함)
- 리프레시 토큰 재사용 탐지(reuse detection), 기기별 세션 관리 화면
- 운영 배포 설정(Docker, CI)

## 3.1 원본 DDL 위치

- 사용자 제공 원본 DDL(`tb_users`, `user_roles`): **`docs/dev-team/jwt-login/ddl-original.sql`** (컬럼/타입/제약 변경 금지, D1)
- 백엔드 반영본: `backend/src/main/resources/schema.sql`(local 프로필/test, H2), `backend/src/main/resources/db/postgresql/schema.sql`(PostgreSQL 수동 적용, 2026-10-29 MySQL에서 전환). 두 파일의 `tb_users`/`user_roles`는 원본과 동일해야 한다. 원본이 바뀌면 이 세 파일을 함께 갱신한다.

## 4. 기술 스택 (기존 코드 없음 -> 아래로 확정)

### 백엔드 (`backend/`)
- Java 21 (환경에 설치된 JDK 21 사용)
- Spring Boot **3.5.x 최신 패치** (3.5.14 이상, Maven Central 확인됨)
- Gradle (Groovy DSL, `build.gradle`), Gradle Wrapper 포함 (설치된 Gradle 8.14.3으로 `gradle wrapper` 생성)
- Spring Web, Spring Security, Validation
- **MyBatis** (`mybatis-spring-boot-starter` 3.0.5) - 사용자 DDL 우선(snake_case 테이블) 구조라 매퍼 XML 방식 채택
- JWT: **jjwt 0.13.0** (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) - 2026-10 기준 최신. 0.12+ 신규 API(`Jwts.builder().subject()`, `Jwts.parser().verifyWith(key).build().parseSignedClaims()`, `Keys.hmacShaKeyFor`) 사용. deprecated API(`setSubject`, `parseClaimsJws`, `signWith(alg, key)`) 사용 금지
- DB: 운영 **MySQL 8** (`prod` 프로필), 로컬 실행/테스트 **H2 (MySQL 모드)** (`local` 기본 프로필, 테스트)
- 테스트: JUnit 5, Mockito, Spring Security Test, MockMvc

### 프런트 (`frontend/`)
- Node 22, Vite 8, React 19, TypeScript
- React Router (`react-router-dom` 7)
- 상태 관리: **zustand 5**
- HTTP: axios 1.x (인터셉터로 토큰 첨부/자동 갱신)
- 폼: React Hook Form + Zod
- Mock: MSW 2.x (계약 예시 값 기반, 개발 시 선택적으로 활성화)
- 테스트: Vitest + Testing Library

## 5. 결정 사항 (근거 포함)

| # | 결정 | 근거 |
|---|------|------|
| D1 | 원본 DDL 컬럼/타입은 **변경하지 않는다**. `schema.sql`에 원본 그대로 넣는다 | 사용자가 개선안을 확정하지 않음 |
| D2 | FK(`tb_users.role_id -> user_roles.role_id`), email UNIQUE는 **추가하지 않는다** | 사용자 미확정. 비파괴 보완도 데이터 정합성에 영향을 주므로 사용자 확인 후 별도 반영 |
| D3 | 역할 시드 데이터: `ROLE_USER`(일반회원), `ROLE_ADMIN`(관리자) | Spring Security 관례. DDL 변경 아님(데이터만). 앞서 제안된 접두사 방식과 일치 |
| D4 | 권한 매핑: `role_id`가 `ROLE_`로 시작하지 않으면 접두사를 붙여 `GrantedAuthority` 생성. `role_id`가 NULL이면 `ROLE_USER`로 간주 | 원본 DDL에서 `role_id`가 NULL 허용이므로 방어 필요. 기존 데이터에 접두사 없는 값이 있어도 동작 |
| D5 | 액세스 토큰: JWT, HS256, 유효 **3시간(10800초)**. 클레임 `sub`=userId, `name`=userName, `role`=roleId(위 D4 정규화 값), `iss`, `iat`, `exp` | 사용자 요구. `role`은 DDL상 사용자당 1개이므로 단일 문자열 |
| D6 | 리프레시 토큰: **불투명(opaque) 랜덤 문자열**(32바이트, Base64URL), 유효 **14일**. DB에는 **SHA-256 해시만** 저장(`tb_refresh_tokens`). 갱신 시 **회전(rotation)**: 기존 행 삭제 + 새 토큰 발급 | 서버 측 폐기(로그아웃) 가능, DB 유출 시에도 원문 노출 없음. JWT로 만들 이점 없음 |
| D7 | 리프레시 토큰 전달: **HttpOnly 쿠키** `refresh_token`, `Path=/api/auth`, `SameSite=Strict`, `Secure`는 설정값(local=false, prod=true), `Max-Age=1209600` | XSS로 탈취 불가. SameSite=Strict로 CSRF 완화. 응답 바디에는 리프레시 토큰을 넣지 않음 |
| D8 | 액세스 토큰 전달: 응답 바디 → 프런트 zustand **메모리 상태**에만 저장(localStorage/sessionStorage persist 하지 않음). 요청 시 `Authorization: Bearer <token>` | XSS 노출 최소화. 새로고침 시에는 앱 시작 때 `/api/auth/refresh`로 복구 |
| D9 | 프런트-백 통신: Vite dev server 프록시 `/api` → `VITE_API_PROXY_TARGET`(기본 `http://localhost:8080`). 브라우저 입장에서 **동일 출처** | 쿠키 SameSite=Strict와 충돌 없음, CORS 불필요 |
| D10 | CORS: 백엔드에 설정값 `app.cors.allowed-origins`(기본 `http://localhost:5173`), `allowCredentials=true` 로 구성만 해 둔다 | 프록시 미사용 환경 대비 |
| D11 | 세션 미사용(`SessionCreationPolicy.STATELESS`), CSRF 비활성화, formLogin/httpBasic 비활성화 | JWT Bearer 기반 API. 쿠키 사용 엔드포인트(refresh/logout)는 SameSite=Strict로 보호 |
| D12 | 보호 경로: `/api/users/**`, `/api/cart/**`, `/api/orders/**` 는 `authenticated()`. 그 외 `permitAll()` | 사용자 요구(사용자 정보/장바구니/구매조회/구매) |
| D13 | 동시 갱신: 프런트는 탭 내 **single-flight**(진행 중인 refresh Promise 공유). 탭 간 경쟁으로 회전된 토큰이 무효화되면 해당 탭은 로그아웃 처리 | 과설계 방지. 탭 간 동기화는 범위 밖 |
| D14 | 회원가입 제외, 시드 계정으로 테스트 (`local`/test 프로필에서만 시드 로드) | 요청 범위가 "로그인". 운영 DB에 테스트 계정이 들어가지 않게 함 |
| D15 | 날짜/시간: ISO-8601 오프셋 포함 문자열(`2026-10-28T15:00:00+09:00`), 타임존 `Asia/Seoul` | 프런트 파싱 일관성 |
| D16 | JSON 필드는 camelCase. DB 컬럼(snake_case)은 MyBatis `map-underscore-to-camel-case: true` | 일반 관례 |
| D17 | 로그인 실패는 아이디 없음/비밀번호 불일치 구분 없이 동일 메시지(401 `INVALID_CREDENTIALS`) | 계정 존재 여부 노출 방지 |
| D18 | `POST /api/orders`(구매)는 이번 범위에서 **501 `NOT_IMPLEMENTED`** 반환 | 보호 여부 검증용 placeholder. 실제 구현은 후속 |
| D19 | Spring Boot 4.x 대신 3.5.x 채택 | 병렬 개발 안정성(자료/호환성). 4.x 이전은 후속 과제로 기록 |
| D20 | `gender` 값은 DDL상 자유 문자열. 시드는 `M`/`F` 사용, API는 저장값 그대로 전달 | DDL에 제약 없음 |
| D21 | 지원하지 않는 HTTP 메서드는 `405 METHOD_NOT_ALLOWED`(공통 에러 포맷)로 응답. 계약 0.5 표에 추가 | 미처리 시 500 `INTERNAL_ERROR`로 떨어져 원인 파악이 어렵다. 프런트 영향 없음(검수 시 승인) |
| D22 | 지원하지 않는 Content-Type, 쿼리 파라미터 오류는 415가 아닌 `400 VALIDATION_ERROR` | 계약의 "JSON 파싱 실패 = 400"과 같은 범주. 에러 코드 종류를 늘리지 않음(검수 시 승인) |
| D23 | JWT 서명 알고리즘 HS256 고정(`signWith(key, Jwts.SIG.HS256)`), 검증 시 HS256 외 거부 | jjwt는 키 길이에 따라 HS384/HS512를 자동 선택하므로 계약 헤더(`alg: HS256`)와 어긋날 수 있음 |
| D24 | MSW mock 모드에서는 **전체 새로고침 시 세션이 유지되지 않는다**(로그인 후 주소창으로 보호 경로를 직접 열면 `/login`으로 이동). 정상 동작으로 간주 | mock의 로그인 상태(`sessionActive`)는 페이지 메모리 모듈 변수라 새로고침 시 초기화된다. HttpOnly 쿠키는 mock으로 재현 불가. 새로고침 세션 복구는 실제 백엔드(쿠키 `Path=/api/auth`)로만 검증한다 |

## 6. 신규 테이블 (리프레시 토큰 저장)

```sql
create table tb_refresh_tokens(
  token_hash  char(64)     not null,   -- SHA-256 hex (소문자)
  user_id     varchar(50)  not null,
  expires_at  timestamp    not null,
  create_at   timestamp    default current_timestamp,
  primary key(token_hash)
);
create index idx_refresh_tokens_user_id on tb_refresh_tokens(user_id);
```

## 7. 시드 데이터 (local/test 전용)

| user_id | 비밀번호(평문, 문서 전용) | user_name | role_id |
|---------|---------------------------|-----------|---------|
| `user01` | `Passw0rd!` | 홍길동 | `ROLE_USER` |
| `admin01` | `Passw0rd!` | 관리자 | `ROLE_ADMIN` |

- DB에는 BCrypt 해시만 저장. 해시 값은 백엔드 개발자가 생성해 `data.sql`에 넣는다.
- 나머지 필수 컬럼 예시: gender `M`, birth `900101`, email `user01@example.com`, phone `010-1234-5678`, addr `서울특별시 중구 세종대로 110`, zip_code `04524`, addr_dtl `101호`.

## 8. 실행 구성

- 백엔드: `http://localhost:8080`, 기본 프로필 `local`(H2 메모리, MySQL 모드)
- 프런트: `http://localhost:5173`, `/api` 프록시 → 백엔드
- 환경 변수: `JWT_SECRET`(Base64, 디코딩 후 32바이트 이상), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`(prod), `VITE_API_PROXY_TARGET`(프런트)

## 9. 사용자 확인이 필요한 사항 (기본값으로 진행 중)

- DB 종류: MySQL 8 가정 (DDL 문법 기준)
- ORM: MyBatis 가정
- FK/email UNIQUE 추가 여부 (현재 미적용)
- 리프레시 토큰 유효기간 14일 적정 여부
- 회원가입을 다음 작업으로 진행할지

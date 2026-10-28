# 백엔드 작업 지시서 (backend-task.md)

담당: `backend-dev`
먼저 읽을 문서: `plan.md`(특히 5. 결정 사항), `api-contract.md`(유일한 기준)
수정 범위: `/home/user/backend/` 만. `frontend/`는 건드리지 않는다.

## 1. 프로젝트 골격

- 경로: `/home/user/backend`
- Gradle(Groovy DSL) + Wrapper (`gradle wrapper --gradle-version 8.14.3` 로 생성)
- Java 21 toolchain, Spring Boot 3.5.x 최신 패치(3.5.14 이상)
- group `com.shop`, 루트 패키지 `com.shop.api`, 메인 클래스 `com.shop.api.ShopApiApplication`
- 의존성
  - `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-validation`
  - `org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.5`
  - `io.jsonwebtoken:jjwt-api:0.13.0`, `runtimeOnly jjwt-impl:0.13.0`, `runtimeOnly jjwt-jackson:0.13.0`
  - `runtimeOnly com.h2database:h2`, `runtimeOnly com.mysql:mysql-connector-j`
  - 테스트: `spring-boot-starter-test`, `spring-security-test`, `org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.5`
  - Lombok 사용은 자유(쓰면 compileOnly + annotationProcessor 설정)

### 권장 패키지 구조
```
com.shop.api
 ├─ ShopApiApplication
 ├─ config        SecurityConfig, CorsConfig(또는 SecurityConfig 내부), JwtProperties, CookieProperties, JacksonConfig(필요 시)
 ├─ security      JwtTokenProvider, JwtAuthenticationFilter, RestAuthenticationEntryPoint, RestAccessDeniedHandler, AuthPrincipal, RoleNormalizer
 ├─ auth          AuthController, AuthService, RefreshTokenService, RefreshTokenCookieManager, dto/(LoginRequest, TokenResponse, AuthUser)
 ├─ user          UserController, UserService, UserMapper(+XML), dto/(UserMeResponse), domain/(User)
 ├─ cart          CartController (placeholder)
 ├─ order         OrderController (placeholder)
 └─ common        error/(ErrorCode, ErrorResponse, FieldErrorDetail, BusinessException, GlobalExceptionHandler)
resources
 ├─ application.yml, application-local.yml, application-prod.yml
 ├─ schema.sql (local/test), data.sql (local/test)
 ├─ db/mysql/schema-mysql.sql (운영 수동 적용용 원본 DDL + tb_refresh_tokens)
 └─ mapper/*.xml
```

## 2. 체크리스트

### 2.1 설정
- [ ] `application.yml`: 기본 활성 프로필 `local`, `server.port: 8080`, `spring.jackson.time-zone: Asia/Seoul`, `mybatis.configuration.map-underscore-to-camel-case: true`, `mybatis.mapper-locations: classpath:mapper/*.xml`
- [ ] `app.jwt.*`, `app.cookie.secure`, `app.cors.allowed-origins` 설정 키를 `api-contract.md` 3장대로 정의. `@ConfigurationProperties`로 바인딩
- [ ] `application-local.yml`: H2 메모리 DB `jdbc:h2:mem:shop;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`, `spring.sql.init.mode: always`(schema.sql, data.sql), 개발용 JWT 키는 `${JWT_SECRET:...}` 기본값으로(디코딩 32바이트 이상 Base64)
- [ ] `application-prod.yml`: MySQL `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`, `${JWT_SECRET}`(기본값 없음), `spring.sql.init.mode: never`, `app.cookie.secure: true`
- [ ] 테스트는 local과 같은 H2 설정으로 동작(`src/test/resources/application-test.yml` 또는 local 재사용)
- [ ] 존재하지 않는 경로가 `404 NOT_FOUND` 공통 에러 포맷으로 나가도록 처리(`NoResourceFoundException`/`NoHandlerFoundException` 처리, `/error` permitAll)

### 2.2 DB 스크립트
- [ ] `schema.sql`: `plan.md`의 원본 DDL(`tb_users`, `user_roles`) **컬럼/타입 그대로** + `tb_refresh_tokens`(plan.md 6장). H2 재실행 대비 `drop table if exists` 선행 허용. FK/UNIQUE 추가 금지(D2)
- [ ] `data.sql`: `user_roles`에 (`ROLE_USER`,`일반회원`), (`ROLE_ADMIN`,`관리자`). `tb_users`에 `user01`, `admin01` (plan.md 7장 값, 비밀번호 `Passw0rd!`의 **BCrypt 해시**를 직접 생성해 기입)
- [ ] `db/mysql/schema-mysql.sql`: 운영 적용용(원본 DDL + `tb_refresh_tokens`). 시드 미포함

### 2.3 보안/JWT
- [ ] `PasswordEncoder` 빈: `BCryptPasswordEncoder`
- [ ] `JwtTokenProvider` (jjwt 0.13.0 신규 API만 사용)
  - 키: `Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))`, 32바이트 미만이면 기동 실패
  - 발급: `Jwts.builder().issuer().subject(userId).claim("name", userName).claim("role", role).issuedAt().expiration().signWith(key).compact()`
  - 검증: `Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token)`
  - `ExpiredJwtException` → `TOKEN_EXPIRED`, 그 외 `JwtException`/`IllegalArgumentException` → `INVALID_TOKEN` 으로 구분 가능한 결과 반환
- [ ] `RoleNormalizer`: null → `ROLE_USER`, `ROLE_` 접두사 없으면 붙임 (D4)
- [ ] `JwtAuthenticationFilter` (`OncePerRequestFilter`)
  - `Authorization: Bearer ` 헤더가 있으면 검증. 성공 시 principal(userId, userName, role) + authority로 `UsernamePasswordAuthenticationToken` 설정
  - 실패 시 SecurityContext 비우고 실패 원인을 request attribute에 저장 후 **체인 계속 진행**(공개 경로는 익명 통과)
  - `/api/auth/**` 요청은 토큰 검사를 건너뜀
  - 필터에서 DB 조회하지 않음(토큰 클레임만 사용)
- [ ] `RestAuthenticationEntryPoint`: request attribute의 원인에 따라 `TOKEN_EXPIRED`/`INVALID_TOKEN`, 없으면 `UNAUTHORIZED` 공통 에러 JSON으로 401
- [ ] `RestAccessDeniedHandler`: 403 `FORBIDDEN` 공통 에러 JSON
- [ ] `SecurityConfig`
  - csrf disable, formLogin/httpBasic/logout disable, session STATELESS
  - `/api/users/**`, `/api/cart/**`, `/api/orders/**` → `authenticated()`, 그 외 `permitAll()`
  - CORS: `app.cors.allowed-origins`, 메서드 GET/POST/PUT/PATCH/DELETE/OPTIONS, 헤더 `Authorization`,`Content-Type`, `allowCredentials(true)`
  - 필터 등록: `UsernamePasswordAuthenticationFilter` 앞

### 2.4 인증 API (`api-contract.md` A1~A3)
- [ ] `LoginRequest` 검증 메시지는 계약 문구 그대로
- [ ] `AuthService.login`: 사용자 조회 → `passwordEncoder.matches` → 실패 시 `INVALID_CREDENTIALS`(아이디 없음과 동일 응답, D17) → 액세스 토큰 + 리프레시 토큰 발급
- [ ] `RefreshTokenService`
  - 생성: `SecureRandom` 32바이트 → Base64URL(패딩 없음). DB에는 SHA-256 소문자 hex만 저장
  - 회전: 조회 → 만료 확인 → 기존 행 삭제 → 새 행 삽입. **하나의 트랜잭션**(`@Transactional`)
  - 삭제 행 수가 0이면(동시 회전 경쟁) `REFRESH_TOKEN_INVALID`
  - 로그인 시 해당 사용자의 만료된 행 정리(`delete ... where user_id = ? and expires_at < now`)
- [ ] 쿠키: `ResponseCookie`로 생성. 이름 `refresh_token`, `Path=/api/auth`, `HttpOnly`, `SameSite=Strict`, `Max-Age=1209600`, `Secure`=설정값. 삭제 시 `Max-Age=0`
- [ ] `/api/auth/refresh` 실패 시 401 응답에 쿠키 삭제 헤더 포함
- [ ] `/api/auth/logout`: 항상 204 + 쿠키 삭제
- [ ] 응답 `TokenResponse`/`AuthUser` 필드명·타입은 계약 0.6/0.7 그대로. `accessTokenExpiresAt`은 `OffsetDateTime`(+09:00)

### 2.5 보호 API
- [ ] `GET /api/users/me`: principal의 userId로 `tb_users LEFT JOIN user_roles` 조회 → `UserMeResponse`(passwd 제외). 사용자가 없으면 401 `INVALID_TOKEN`
- [ ] `GET /api/cart` → `{"items":[],"totalCount":0}`
- [ ] `GET /api/orders` → `{"items":[],"totalCount":0}`
- [ ] `POST /api/orders` → 501 `NOT_IMPLEMENTED` 공통 에러

### 2.6 공통 에러
- [ ] `ErrorCode` enum: 계약 0.5 표의 code/status/message 그대로
- [ ] `GlobalExceptionHandler`(`@RestControllerAdvice`): `MethodArgumentNotValidException`→400(+`errors`), `HttpMessageNotReadableException`→400, `BusinessException`→해당 코드, 404, 그 외 `Exception`→500(로그에는 스택, 응답에는 고정 메시지)
- [ ] 필터 단계(EntryPoint/AccessDeniedHandler)와 컨트롤러 단계가 **같은 JSON 형식**을 쓰도록 `ErrorResponse` 작성 로직 공유
- [ ] `timestamp`는 `+09:00` ISO-8601

### 2.7 보안 기본기
- [ ] 비밀번호·토큰·쿠키 값을 로그에 출력하지 않음
- [ ] MyBatis는 `#{}` 바인딩만 사용 (`${}` 금지)
- [ ] 비밀 키는 설정/환경 변수로만. prod 기본값 없음

### 2.8 테스트 (실제 실행)
- [ ] `JwtTokenProviderTest`: 발급 토큰의 `sub/name/role/iss`, `exp-iat=10800`, 만료 토큰 → TOKEN_EXPIRED, 변조 토큰 → INVALID_TOKEN
- [ ] `AuthServiceTest`(Mockito): 로그인 성공, 아이디 없음/비번 불일치 → INVALID_CREDENTIALS, role_id null → ROLE_USER
- [ ] `RefreshTokenServiceTest` 또는 통합 테스트: 회전 후 이전 토큰 재사용 시 401, 만료 토큰 401
- [ ] MockMvc 통합 테스트(`@SpringBootTest` + `@AutoConfigureMockMvc`, H2 시드 사용)
  - 로그인 200 + Set-Cookie 속성(HttpOnly, SameSite=Strict, Path=/api/auth) + 바디 필드
  - 로그인 400(필드 errors), 401 INVALID_CREDENTIALS
  - refresh 쿠키로 200 + 새 쿠키, 쿠키 없음 401 REFRESH_TOKEN_INVALID + 삭제 쿠키
  - logout 204 + 삭제 쿠키, 이후 같은 쿠키로 refresh 401
  - `/api/users/me` 토큰 없음 401 UNAUTHORIZED, 잘못된 토큰 401 INVALID_TOKEN, 정상 200(passwd 키 없음)
  - `/api/cart`, `/api/orders` 무토큰 401, 토큰 200, `POST /api/orders` 토큰 501
  - 공개 경로(예: 존재하지 않는 `/api/products`)는 잘못된 토큰이 있어도 401이 아니라 404
- [ ] `./gradlew test` 실행 결과를 보고서에 기록

## 3. 완료 기준

1. `cd /home/user/backend && ./gradlew test` 성공
2. `./gradlew bootRun` 후 아래가 동작
   - `curl -i -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"userId":"user01","password":"Passw0rd!"}'` → 200, `Set-Cookie: refresh_token=...`
   - 받은 토큰으로 `GET /api/users/me` → 200
3. `api-contract.md`의 모든 엔드포인트·상태 코드·에러 코드·필드명이 일치
4. 응답 어디에도 `passwd` 및 리프레시 토큰 원문(쿠키 제외)이 포함되지 않음
5. `/home/user/docs/dev-team/jwt-login/backend-report.md` 작성 (에이전트 정의의 보고 형식)

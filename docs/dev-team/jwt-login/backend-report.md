# backend-report

STATUS: DONE

CHANGED_FILES:
- /home/user/backend/ (신규 생성, frontend/ 및 기존 docs 미수정)
  - build.gradle, settings.gradle, .gitignore, gradlew, gradlew.bat, gradle/wrapper/*
  - src/main/java/com/shop/api/: ShopApiApplication; config(AppConfig, SecurityConfig, JwtProperties, CookieProperties, CorsProperties); security(JwtTokenProvider, JwtAuthenticationFilter, RestAuthenticationEntryPoint, RestAccessDeniedHandler, AuthPrincipal, RoleNormalizer); auth(AuthController, AuthService, RefreshTokenService, RefreshTokenCookieManager, RefreshTokenMapper, RefreshTokenRecord, dto/*); user(UserController, UserService, UserMapper, domain/User, dto/UserMeResponse); cart/CartController; order/OrderController; common/error(ErrorCode, ErrorResponse, FieldErrorDetail, BusinessException, ErrorResponseWriter, GlobalExceptionHandler)
  - src/main/resources/: application.yml, application-local.yml, application-prod.yml, schema.sql, data.sql, db/mysql/schema-mysql.sql, mapper/UserMapper.xml, mapper/RefreshTokenMapper.xml
  - src/test/: security/JwtTokenProviderTest (5), auth/AuthServiceTest (8), auth/AuthApiIntegrationTest (21, MockMvc + H2), resources/application-test.yml
- /home/user/docs/dev-team/jwt-login/backend-report.md

TEST_RESULT: `cd /home/user/backend && ./gradlew test` -> BUILD SUCCESSFUL. 34 tests, 0 failures, 0 errors, 0 skipped (JwtTokenProviderTest 5, AuthServiceTest 8, AuthApiIntegrationTest 21).
Maven Central returned HTTP 429 (rate limit) during the first dependency downloads; repeated runs succeeded, no code change involved.
Manual check with bootRun + curl: login 200 with Set-Cookie (HttpOnly; SameSite=Strict; Path=/api/auth; Max-Age=1209600), GET /api/users/me with the token 200 (no passwd), GET /api/orders without a token 401 UNAUTHORIZED. Server stopped afterwards.

RUN:
- Dev: `cd /home/user/backend && ./gradlew bootRun` (profile local, H2 memory, seed user01/admin01, password `Passw0rd!`, port 8080)
- Test: `./gradlew test`
- Prod: `SPRING_PROFILES_ACTIVE=prod` with env JWT_SECRET (Base64, 32+ bytes decoded), DB_URL, DB_USERNAME, DB_PASSWORD, CORS_ALLOWED_ORIGINS. Apply `src/main/resources/db/mysql/schema-mysql.sql` manually first (sql.init is never in prod).

CONTRACT_ISSUES:
- [해결됨] 원본 DDL: ddl-original.sql 수신 후 schema.sql, db/mysql/schema-mysql.sql 의 tb_users/user_roles 를 원본 그대로 교체(tb_refresh_tokens 유지). 시드는 새 길이 제약에 맞고 매퍼/DTO는 update_at 등 추가 컬럼과 무관(명시 컬럼 조회). 재실행 `./gradlew test`: 34건 통과, 실패 0.
- 계약에 없는 405 처리: 지원하지 않는 HTTP 메서드(예: GET /api/auth/login)는 500이 되지 않도록 405 + code `METHOD_NOT_ALLOWED` + 메시지 `허용되지 않는 요청 방식입니다.`(공통 포맷)로 응답한다. 계약 0.5 표에 행 추가 제안. 프런트 영향 없음.
- Content-Type 오류(415 해당)와 쿼리 파라미터 오류는 계약의 "JSON 파싱 실패"에 준해 400 VALIDATION_ERROR로 처리했다.
- 액세스 토큰 JWT 헤더는 계약대로 `{"alg":"HS256","typ":"JWT"}` (jjwt 기본은 키 길이에 따라 HS384 이상을 고르므로 `signWith(key, Jwts.SIG.HS256)`로 고정, 검증 시에도 HS256만 허용).
- 400 errors 배열 순서는 userId, password 순으로 고정(필드명 역순 정렬).

TODO:
- 회원가입, 실제 장바구니/구매 로직 (범위 밖)
- prod 프로필 실제 MySQL 연결 검증은 미실시 (MySQL 환경 없음). H2 MySQL 모드로만 검증

# 검수 결과 (review.md) - jwt-login

검수: PM(pm-lead), 2026-10-28
대상: `/home/user/backend`, `/home/user/frontend`
기준: `api-contract.md`, `backend-task.md`, `frontend-task.md`, `plan.md`, `ddl-original.sql`

## 판정: PASS

차단 수준 결함은 없다. 계약 보완 사항(405, 400 범위, HS256 고정)은 문서에 반영했다. 아래 "권장 개선"은 후속 작업에서 처리해도 된다.

## 1. 빌드/테스트 직접 실행 결과

| 대상 | 명령 | 결과 |
|------|------|------|
| 백엔드 | `./gradlew test` | 성공. 34건(AuthApiIntegrationTest 21, AuthServiceTest 8, JwtTokenProviderTest 5), 실패 0, 스킵 0 |
| 프런트 | `npm run build` (`tsc -b && vite build`) | 성공, 타입 에러 0 |
| 프런트 | `npm test` (vitest) | 3파일 15건 통과 |

- 오케스트레이터가 실제 연동 확인 중이어서 서버(8080/5173)는 띄우지 않았다. bootRun + curl 결과는 백엔드 보고를 따른다.
- 백엔드 통합 테스트는 `@SpringBootTest`(MOCK 웹 환경)라 포트를 쓰지 않는다.
- prod(MySQL 8) 프로필은 아무도 검증하지 못했다(미검증으로 남김).

## 2. 오케스트레이터 관찰 판정: mock 새로고침 시 `/login`으로 이동

**판정: mock의 한계이며 실제 결함은 아니다.**

근거(코드):
- `frontend/src/mocks/handlers.ts:7` `const state = { sessionActive: false, seq: 0 }`. MSW 2.x 브라우저 모드에서 핸들러는 서비스 워커가 아니라 **페이지 JS 컨텍스트**에서 실행된다. 이 모듈 변수는 페이지 메모리에만 있다.
- 주소창으로 `/checkout`을 직접 열면 페이지 전체가 새로 로드되어 `sessionActive`가 `false`로 초기화된다. 이후 `bootstrap()`(`authStore.ts:59`)의 `POST /api/auth/refresh`가 mock에서 401 `REFRESH_TOKEN_INVALID`(`handlers.ts:81`)를 받고 `clearAuth()`를 호출한다. 그래서 `ProtectedRoute.tsx:10`이 `/login?redirect=%2Fcheckout`으로 보낸다. 계약 2장 4항대로의 동작이다.
- 실제 백엔드에서는 로그인 응답의 `refresh_token` 쿠키(HttpOnly, `Path=/api/auth`, `SameSite=Strict`, `Max-Age=1209600`)가 브라우저에 남는다. 새로고침 후 `bootstrap`의 동일 출처 XHR(`/api/auth/refresh`, Vite 프록시 경유)에 이 쿠키가 실린다. 경로가 Path와 맞고 same-site 요청이므로 Strict라도 전송된다. 그 결과 200과 함께 세션이 복구된다. `ProtectedRoute`는 `idle/loading` 동안 로딩만 표시하고, StrictMode 이중 호출도 `bootstrapPromise` 가드로 refresh 1회만 보낸다.
- frontend-task 2.7은 "쿠키는 MSW에서 흉내 수준"으로 정해 두었다. 완료 기준 5(새로고침 유지)는 실제 백엔드 기준이다. 따라서 수정 대상이 아니다. plan.md D24로 기록했다.
- 실제 연동 확인 포인트: 로그인 후 DevTools에서 `refresh_token` 쿠키 존재 여부, 새로고침 시 `POST /api/auth/refresh` 요청에 Cookie 헤더가 실리는지, 응답 200과 새 Set-Cookie(회전)를 확인한다. 여기서 실패하면 그때는 실제 결함이다(의심 지점: 프록시 `changeOrigin`, 쿠키 `Secure` 설정값).

## 3. 계약 대조 (엔드포인트 단위)

| # | 항목 | 백엔드 | 프런트 | 결과 |
|---|------|--------|--------|------|
| A1 | `POST /api/auth/login` 200 + TokenResponse + Set-Cookie | `AuthController.login`, 쿠키 속성 일치, `expiresIn` 10800, `accessTokenExpiresAt` +09:00 | `loginApi`, store 저장 | 일치 |
| A1 | 400 VALIDATION_ERROR + 필드 메시지 | `LoginRequest` 메시지 문구 일치, errors 순서 userId->password | Zod 문구 동일, 서버 errors를 `setError` | 일치 |
| A1 | 401 INVALID_CREDENTIALS (아이디 없음/비번 불일치 동일) | dummy hash로 타이밍 차이까지 완화 | 폼 상단 `role="alert"` | 일치 |
| A2 | `POST /api/auth/refresh` 회전 + 401 시 삭제 쿠키 | `RefreshTokenService.rotate` 삭제 0건이면 401, `GlobalExceptionHandler`가 삭제 쿠키 부착 | single-flight, 재귀 방지(`/auth/` 제외) | 일치 |
| A3 | `POST /api/auth/logout` 항상 204 + 삭제 쿠키 | 일치 | 결과 무관 `clearAuth`, `/` 이동 | 일치 |
| U1 | `GET /api/users/me` 필드 12개, passwd 제외, 사용자 없음 401 INVALID_TOKEN | `UserMeResponse` 일치, null 키 유지 테스트 있음 | `UserMe` 타입 일치 | 일치 |
| C1/O1 | `{items:[], totalCount:0}` | 일치 | 빈 상태 문구 | 일치 |
| O2 | `POST /api/orders` 501 (비로그인은 401 우선) | 일치 | 서버 message 표시 | 일치 |
| 공통 | 에러 포맷 6필드, 필터 단계 동일 포맷 | `ErrorResponseWriter` 공유 | `toApiError` | 일치 |
| 공통 | 공개 경로 잘못된 토큰은 익명 처리 | 필터가 실패 원인만 attribute로 남김, 404 테스트 있음 | - | 일치 |
| 공통 | JWT 클레임 iss/sub/name/role/iat/exp, HS256 | 일치(HS256 고정) | 디코딩 안 함 | 일치 |

## 4. 계약 이슈 처리 (백엔드 CONTRACT_ISSUES)

| 이슈 | 결정 | 반영 |
|------|------|------|
| 405 `METHOD_NOT_ALLOWED` 추가 | 승인 | `api-contract.md` 0.5 표에 행 추가, plan.md D21 |
| 415/파라미터 오류를 400 `VALIDATION_ERROR`로 | 승인 | 0.5 표 400 행 설명 확장, plan.md D22 |
| HS256 고정 | 승인(계약 헤더와 일치시키는 올바른 조치) | 0.5 하단 명시, plan.md D23 |
| 원본 DDL 위치 | - | plan.md "3.1 원본 DDL 위치" 신설 |

프런트는 `ErrorCode` 유니언에 `METHOD_NOT_ALLOWED`가 없지만 `message`를 그대로 표시하므로 동작 영향이 없다(선택 반영).

## 5. 체크리스트/완료 기준

- 백엔드: 설정 키, H2/prod 프로필, schema.sql(원본 DDL 동일, FK/UNIQUE 미추가), data.sql(BCrypt), jjwt 0.13 신규 API만 사용, 필터/EntryPoint/AccessDeniedHandler, STATELESS/CSRF off, 보호 경로, 쿠키, 테스트 항목 전부 충족.
- 프런트: persist 미사용, Web Storage 미사용, 인터셉터 규약(첨부/401 갱신/single-flight/1회 재시도/실패 시 clearAuth), ProtectedRoute 로딩 처리, bootstrap 1회 가드, 오픈 리다이렉트 방지, 로그인 폼 접근성 속성, MSW, 테스트 항목 전부 충족.
- 완료 기준 중 실제 백엔드 연동(프런트 4, 5)은 오케스트레이터가 확인 중이다.

## 6. 보안 기본기

| 항목 | 결과 |
|------|------|
| 비밀번호 평문 저장/로그 | 없음. BCrypt, `LoginRequest.toString` 마스킹, `JwtProperties.toString` 마스킹 |
| SQL 인젝션 | 없음. 매퍼 XML 전부 `#{}` |
| 인가 누락 | 없음. `/api/users/**`, `/api/cart/**`, `/api/orders/**` authenticated, `/me`는 토큰 sub만 사용 |
| 민감 정보 응답 노출 | 없음. passwd 미포함, 리프레시 토큰은 쿠키로만, DB에는 SHA-256 해시 |
| 하드코딩 비밀 키 | local/test에만 개발용 기본 키(`${JWT_SECRET:...}`), prod는 기본값 없음(미설정 시 기동 실패). 허용 |
| 프런트 | 토큰 메모리 보관, `console.*` 출력 없음 |

## 7. 권장 개선 (비차단, 후속 처리 가능)

1. `backend/src/main/resources/db/mysql/schema-mysql.sql:35`: 테이블은 `if not exists`인데 `create index`는 재실행하면 중복 오류가 난다. 운영 수동 적용 스크립트이므로 재실행 시 주의하거나 별도 마이그레이션 도구 도입 시 정리한다.
2. `backend/.../GlobalExceptionHandler.java:71`: `HttpMediaTypeNotAcceptableException`(예: `Accept: application/xml`)이 전용 핸들러 없이 500으로 떨어질 수 있다. 실사용 영향은 낮다.
3. `frontend/src/mocks/handlers.ts:7`: mock 개발 편의를 위해 mock 전용 로그인 플래그를 `sessionStorage`에 두면 새로고침도 흉내 낼 수 있다. 다만 "Web Storage 비어 있음" 점검과 헷갈릴 수 있으므로 하지 않아도 된다(D24).
4. `frontend/src/pages/CheckoutPage.tsx:29`: 성공 메시지도 `ErrorMessage` 컴포넌트로 표시한다. 현재는 항상 501이라 영향이 없고, 구매 실구현 때 정리한다.
5. prod(MySQL 8) 프로필 실제 연결과 TIMESTAMP 타임존(세션 타임존과 `Asia/Seoul` LocalDateTime 저장) 확인은 배포 전 필요하다.

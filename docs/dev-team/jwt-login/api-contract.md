# API 계약서 - JWT 로그인 (api-contract.md)

이 문서가 백엔드와 프런트가 공유하는 **유일한 기준**입니다. 경로, 메서드, 필드명, 타입, 상태 코드, 에러 코드를 임의로 바꾸지 않습니다.

## 0. 공통 규칙

### 0.1 기본
- Base URL: `/api` (프런트는 상대 경로 `/api/...`로 호출, Vite 프록시가 `http://localhost:8080`으로 전달)
- 요청/응답 `Content-Type: application/json; charset=UTF-8` (본문 없는 응답 제외)
- JSON 키: camelCase
- 날짜/시간: ISO-8601 오프셋 포함 문자열, 타임존 `Asia/Seoul` 예) `"2026-10-28T15:00:00+09:00"`
- 값이 없는 필드는 `null`로 내려준다(키 생략 금지). 예외: 에러 응답의 `errors`는 항상 배열(비어 있을 수 있음)

### 0.2 인증 방식
| 항목 | 값 |
|------|----|
| 액세스 토큰 | JWT (HS256), 유효 10800초(3시간) |
| 액세스 토큰 전달 | 요청 헤더 `Authorization: Bearer <accessToken>` |
| 액세스 토큰 보관(프런트) | zustand 메모리 상태 (persist 금지) |
| 리프레시 토큰 | 불투명 랜덤 문자열, 유효 14일(1209600초), 사용 시마다 회전 |
| 리프레시 토큰 전달 | **HttpOnly 쿠키** (응답 바디에 포함하지 않음) |
| 쿠키 속성 | `refresh_token=<값>; Path=/api/auth; HttpOnly; SameSite=Strict; Max-Age=1209600` (+ `Secure`는 prod에서만) |
| 쿠키 삭제 | `refresh_token=; Path=/api/auth; HttpOnly; SameSite=Strict; Max-Age=0` |
| 프런트 요청 옵션 | `/api/auth/*` 호출 시 axios `withCredentials: true` (동일 출처라 기본 전송되지만 명시) |
| 세션 | 사용하지 않음 (STATELESS) |

### 0.3 JWT 액세스 토큰 클레임

헤더: `{"alg":"HS256","typ":"JWT"}`

페이로드 예시:
```json
{
  "iss": "shop-api",
  "sub": "user01",
  "name": "홍길동",
  "role": "ROLE_USER",
  "iat": 1761631200,
  "exp": 1761642000
}
```
| 클레임 | 타입 | 설명 |
|--------|------|------|
| `iss` | string | 고정 `"shop-api"` (설정 `app.jwt.issuer`) |
| `sub` | string | 사용자 아이디 (`tb_users.user_id`) |
| `name` | string | 사용자 이름 (`tb_users.user_name`) |
| `role` | string | 권한. `role_id`가 NULL이면 `"ROLE_USER"`, `ROLE_`로 시작하지 않으면 접두사를 붙인 값 |
| `iat`, `exp` | number | epoch 초. `exp - iat = 10800` |

- 백엔드는 서명/만료/`iss`를 검증한다. 프런트는 토큰을 **디코딩해 사용하지 않는다**(사용자 정보는 응답 바디의 `user`를 사용).

### 0.4 공통 에러 포맷

모든 4xx/5xx 응답(Spring Security 필터 단계의 401/403 포함)은 아래 형식이다.

```json
{
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "message": "아이디 또는 비밀번호가 올바르지 않습니다.",
  "path": "/api/auth/login",
  "timestamp": "2026-10-28T15:00:00+09:00",
  "errors": []
}
```
| 필드 | 타입 | 설명 |
|------|------|------|
| `status` | number | HTTP 상태 코드와 동일 |
| `code` | string | 아래 에러 코드 표의 값 |
| `message` | string | 사용자에게 그대로 보여 줄 수 있는 한국어 메시지 |
| `path` | string | 요청 경로 |
| `timestamp` | string | ISO-8601 |
| `errors` | array | 필드 검증 오류 목록 `[{ "field": string, "message": string }]`. 없으면 `[]` |

### 0.5 에러 코드 표

| HTTP | code | 언제 | message (고정) |
|------|------|------|----------------|
| 400 | `VALIDATION_ERROR` | Bean Validation 실패, JSON 파싱 실패, 지원하지 않는 Content-Type(415 대신), 쿼리 파라미터 누락/타입 오류 | `입력값을 확인해 주세요.` |
| 401 | `INVALID_CREDENTIALS` | 로그인 아이디 없음 또는 비밀번호 불일치 | `아이디 또는 비밀번호가 올바르지 않습니다.` |
| 401 | `UNAUTHORIZED` | 보호 경로에 `Authorization` 헤더 없음 | `로그인이 필요합니다.` |
| 401 | `TOKEN_EXPIRED` | 액세스 토큰 만료 | `인증이 만료되었습니다.` |
| 401 | `INVALID_TOKEN` | 액세스 토큰 서명 오류/형식 오류/iss 불일치 | `유효하지 않은 인증 정보입니다.` |
| 401 | `REFRESH_TOKEN_INVALID` | 리프레시 쿠키 없음/DB에 없음/만료/사용자 없음 | `다시 로그인해 주세요.` |
| 403 | `FORBIDDEN` | 인증은 되었으나 권한 부족 (이번 범위에서는 발생 경로 없음, 핸들러만 구성) | `접근 권한이 없습니다.` |
| 404 | `NOT_FOUND` | 존재하지 않는 API 경로 | `요청한 리소스를 찾을 수 없습니다.` |
| 405 | `METHOD_NOT_ALLOWED` | 경로는 있으나 지원하지 않는 HTTP 메서드 (예: `GET /api/auth/login`) | `허용되지 않는 요청 방식입니다.` |
| 501 | `NOT_IMPLEMENTED` | 구매 placeholder | `준비 중인 기능입니다.` |
| 500 | `INTERNAL_ERROR` | 그 외 서버 오류 (스택트레이스 노출 금지) | `일시적인 오류가 발생했습니다.` |

- (2026-10-28 검수 반영) `405 METHOD_NOT_ALLOWED` 행과 400의 Content-Type/파라미터 오류 사례는 백엔드 구현 후 추가했다. 프런트는 `ErrorCode` 유니언에 `METHOD_NOT_ALLOWED`를 추가해도 되고, 추가하지 않아도 `message`를 그대로 표시하므로 동작 차이는 없다.
- 액세스 토큰 서명 알고리즘은 HS256으로 **고정**한다(키 길이와 무관). 서버는 HS256 이외 알고리즘의 토큰을 `INVALID_TOKEN`으로 거부한다.
- 공개 경로에 잘못된/만료된 `Authorization` 헤더가 붙어 와도 **401을 주지 않고 익명으로 처리**한다(토큰 검증 실패 시 SecurityContext를 비우고 통과). 보호 경로에서만 401이 나간다. 이때 code는 실패 원인(`TOKEN_EXPIRED`/`INVALID_TOKEN`/`UNAUTHORIZED`)을 반영한다.

### 0.6 공통 객체: `AuthUser`
로그인/갱신 응답에 포함되는 사용자 요약.
```json
{
  "userId": "user01",
  "userName": "홍길동",
  "role": "ROLE_USER"
}
```
| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | string | |
| `userName` | string | |
| `role` | string | JWT `role` 클레임과 동일 값 (`"ROLE_USER"` 또는 `"ROLE_ADMIN"` 등) |

### 0.7 공통 객체: `TokenResponse`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzaG9wLWFwaSIsInN1YiI6InVzZXIwMSJ9.signature",
  "tokenType": "Bearer",
  "expiresIn": 10800,
  "accessTokenExpiresAt": "2026-10-28T18:00:00+09:00",
  "user": {
    "userId": "user01",
    "userName": "홍길동",
    "role": "ROLE_USER"
  }
}
```
| 필드 | 타입 | 설명 |
|------|------|------|
| `accessToken` | string | JWT |
| `tokenType` | string | 고정 `"Bearer"` |
| `expiresIn` | number | 초 단위, `10800` |
| `accessTokenExpiresAt` | string | 만료 시각 ISO-8601 |
| `user` | AuthUser | |

---

## 1. 엔드포인트 목록

| # | 메서드 | 경로 | 인증 | 설명 |
|---|--------|------|------|------|
| A1 | POST | `/api/auth/login` | 불필요 | 로그인 |
| A2 | POST | `/api/auth/refresh` | 리프레시 쿠키 | 액세스 토큰 갱신(리프레시 회전) |
| A3 | POST | `/api/auth/logout` | 불필요(쿠키 있으면 폐기) | 로그아웃 |
| U1 | GET | `/api/users/me` | **필요** | 내 정보 조회 |
| C1 | GET | `/api/cart` | **필요** | 장바구니 조회 (placeholder) |
| O1 | GET | `/api/orders` | **필요** | 구매조회 (placeholder) |
| O2 | POST | `/api/orders` | **필요** | 구매 (placeholder, 501) |

보호 경로 패턴: `/api/users/**`, `/api/cart/**`, `/api/orders/**`. 나머지는 모두 공개.

---

## A1. 로그인 `POST /api/auth/login`

인증: 불필요

### 요청
```json
{
  "userId": "user01",
  "password": "Passw0rd!"
}
```
| 필드 | 타입 | 필수 | 검증 |
|------|------|------|------|
| `userId` | string | Y | 공백 불가, 최대 50자 |
| `password` | string | Y | 공백 불가, 최대 100자 |

### 성공 `200 OK`
- 헤더: `Set-Cookie: refresh_token=<랜덤값>; Path=/api/auth; Max-Age=1209600; HttpOnly; SameSite=Strict`
- 바디: `TokenResponse` (0.7 예시와 동일 형식)

### 에러
`400 VALIDATION_ERROR`
```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해 주세요.",
  "path": "/api/auth/login",
  "timestamp": "2026-10-28T15:00:00+09:00",
  "errors": [
    { "field": "userId", "message": "아이디를 입력해 주세요." },
    { "field": "password", "message": "비밀번호를 입력해 주세요." }
  ]
}
```
필드 메시지(고정): `userId` 공백 → `아이디를 입력해 주세요.`, `userId` 50자 초과 → `아이디는 50자 이하로 입력해 주세요.`, `password` 공백 → `비밀번호를 입력해 주세요.`, `password` 100자 초과 → `비밀번호는 100자 이하로 입력해 주세요.`

`401 INVALID_CREDENTIALS`
```json
{
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "message": "아이디 또는 비밀번호가 올바르지 않습니다.",
  "path": "/api/auth/login",
  "timestamp": "2026-10-28T15:00:00+09:00",
  "errors": []
}
```

---

## A2. 토큰 갱신 `POST /api/auth/refresh`

인증: `refresh_token` 쿠키 (Authorization 헤더 불필요, 있어도 무시)

### 요청
- 바디 없음
- 쿠키: `refresh_token=<값>`

### 처리
1. 쿠키 값의 SHA-256 hex로 `tb_refresh_tokens` 조회
2. 없거나 `expires_at` 지남 → 401 (만료 행은 삭제)
3. 사용자 조회(삭제된 사용자면 401). 최신 `user_name`, `role_id`로 새 액세스 토큰 발급
4. 기존 행 삭제 + 새 리프레시 토큰 발급/저장(회전), 새 쿠키 설정 (유효기간 14일 새로 시작)

### 성공 `200 OK`
- 헤더: `Set-Cookie: refresh_token=<새 랜덤값>; Path=/api/auth; Max-Age=1209600; HttpOnly; SameSite=Strict`
- 바디: `TokenResponse`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzaG9wLWFwaSIsInN1YiI6InVzZXIwMSJ9.newsignature",
  "tokenType": "Bearer",
  "expiresIn": 10800,
  "accessTokenExpiresAt": "2026-10-28T21:00:00+09:00",
  "user": { "userId": "user01", "userName": "홍길동", "role": "ROLE_USER" }
}
```

### 에러
`401 REFRESH_TOKEN_INVALID` (쿠키 없음/불일치/만료/사용자 없음 모두 동일)
- 헤더: 쿠키 삭제 `Set-Cookie: refresh_token=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Strict`
```json
{
  "status": 401,
  "code": "REFRESH_TOKEN_INVALID",
  "message": "다시 로그인해 주세요.",
  "path": "/api/auth/refresh",
  "timestamp": "2026-10-28T15:00:00+09:00",
  "errors": []
}
```

---

## A3. 로그아웃 `POST /api/auth/logout`

인증: 불필요 (액세스 토큰 만료 상태에서도 로그아웃 가능해야 함)

### 요청
- 바디 없음, 쿠키 `refresh_token`(있을 수도 없을 수도 있음)

### 성공 `204 No Content`
- 쿠키가 있으면 해당 해시 행 삭제. 없거나 DB에 없어도 **항상 204**
- 헤더: `Set-Cookie: refresh_token=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Strict`
- 바디 없음

### 에러
- 없음 (500 `INTERNAL_ERROR`만 가능)

---

## U1. 내 정보 조회 `GET /api/users/me`

인증: **필요** (`Authorization: Bearer <accessToken>`)

### 성공 `200 OK`
```json
{
  "userId": "user01",
  "userName": "홍길동",
  "gender": "M",
  "birth": "900101",
  "email": "user01@example.com",
  "phone": "010-1234-5678",
  "zipCode": "04524",
  "addr": "서울특별시 중구 세종대로 110",
  "addrDtl": "101호",
  "role": "ROLE_USER",
  "roleName": "일반회원",
  "createAt": "2026-10-28T10:00:00+09:00"
}
```
| 필드 | 타입 | 비고 |
|------|------|------|
| `userId` | string | |
| `userName` | string | |
| `gender` | string | DB 저장값 그대로 |
| `birth` | string | 6자리 `yyMMdd` |
| `email` | string | |
| `phone` | string | |
| `zipCode` | string | |
| `addr` | string | |
| `addrDtl` | string \| null | DB 값이 NULL이면 `null`, 빈 문자열이면 `""` |
| `role` | string | 정규화된 권한 값 (0.3 규칙) |
| `roleName` | string \| null | `user_roles.role_name` (LEFT JOIN, 없으면 `null`) |
| `createAt` | string \| null | |

- `passwd`는 **절대 포함하지 않는다**.
- 조회 대상은 토큰의 `sub`. 경로/쿼리로 다른 사용자 아이디를 받지 않는다.

### 에러
`401 UNAUTHORIZED` (헤더 없음)
```json
{
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다.",
  "path": "/api/users/me",
  "timestamp": "2026-10-28T15:00:00+09:00",
  "errors": []
}
```
`401 TOKEN_EXPIRED`
```json
{
  "status": 401,
  "code": "TOKEN_EXPIRED",
  "message": "인증이 만료되었습니다.",
  "path": "/api/users/me",
  "timestamp": "2026-10-28T15:00:00+09:00",
  "errors": []
}
```
`401 INVALID_TOKEN`
```json
{
  "status": 401,
  "code": "INVALID_TOKEN",
  "message": "유효하지 않은 인증 정보입니다.",
  "path": "/api/users/me",
  "timestamp": "2026-10-28T15:00:00+09:00",
  "errors": []
}
```
`401 INVALID_TOKEN` 은 토큰은 유효하나 해당 사용자가 DB에 없는 경우에도 사용한다.

---

## C1. 장바구니 조회 `GET /api/cart` (placeholder)

인증: **필요**

### 성공 `200 OK`
```json
{
  "items": [],
  "totalCount": 0
}
```
- 이번 범위에서는 항상 빈 목록. `items` 원소 형식은 후속 작업에서 정의.

### 에러
401 `UNAUTHORIZED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` (U1과 동일 형식, `path`만 `/api/cart`)

---

## O1. 구매조회 `GET /api/orders` (placeholder)

인증: **필요**

### 성공 `200 OK`
```json
{
  "items": [],
  "totalCount": 0
}
```

### 에러
401 `UNAUTHORIZED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` (`path`: `/api/orders`)

---

## O2. 구매 `POST /api/orders` (placeholder)

인증: **필요**

### 요청
- 바디: 임의 JSON 허용(검증하지 않음). 프런트는 `{}`를 보낸다.

### 응답 `501 Not Implemented`
```json
{
  "status": 501,
  "code": "NOT_IMPLEMENTED",
  "message": "준비 중인 기능입니다.",
  "path": "/api/orders",
  "timestamp": "2026-10-28T15:00:00+09:00",
  "errors": []
}
```
- 비로그인 시에는 501이 아니라 401 (인증 검사가 먼저).

---

## 2. 프런트 토큰 처리 규약 (백엔드 동작과 맞물리는 부분)

1. 로그인 성공 → `accessToken`, `user`, `accessTokenExpiresAt`을 zustand에 저장.
2. 모든 API 요청에 `Authorization: Bearer <accessToken>` 첨부(토큰이 있을 때만). `/api/auth/*` 요청에는 첨부하지 않는다.
3. 응답이 **401**이고, 요청 URL이 `/api/auth/`로 시작하지 않으며, 해당 요청이 아직 재시도되지 않았으면:
   - `POST /api/auth/refresh` 호출 (탭 내 동시 요청은 하나의 refresh Promise 공유)
   - 성공 → store 갱신 후 원 요청을 새 토큰으로 **1회** 재시도
   - 실패 → store 초기화(로그아웃 상태), 보호 화면이면 `/login?redirect=<현재경로>`로 이동
4. 앱 시작 시(새로고침 포함) `POST /api/auth/refresh`를 1회 호출해 세션 복구. 401이면 비로그인 상태로 확정(에러 표시 없음).
5. 로그아웃 → `POST /api/auth/logout` 호출 후 결과와 무관하게 store 초기화, `/`로 이동.

## 3. 서버 설정 규약

| 설정 키 | local 기본값 | prod |
|---------|--------------|------|
| `server.port` | `8080` | `8080` |
| `app.jwt.secret` | `${JWT_SECRET:<개발용 Base64 키>}` | `${JWT_SECRET}` (필수, 기본값 없음) |
| `app.jwt.issuer` | `shop-api` | `shop-api` |
| `app.jwt.access-token-validity-seconds` | `10800` | `10800` |
| `app.jwt.refresh-token-validity-seconds` | `1209600` | `1209600` |
| `app.cookie.secure` | `false` | `true` |
| `app.cors.allowed-origins` | `http://localhost:5173` | `${CORS_ALLOWED_ORIGINS}` |

프런트 `.env.development`: `VITE_API_PROXY_TARGET=http://localhost:8080`, `VITE_USE_MOCK=false`

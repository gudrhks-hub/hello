# 프런트 작업 지시서 (frontend-task.md)

담당: `frontend-dev`
먼저 읽을 문서: `plan.md`(특히 5. 결정 사항 D7~D9, D13), `api-contract.md`(유일한 기준, 특히 2장 "프런트 토큰 처리 규약")
수정 범위: `/home/user/frontend/` 만. `backend/`는 건드리지 않는다. 백엔드는 동시에 개발 중이므로 MSW mock으로 개발·테스트한다.

## 1. 프로젝트 골격

- 경로: `/home/user/frontend`
- Vite + React 19 + TypeScript (`npm create vite@latest frontend -- --template react-ts` 또는 동등 구성)
- 의존성: `react-router-dom@7`, `zustand@5`, `axios@1`, `react-hook-form`, `zod`, `@hookform/resolvers`
- 개발 의존성: `msw@2`, `vitest`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `jsdom`
- 스타일: 별도 UI 라이브러리 없이 CSS(모듈 또는 단일 CSS). 과한 디자인 불필요, 기본 접근성(label, aria, 포커스) 준수

### 권장 구조
```
src/
 ├─ main.tsx, App.tsx
 ├─ api/        client.ts(axios 인스턴스+인터셉터), auth.ts, users.ts, cart.ts, orders.ts, types.ts(계약 타입), errors.ts(ApiError 파싱)
 ├─ stores/     authStore.ts (zustand)
 ├─ routes/     router.tsx, ProtectedRoute.tsx
 ├─ pages/      HomePage, LoginPage, MyPage, CartPage, OrdersPage, CheckoutPage, NotFoundPage
 ├─ components/ Header(로그인 상태/이름/로그아웃), 공통 Loading/ErrorMessage/Empty
 ├─ mocks/      handlers.ts, browser.ts, server.ts (MSW)
 └─ test/       setup.ts
```

## 2. 체크리스트

### 2.1 설정
- [ ] `vite.config.ts`: dev server `port: 5173`, `server.proxy['/api'] = { target: env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080', changeOrigin: true }` (`loadEnv` 사용, 주소 하드코딩 금지)
- [ ] `.env.development`: `VITE_API_PROXY_TARGET=http://localhost:8080`, `VITE_USE_MOCK=false`
- [ ] `VITE_USE_MOCK=true`이면 `main.tsx`에서 MSW worker 시작 후 렌더
- [ ] `package.json` 스크립트: `dev`, `build`(`tsc -b && vite build`), `test`(`vitest run`), `preview`

### 2.2 계약 타입 (`src/api/types.ts`)
- [ ] `AuthUser { userId: string; userName: string; role: string }`
- [ ] `TokenResponse { accessToken: string; tokenType: 'Bearer'; expiresIn: number; accessTokenExpiresAt: string; user: AuthUser }`
- [ ] `LoginRequest { userId: string; password: string }`
- [ ] `UserMe { userId; userName; gender; birth; email; phone; zipCode; addr; addrDtl: string | null; role; roleName: string | null; createAt: string | null }`
- [ ] `ListResponse { items: unknown[]; totalCount: number }`
- [ ] `ApiErrorBody { status: number; code: ErrorCode; message: string; path: string; timestamp: string; errors: { field: string; message: string }[] }`
- [ ] `ErrorCode` 유니언: 계약 0.5 표의 code 전부

### 2.3 zustand 스토어 (`src/stores/authStore.ts`)
- [ ] 상태: `accessToken: string | null`, `accessTokenExpiresAt: string | null`, `user: AuthUser | null`, `status: 'idle' | 'loading' | 'authenticated' | 'unauthenticated'`
- [ ] 액션: `setAuth(tokenResponse)`, `clearAuth()`, `login(req)`, `logout()`, `bootstrap()`(앱 시작 시 refresh 1회)
- [ ] 파생: `isAuthenticated`, `hasRole(role: string)` 셀렉터/헬퍼
- [ ] **persist 미들웨어 사용 금지**(D8). localStorage/sessionStorage에 토큰 저장 금지
- [ ] 토큰·비밀번호 `console.*` 출력 금지
- [ ] React 외부(axios 인터셉터)에서 `useAuthStore.getState()`로 접근

### 2.4 axios 클라이언트 (`src/api/client.ts`)
- [ ] `baseURL: '/api'`, `withCredentials: true`
- [ ] 요청 인터셉터: 토큰이 있고 URL이 `/auth/`로 시작하지 않으면 `Authorization: Bearer <token>`
- [ ] 응답 인터셉터 (계약 2장 그대로)
  - 401 && 요청 URL이 `/auth/` 아님 && `_retry` 없음 → refresh 시도
  - **single-flight**: 진행 중 refresh Promise를 모듈 변수로 공유, 완료 후 해제
  - 성공 → `setAuth` → 원 요청 헤더 교체 후 1회 재시도
  - 실패 → `clearAuth()` → 보호 라우트에 있으면 `/login?redirect=<현재 경로>`로 이동(라우터 navigate 또는 상태 변화로 ProtectedRoute가 처리)
- [ ] 에러 파싱: 응답 바디가 계약 에러 포맷이면 `ApiError`(code, message, fieldErrors)로 변환, 아니면 네트워크 오류 메시지 `서버에 연결할 수 없습니다.`
- [ ] refresh 호출은 인터셉터 재귀를 피하도록 별도 axios 인스턴스 또는 플래그 사용

### 2.5 라우팅/화면
| 경로 | 보호 | 화면 내용 |
|------|------|-----------|
| `/` | 공개 | 홈. 로그인 상태면 "홍길동님 환영합니다" |
| `/login` | 공개 | 로그인 폼. 이미 로그인 상태면 `redirect` 또는 `/`로 이동 |
| `/mypage` | **보호** | `GET /api/users/me` 결과 표시(아이디, 이름, 성별, 생년월일, 이메일, 연락처, 우편번호, 주소, 상세주소, 권한명, 가입일) |
| `/cart` | **보호** | `GET /api/cart`. 빈 목록이면 "장바구니가 비어 있습니다." |
| `/orders` | **보호** | `GET /api/orders`. 빈 목록이면 "구매 내역이 없습니다." |
| `/checkout` | **보호** | "구매하기" 버튼 → `POST /api/orders` `{}` → 501이면 서버 message("준비 중인 기능입니다.") 표시 |
| `*` | 공개 | 404 화면 |

- [ ] `ProtectedRoute`: `status`가 `idle`/`loading`이면 로딩 표시(깜빡임으로 로그인 화면에 튕기지 않게), `unauthenticated`면 `/login?redirect=<원래 경로>`로 `replace` 이동
- [ ] `App` 마운트 시 `bootstrap()` 1회 (StrictMode 이중 호출에도 refresh가 한 번만 나가도록 가드)
- [ ] Header: 비로그인 → "로그인" 링크. 로그인 → `user.userName`, 메뉴(마이페이지/장바구니/구매내역), "로그아웃" 버튼. `role === 'ROLE_ADMIN'`이면 이름 옆에 "관리자" 배지(권한 기반 표시 예시)
- [ ] `redirect` 파라미터는 `/`로 시작하는 내부 경로만 허용(오픈 리다이렉트 방지)

### 2.6 로그인 폼
- [ ] React Hook Form + Zod: `userId` 필수·50자 이하, `password` 필수·100자 이하. 메시지는 계약 A1의 필드 메시지와 동일 문구
- [ ] 제출 중 버튼 비활성화 + 로딩 표시
- [ ] 401 `INVALID_CREDENTIALS` → 폼 상단에 서버 `message` 표시 (`role="alert"`)
- [ ] 400 `VALIDATION_ERROR` → `errors[]`를 필드별로 `setError`
- [ ] 비밀번호 입력 `type="password"`, `autoComplete="current-password"`, 아이디 `autoComplete="username"`
- [ ] 성공 → `redirect` 경로(없으면 `/`)로 이동

### 2.7 MSW mock (`src/mocks/handlers.ts`)
- [ ] 계약 예시 값 그대로: `user01`/`Passw0rd!` 로그인 성공, 그 외 401 `INVALID_CREDENTIALS`, 빈 값 400
- [ ] refresh: mock 내부 상태로 로그인 여부 관리(쿠키는 MSW에서 흉내 수준), 비로그인 401 `REFRESH_TOKEN_INVALID`
- [ ] `/api/users/me`, `/api/cart`, `/api/orders` : `Authorization` 없으면 401 `UNAUTHORIZED`, 특정 토큰값(예: `expired-token`)이면 401 `TOKEN_EXPIRED`
- [ ] `POST /api/orders` 501

### 2.8 테스트 (Vitest + Testing Library + MSW node server, 실제 실행)
- [ ] authStore: login 성공 시 상태 저장, logout 시 초기화, persist 미사용(localStorage 비어 있음)
- [ ] 인터셉터: 보호 API 401(TOKEN_EXPIRED) → refresh 1회 → 원 요청 재시도 성공
- [ ] 인터셉터: 동시에 2개 요청이 401 → refresh 호출은 **1회**
- [ ] 인터셉터: refresh 401 → `clearAuth` 되고 상태 `unauthenticated`
- [ ] LoginPage: 빈 값 제출 시 필드 메시지, 잘못된 비밀번호 시 서버 메시지 표시, 성공 시 이동
- [ ] ProtectedRoute: 비로그인으로 `/mypage` 접근 → `/login?redirect=%2Fmypage`

## 3. 완료 기준

1. `cd /home/user/frontend && npm install && npm run build` 성공 (타입 에러 0)
2. `npm test` 성공
3. `VITE_USE_MOCK=true npm run dev`로 서버 없이 로그인 → 마이페이지/장바구니/구매내역/구매 흐름 확인 가능
4. 백엔드 기동 상태에서 `npm run dev`(프록시)로 동일 흐름 동작하도록 계약 경로/필드 그대로 사용
5. 새로고침 후에도 로그인 상태 유지(refresh 쿠키 기반 bootstrap), 토큰이 Web Storage에 없음
6. `/home/user/docs/dev-team/jwt-login/frontend-report.md` 작성 (에이전트 정의의 보고 형식)

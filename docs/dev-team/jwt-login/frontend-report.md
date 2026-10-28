STATUS: DONE
CHANGED_FILES:
- /home/user/frontend/ (신규 생성, backend/ 및 docs/ 기존 문서는 미수정)
- package.json, index.html, vite.config.ts, tsconfig*.json, .env.development, .gitignore, public/mockServiceWorker.js
- src/main.tsx, src/App.tsx, src/index.css
- src/api/{types,errors,client,auth,users,cart,orders}.ts
- src/stores/authStore.ts
- src/routes/{router.tsx,ProtectedRoute.tsx,redirect.ts}
- src/pages/{Home,Login,MyPage,Cart,Orders,Checkout,NotFound}Page.tsx
- src/components/{Header,Layout,Feedback}.tsx, useApiData.ts
- src/mocks/{handlers,browser,server}.ts
- src/test/{setup.ts,authStore.test.ts,interceptor.test.ts,pages.test.tsx}
BUILD_TEST_RESULT:
- npm install: 성공 (취약점 0)
- npm run build (tsc -b && vite build): 성공, 타입 에러 0 (최초 실행 시 zustand 자기참조 타입 에러 6건 발생 -> get() 사용으로 수정 후 재실행 통과)
- npm test (vitest run): 3 files, 15 tests 모두 통과
- VITE_USE_MOCK=true npm run dev: 서버 기동 및 /, /mockServiceWorker.js 200 확인. 브라우저에서의 화면 클릭 흐름은 직접 확인하지 못함(자동 테스트로만 검증)
- 백엔드 연동(프록시) 실제 호출은 서버가 없어 미검증
RUN:
- cd /home/user/frontend && npm install
- 백엔드 연동: npm run dev (http://localhost:5173, /api -> VITE_API_PROXY_TARGET, 기본 http://localhost:8080)
- 서버 없이: VITE_USE_MOCK=true npm run dev (계정 user01 / Passw0rd!)
- 환경 변수: VITE_API_PROXY_TARGET, VITE_USE_MOCK (.env.development에 기본값)
- 테스트: npm test
CONTRACT_ISSUES:
- 없음 (계약 그대로 구현). 참고: 계약 2장은 refresh 실패 시 "보호 화면이면 /login?redirect=로 이동"이라 되어 있어, 인터셉터는 clearAuth만 하고 ProtectedRoute가 status 변화를 보고 이동시키는 방식으로 구현함.
- 참고: refresh 호출이 /auth/ 로 시작하므로 Authorization 헤더는 붙지 않으며, 만료 토큰이 있어도 백엔드 refresh는 쿠키만 사용한다는 계약과 일치.
TODO:
- 실제 백엔드 기동 후 프록시 경유 E2E 확인 (쿠키 Path=/api/auth, 회전 동작)
- 탭 간 refresh 경쟁(D13)은 범위 밖: 다른 탭이 회전시킨 경우 해당 탭은 로그아웃 처리됨
- 장바구니/구매내역 items 원소 렌더링은 후속(현재 빈 목록만 정의됨)

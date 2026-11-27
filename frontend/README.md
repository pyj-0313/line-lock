# frontend

빌드 도구 없이 순수 HTML/CSS/JS(ES modules)로 만들고, `fetch`로 백엔드 API를 호출한다.
실무에서 흔한 React 같은 프레임워크를 쓰지 않은 것은 브라우저 기본 동작을 익히기 위한 의도적인 선택이다.

## 구조

| 파일 | 역할 |
|---|---|
| `index.html` | 화면 (로그인, 회원가입, 로그인 후 화면) |
| `js/main.js` | 화면 이벤트 처리와 화면 전환 |
| `js/api.js` | API 호출 공통 함수. 백엔드 주소 관리, 토큰 첨부, 에러 처리 |
| `js/auth.js` | 토큰 저장/조회/삭제 (`localStorage`) |
| `css/style.css` | 공통 스타일(색상과 간격 변수, 입력칸, 버튼)과 화면 배치 |

## 실행

1. MySQL과 Redis를 켜고 백엔드를 실행한다. (`backend/` 에서)

   ```bash
   ./gradlew bootRun
   ```

2. 프론트 서버를 실행한다. (저장소 최상위 폴더에서)

   ```bash
   python3 -m http.server 5500 --directory frontend
   ```

3. 브라우저에서 `http://localhost:5500` 을 연다.

### 주의

- 반드시 `localhost:5500`으로 연다. 백엔드의 CORS 허용 출처(`cors.allowed-origins`)가 이 주소라서, `127.0.0.1:5500`이나 다른 포트로 열면 요청이 차단된다.
- VS Code Live Server가 5500 포트를 쓰고 있으면 먼저 끈다. 기본 설정으로는 `127.0.0.1`을 열고, `frontend/`가 아닌 상위 폴더를 보여줄 수 있다.
- ES modules는 `index.html` 파일을 직접 더블클릭해서 열면 동작하지 않는다.
- 다른 주소를 쓰려면 백엔드의 `cors.allowed-origins`와 `js/api.js`의 `API_BASE_URL`을 맞춘다.

## 알려진 한계

- **공개 회원가입**: 학습용으로 만들었다. 실제 사내 설비 시스템은 관리자가 계정을 만들어야 하므로, 배포 전에 ADMIN 전용 계정 생성 방식으로 바꾼다.
- **토큰 저장 위치**: `localStorage`는 XSS 공격에 노출될 수 있다. 화면에는 값을 `textContent`로만 넣고, refresh token(쿠키) 방식은 추후 과제로 둔다.

## 예정 화면

- 설비 목록 + 예약
- 내 작업지시 (완료/취소)
- (아두이노 연동 후) 설비 상태 실시간 표시

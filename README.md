# Open-WebUI API Gateway

Open-WebUI 웹 UI 없이 **API만으로** 인증 및 채팅을 할 수 있게 해주는 프록시 게이트웨이.

Open-WebUI의 API Key는 만료 기한이 없어 보안에 취약한데, 이 Gateway는 **만료 기한이 있는 JWT 토큰**을 발급하여 이 문제를 보완한다.

## 아키텍처

```
클라이언트 (API 사용자)
  │
  │  ① POST /api/v1/gateway/auth/login  (email + password)
  │  ② 이후 모든 API: /api/v1/openwebui/**  (Gateway JWT)
  │
  ▼
┌─────────────────────────────────┐
│  API Gateway (:9090)            │
│  - Gateway JWT 발급/검증         │
│  - 만료 주기: open-webui와 동기화  │
│  - open-webui로 프록시            │
└──────────┬──────────────────────┘
           │  내부 JWT (30초 수명, WEBUI_SECRET_KEY 서명)
           ▼
┌─────────────────────────────────┐
│  Open-WebUI (:8080)             │
│  - 실제 LLM 처리                 │
└──────────┬──────────────────────┘
           │
           ▼
┌─────────────────────────────────┐
│  PostgreSQL                     │
│  - user, auth, config 테이블     │
│  - Gateway와 Open-WebUI가 공유   │
└─────────────────────────────────┘
```

## 기술 스택

- Java 21, Spring Boot 4.0.4
- Spring Security (Stateless JWT)
- Spring Data JPA (PostgreSQL)
- jjwt 0.12.6

## 빠른 시작 (Docker Compose)

```bash
# 1. 환경변수 설정
cp .env.example .env
# .env 파일을 열어 비밀번호와 시크릿 키를 입력

# 2. 실행
docker compose up -d
```

세 개의 서비스가 기동된다:

| 서비스 | 포트 | 설명 |
|---|---|---|
| postgres | 5432 | 공유 DB |
| open-webui | 8080 | Open-WebUI 본체 |
| api-gateway | 9090 | 이 프로젝트 |

## 환경변수

`.env.example`을 복사하여 `.env`를 만들고 아래 값들을 설정한다.

| 변수 | 설명 | 필수 |
|---|---|---|
| `DB_USERNAME` | PostgreSQL 사용자명 | O |
| `DB_PASSWORD` | PostgreSQL 비밀번호 | O |
| `DB_NAME` | 데이터베이스명 (기본: `open_webui`) | O |
| `DB_PORT` | PostgreSQL 포트 (기본: `5432`) | O |
| `WEBUI_SECRET_KEY` | Open-WebUI의 JWT 서명 키. **양쪽이 반드시 동일해야 함** | O |
| `GATEWAY_JWT_SECRET` | Gateway 자체 JWT 서명 키 | O |
| `GATEWAY_JWT_EXPIRES_IN_FALLBACK` | DB에 만료 설정이 없을 때 폴백 (기본: `4w`) | |

> **`WEBUI_SECRET_KEY`는 Open-WebUI의 환경변수와 반드시 동일한 값을 사용해야 한다.** Gateway가 이 키로 내부 JWT를 서명하여 Open-WebUI에 프록시 인증하기 때문이다.

## API 사용법

### 1. 로그인

```bash
curl -X POST http://localhost:9090/api/v1/gateway/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "mypassword"}'
```

응답:

```json
{
  "token": "eyJhbG...",
  "tokenType": "Bearer",
  "expiresAt": 1751326800,
  "id": "user-uuid",
  "email": "user@example.com",
  "name": "홍길동",
  "role": "user",
  "profileImageUrl": "..."
}
```

### 2. 채팅

```bash
curl -X POST http://localhost:9090/api/v1/openwebui/openai/chat/completions \
  -H "Authorization: Bearer eyJhbG..." \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3",
    "messages": [{"role": "user", "content": "안녕하세요"}]
  }'
```

### 3. 기타 Open-WebUI API

`/api/v1/openwebui/` 뒤에 Open-WebUI의 원래 경로를 붙이면 된다.

```bash
# 모델 목록
curl http://localhost:9090/api/v1/openwebui/api/models \
  -H "Authorization: Bearer eyJhbG..."

# 채팅 기록
curl http://localhost:9090/api/v1/openwebui/api/v1/chats \
  -H "Authorization: Bearer eyJhbG..."
```

### 4. 헬스체크

```bash
curl http://localhost:9090/actuator/health
```

## JWT 만료 주기 동기화

Gateway는 기동 시 Open-WebUI의 `config` 테이블에서 `auth.jwt_expiry` 값을 1회 조회하여 캐시한다. Open-WebUI 관리자가 웹 UI에서 만료 주기를 변경한 경우, Gateway를 재기동하면 새 설정이 반영된다.

조회 우선순위:
1. DB `config` 테이블의 `auth.jwt_expiry`
2. 환경변수 `GATEWAY_JWT_EXPIRES_IN_FALLBACK`
3. 하드코딩 기본값 `4w` (4주)

지원하는 만료 포맷: `ms`, `s`, `m`, `h`, `d`, `w` (예: `4w`, `24h`, `7d`)

## 프로젝트 구조

```
src/main/java/com/openwebui/gateway/
├── OpenWebuiGatewayApplication.java
├── config/
│   ├── SecurityConfig.java          # Spring Security 설정
│   └── GlobalExceptionHandler.java  # 전역 예외 처리
├── controller/
│   ├── AuthController.java          # POST /api/v1/gateway/auth/login
│   └── ProxyController.java         # /api/v1/openwebui/** → open-webui
├── dto/
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   └── ErrorResponse.java
├── entity/
│   ├── User.java                    # open-webui user 테이블 (읽기 전용)
│   ├── Auth.java                    # open-webui auth 테이블 (읽기 전용)
│   └── Config.java                  # open-webui config 테이블 (읽기 전용)
├── repository/
│   ├── UserRepository.java
│   ├── AuthRepository.java
│   └── ConfigRepository.java
├── security/
│   ├── JwtTokenProvider.java        # JWT 발급/검증 + 만료 캐시
│   └── JwtAuthenticationFilter.java # 요청별 JWT 검증 필터
└── service/
    ├── AuthService.java             # 로그인 (bcrypt 검증)
    └── ProxyService.java            # open-webui 프록시
```

## 로컬 개발 (Docker 없이)

```bash
# PostgreSQL과 Open-WebUI가 이미 실행 중인 환경에서:
./gradlew bootRun \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/open_webui \
  -Dspring.datasource.username=postgres \
  -Dspring.datasource.password=yourpassword \
  -Dgateway.jwt.secret=your-secret \
  -Dgateway.openwebui.url=http://localhost:8080 \
  -Dgateway.openwebui.secret-key=your-webui-secret-key
```

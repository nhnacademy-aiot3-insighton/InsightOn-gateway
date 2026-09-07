# insighton-gateway

InsightOn 플랫폼의 API Gateway. 모든 외부 요청이 거치는 단일 진입점(edge)으로, JWT 인증, 서비스 라우팅, 접근 로깅, API 문서 통합 등을 담당한다.

## 역할

InsightOn은 `auth` / `core` / `ai` / `ruleengine` / `front` 등 여러 마이크로서비스로 구성되어 있고, `insighton-gateway`는 이 서비스들 앞단에서 다음을 수행한다.

- 클라이언트 요청을 경로(path) 기준으로 각 백엔드 서비스로 라우팅
- JWT 액세스 토큰 검증 및 인증/인가 처리 (하위 서비스는 인증을 신뢰)
- 로그아웃/탈퇴 등으로 무효화된 토큰을 auth 서비스에 조회하여 차단 (블랙리스트 체크)
- 요청 접근 로그 기록
- 하위 서비스들의 Swagger(OpenAPI) 문서를 게이트웨이 한 곳에서 통합 제공
- Actuator/Prometheus/Zipkin을 통한 헬스체크, 메트릭, 분산 추적 노출

## 기술 스택

- Java 21, Spring Boot 4.1 (WebFlux 기반)
- Spring Cloud Gateway (Server WebFlux)
- JJWT (`io.jsonwebtoken`) — RSA 공개키 기반 JWT 검증
- springdoc-openapi (WebFlux UI) — Swagger 문서 통합
- Micrometer + Prometheus, Zipkin — 관측성
- Lombok, JSpecify(`@NullMarked`)
- Maven, Docker

## 요청 처리 흐름

```
Client
  │
  ▼
AccessLogFilter        (모든 요청/응답 로그 기록, 가장 먼저 실행)
  │
  ▼
JwtAuthenticationFilter
  ├─ 인증 제외 경로면 통과 (SecurityConstants.EXCLUDED_PATHS)
  ├─ Authorization: Bearer <token> 파싱
  ├─ RSA 공개키로 서명 검증 (JwtParserConfig / JwtKeyConfig)
  ├─ jti로 auth 서비스에 블랙리스트 여부 조회 (AuthServiceTokenBlacklistChecker)
  │    └─ auth 서비스 장애/타임아웃(2초) 시 fail-closed(거부) 처리
  └─ 통과 시 X-User-Id, X-User-Role(ADMIN인 경우) 헤더를 하위 서비스로 전달
     (클라이언트가 직접 보낸 X-User-Id/X-User-Role은 항상 제거 후 재설정 — 위조 방지)
  │
  ▼
GatewayRouteConfig에 정의된 라우트로 프록시
```

## 라우팅 규칙

`GatewayRouteConfig`에 정의된 경로 기반 라우팅. 대상 서비스 주소는 `gateway-route.*` 프로퍼티로 주입된다.

| 라우트 | 대상 서비스 | 경로 |
| --- | --- | --- |
| `auth-route` | insighton-auth | `/api/v1/users/**`, `/api/v1/auth/**`, `/api/v1/admin/**` |
| `core-route` | InsightOn-core | `/api/v1/groups/**`, `/api/v1/gateways/**`, `/api/v1/sensor/**`, `/api/v1/weather/**`, `/api/v1/regions/**`, `/api/v1/group-registrations/**` |
| `ai-route` | InsightOn-ai | `/api/v1/reports/**`, `/api/v1/suggestions/**`, `/api/v1/hourly-telemetry-stats/**`, `/api/v1/dashboard-notifications/**`, `/api/v1/engine-alerts/**`, `/api/v1/chat/**` |
| `ruleengine-route` | insighton-ruleengine | `/api/v1/flows/**` |
| `*-api-docs` | 각 서비스 | `/{auth,core,ai,ruleengine}/v3/api-docs` → 접두사 제거 후 각 서비스의 `/v3/api-docs`로 프록시 |

각 서비스의 API 문서는 게이트웨이의 `/api/swagger`에서 하나로 모아 볼 수 있다 (`config/prod/swagger.properties`).

### 인증 없이 접근 가능한 경로

`SecurityConstants.EXCLUDED_PATHS`에 정의된 로그인/회원가입/이메일 인증/비밀번호 찾기/OAuth/API 문서 관련 경로는 JWT 검증 없이 통과한다.

## 설정

주요 설정은 `application.properties`(공통)와 프로파일별 `application-{dev,prod}.properties`로 분리되어 있다.

| 프로퍼티 | 설명 |
| --- | --- |
| `server.port` | 기본 8080 |
| `jwt.public-key-base64` | JWT 서명 검증용 RSA 공개키 (PEM을 base64 인코딩), 환경변수 `JWT_PUBLIC_KEY`로 주입 |
| `gateway-route.auth` / `.core` / `.ai` / `.rule` | 각 하위 서비스 주소 (dev는 `localhost:포트`, 운영은 k8s 서비스명) |
| `management.endpoints.web.exposure.include` | 노출할 actuator 엔드포인트 (`health, info, prometheus, gateway` 등) |
| `management.tracing.export.zipkin.endpoint` | Zipkin 수집 서버 주소 (운영 프로파일) |

로컬 개발 시 필요한 환경변수는 `.env`에 정의한다 (`JWT_PUBLIC_KEY` 등).

## 로컬 실행

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

dev 프로파일은 `insighton-auth`(8000), `insighton-core`(8300), `insighton-ai`(8100), `insighton-ruleengine`(8200)이 로컬에서 함께 떠 있다고 가정한다.

헬스체크 및 라우트 확인:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/gateway/routes
```

## 테스트

```bash
./mvnw test
```

주요 테스트: `JwtKeyConfigTest`, `GatewayRouteConfigTest`, `AuthServiceTokenBlacklistCheckerTest`, `JwtAuthenticationFilterTest`, `AccessLogFilterTest`

## Docker

```bash
docker build -t insighton-gateway .
docker run -p 8080:8080 --env-file .env insighton-gateway
```

멀티스테이지 빌드(Maven → JRE 21)이며, `/actuator/health` 기반 `HEALTHCHECK`가 포함되어 있다.

## CI/CD

`main` / `dev-deploy` / `dev` 브랜치 push 및 PR 시 [InsightOn-infra](https://github.com/nhnacademy-aiot3-insighton/InsightOn-infra)의 재사용 워크플로우(`gateway-ci-cd.yml`)를 호출해 빌드/배포한다. 운영 환경은 k8s(`insighton-k8s-manifests`)에 Deployment로 배포된다.

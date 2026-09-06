# invi — 모바일 청첩장 서비스

1인 개발 · 포트폴리오 겸 실서비스. 하객 100명 이상 실사용 확보가 목표.
전체 아키텍처 배경은 프로젝트 기획서(구글 문서)와 별도로 정리한 아키텍처 문서를 참고 — 이 파일은 그중 "코드를 어떻게 짜는가"만 다룬다.

## 레포 구조

```
invi/
  backend/    Spring Boot (Gradle) — REST API
  frontend/   Vite + React + TypeScript — 에디터 SPA + 하객뷰
  docker-compose.yml   로컬 개발용 PostgreSQL
```

## 백엔드 컨벤션 (backend/)

**패키지**: `com.invi.api.<domain>` 단위로 기능별 분리. domain 하나에 entity·repository·dto를 같이 둔다(계층별 패키지 아님). 아직 컨트롤러/서비스가 없는 도메인은 entity + repository까지만 존재해도 된다 — 다음 단계에서 채운다.

- `common` — BaseEntity, JPA/Security 설정, 아직 도메인이 정해지지 않은 공용 코드
- `account` — Member(신랑신부 계정)
- `invitation` — Invitation(청첩장 본체)
- `template` — Template(템플릿 프리셋)
- `rsvp` — Rsvp(참석 응답)
- `guestbook` — GuestbookEntry(방명록)
- `media`, `payment` — Phase 1/3에서 추가 예정, 아직 생성하지 않음

**ID**: 모든 엔티티는 `BaseEntity`(`com.invi.api.common.BaseEntity`)를 상속한다. `UUID id`, `createdAt`, `updatedAt`을 자동으로 갖는다 — 엔티티에 다시 선언하지 않는다. PK를 UUID로 쓰는 이유는 슬러그·초대 링크처럼 외부에 노출되는 리소스라 순차 ID를 숨겨야 해서다.

**Lombok**: 엔티티는 `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Builder`/정적 팩토리 메서드 조합을 쓴다. `@Setter`나 `@Data`는 엔티티에 쓰지 않는다 — 상태 변경은 의도가 드러나는 메서드(`publish()`, `markPaid()` 등)로 한다. DTO는 Java `record`를 쓴다(Lombok 불필요).

**JSONB 컬럼**: `page_data`처럼 자유도 높은 구조는 고정 POJO로 매핑하지 않고 Jackson `JsonNode`를 그대로 저장한다.
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private JsonNode pageData;
```
이유는 아키텍처 문서 03번 결정 참고 — 에디터와 하객뷰가 같은 JSON을 공유 렌더링하기 때문에 백엔드가 스키마를 강하게 고정하면 안 된다.

**Jackson 2 / 3 경계 (중요, 헷갈리기 쉬움)**: Spring Boot 4가 HTTP 레이어를 Jackson 3(`tools.jackson.databind`)로 옮겼는데, Hibernate의 JSON 컬럼 매핑(`@JdbcTypeCode(SqlTypes.JSON)`)은 여전히 Jackson 2(`com.fasterxml.jackson.databind`)를 기준으로 동작한다. 두 라이브러리가 같은 클래스명(`JsonNode`, `ObjectMapper`)을 쓰기 때문에 섞어 쓰면 조용히 잘못 동작한다(예: `pageData`가 JSON이 아니라 `JsonNode`의 getter들을 직렬화한 이상한 객체로 나감).
- **엔티티**는 항상 Jackson 2 `com.fasterxml.jackson.databind.JsonNode`를 쓴다(`Invitation.pageData/langVariants`, `Template.defaultPageData`). Hibernate가 이걸 기준으로 매핑한다.
- **DTO**(HTTP 응답으로 나가는 것: `InvitationDto`, `GuestInvitationDto`)는 JsonNode를 직접 노출하지 않고, `@JsonRawValue` 붙은 `String` 필드에 `node.toString()`을 담아 내보낸다. `@JsonRawValue`는 `com.fasterxml.jackson.annotation` 패키지라 Jackson 2/3 어느 쪽에서도 동일하게 인식된다.
- **요청 바디**로 들어오는 JSON(`UpdatePageDataRequest.pageData`)은 Spring이 Jackson 3로 역직렬화하므로 타입이 `tools.jackson.databind.JsonNode`다. 서비스 레이어에서 `entityJsonMapper.readTree(node.toString())`로 Jackson 2 `JsonNode`로 변환한 뒤 엔티티에 반영한다(`InvitationService.toEntityJson()` 참고). `entityJsonMapper`는 `JacksonConfig`가 등록하는 Jackson 2 전용 빈이다.
- 새 엔드포인트에서 JSONB 필드를 다룰 때는 이 패턴(엔티티=Jackson2, DTO=`@JsonRawValue String`, 입력 변환=서비스 레이어)을 그대로 따른다.

**소프트 삭제**: 방명록처럼 "삭제 권한은 있지만 감사 추적이 필요한" 데이터는 물리 삭제 대신 `deletedAt(Instant, nullable)` 컬럼을 둔다. 무료 만료로 인한 청첩장 자체 삭제(15일 배치)는 예외로, 실제 레코드+R2 이미지를 지운다(개인정보 보관 최소화가 우선이라 소프트 삭제하지 않음).

**테이블명 예외**: `user`는 PostgreSQL 예약어라 엔티티명을 `Member`로 한다(기획서/아키텍처 문서의 "User"와 동일 개념).

## page_data 블록 스키마

청첩장 콘텐츠는 `Invitation.pageData`(JSONB) 하나에 블록 배열로 저장한다. 에디터와 하객뷰가 이 구조를 그대로 렌더링하므로 프론트/백엔드 모두 이 형태를 계약으로 삼는다.

```json
{
  "blocks": [
    {
      "id": "hero-1",
      "type": "hero",
      "order": 0,
      "visible": true,
      "content": { "groomName": "...", "brideName": "...", "weddingDate": "2026-11-01" },
      "style": { "bgColor": "#fff", "fontFamily": "noto-serif-kr", "padding": "lg" }
    }
  ]
}
```

- `type`: `hero | gallery | rsvp | map | account | guestbook | countdown` (아키텍처 문서 03번)
- 모든 블록은 `id`, `type`, `order`, `visible`, `content`, `style`을 공통으로 갖는다. `content`의 내부 구조만 타입별로 다르다.
- 백엔드는 이 구조를 검증하지 않고 그대로 저장/반환한다(자유도 우선). 검증이 필요해지면 프론트 Zod 스키마를 우선 기준으로 삼고 백엔드는 최소한의 sanity check만 추가한다.
- `Invitation.langVariants`도 같은 JSONB 방식. 블록별로 언어 코드(`ko`/`en`)를 키로 갖는 텍스트 오버라이드만 저장한다.

## 데이터 모델 (Phase 0 범위)

| 엔티티 | 테이블 | 핵심 필드 | 비고 |
|---|---|---|---|
| `Member` | `member` | provider(KAKAO/GOOGLE), providerId, email, name | providerId+provider로 유니크 |
| `Invitation` | `invitation` | member, slug(unique), template, weddingDate, plan(FREE/PAID), status(DRAFT/PUBLISHED/ARCHIVED), pageData(jsonb), langVariants(jsonb), expiresAt | slug는 서브도메인과 1:1 |
| `Template` | `template` | name, category, defaultPageData(jsonb), thumbnailUrl | 초기 4종 |
| `Rsvp` | `rsvp_response` | invitation, guestName, attending, guestCount, message | 로그인 없이 제출 |
| `GuestbookEntry` | `guestbook_entry` | invitation, author, message, deletedAt | 관리자 소프트 삭제 |

## 인증 (Kakao / Google 로그인)

`com.invi.api.account`에 있다. 세션이 아니라 JWT 기반 — SPA가 API를 호출할 때마다 `Authorization: Bearer <token>`을 보낸다.

- **로그인 흐름**: 프론트가 `GET /oauth2/authorization/{kakao|google}`로 이동 → Spring Security의 `oauth2Login()`이 제공자 로그인 페이지로 리다이렉트 → 콜백에서 `CustomOAuth2UserService`가 Kakao/Google의 서로 다른 유저 정보 응답을 공통 `(provider, providerId, email, name)`으로 매핑하고 `Member`를 find-or-create → `OAuth2LoginSuccessHandler`가 JWT를 발급해 `{FRONTEND_BASE_URL}/oauth/callback?token=...`로 리다이렉트 → 프론트의 `OAuthCallbackPage`가 토큰을 저장하고 `/api/auth/me`를 호출해 로그인 완료.
- **이후 모든 API 호출**: `JwtAuthenticationFilter`가 `Authorization` 헤더를 검증해서 `Authentication#getName()`에 `Member.id`(문자열 UUID)를 심는다. 컨트롤러는 `CurrentMember.requireId(authentication)`으로 꺼내 쓴다 — `Invitation` 생성 시 `memberId`를 요청 바디로 받지 않는 이유가 이거다(과거엔 받았지만 로그인이 생기면서 제거함).
- **공개 엔드포인트**(로그인 불필요, `SecurityConfig` 참고): `GET /api/templates/**`, `GET /api/invitations/slug/**`, `GET /api/invitations/slug-available`, RSVP 제출(`POST /api/invitations/*/rsvps`), 방명록 조회·작성(`GET`/`POST` `/api/invitations/*/guestbook`, 하객이 서로의 글을 읽어야 하는 기능이라 조회도 공개). RSVP 열람(`GET /api/invitations/*/rsvps`)은 기획서 5번대로 관리자 전용이라 로그인 필요. 그 외 `/api/**`는 전부 로그인 필요.
- **소유권 검증**: 로그인만으로는 부족하다 — `InvitationService`/`RsvpService`/`GuestbookService`가 각각 `getOwnedOrThrow` 스타일 체크로 "이 토큰의 memberId가 이 청첩장의 실제 소유자인가"를 한 번 더 확인한다. 아니면 `ForbiddenException`(403). 새 엔드포인트가 특정 청첩장에 종속된 데이터를 다루면 이 패턴을 그대로 따른다 — 로그인 여부만 확인하고 소유권을 빼먹는 게 제일 흔한 실수다.
- **인증 실패 응답**: `/api/**`로 시작하는 요청이 인증 없이 보호된 엔드포인트에 닿으면 Spring Security 기본값(로그인 페이지로 302 리다이렉트) 대신 401을 반환한다(`SecurityConfig`의 `exceptionHandling().defaultAuthenticationEntryPointFor`) — SPA가 JSON을 기대하는데 HTML 리다이렉트가 오면 fetch가 이상하게 깨지기 때문.
- **비밀값**: Kakao/Google client-id·secret과 JWT 서명 키는 `backend/src/main/resources/application-local.yml`에 있다 — **git에 안 올라간다**(`.gitignore` 참고). 새로 clone한 환경에서는 이 파일을 직접 만들어야 하고, JWT 시크릿이 없으면 `application.yml`의 개발용 기본값(`dev-only-insecure-default-...`)으로 폴백한다 — 운영 배포 전에는 반드시 실제 값으로 교체.
- **외부 콘솔 설정** (코드로 못 하는 부분, 사람이 직접): Kakao Developers/Google Cloud Console 양쪽에 리다이렉트 URI를 `http://localhost:8080/login/oauth2/code/{kakao|google}`로 등록해야 로그인이 동작한다. Kakao는 추가로 "카카오 로그인" 활성화 + 동의항목(닉네임, 카카오계정(이메일))을 켜둬야 `account_email` 스코프가 실제 이메일을 준다 — 동의 안 하면 `Member.email`은 `{providerId}@kakao.invi.local` 같은 대체값으로 채워진다.
- **로그인 없이 테스트하던 시절의 흔적**: `MemberController.dev` / `MemberSeeder`는 실제 로그인이 붙으면서 삭제했다. 새 임시 우회가 필요해지면 만들지 말고 로컬 계정으로 실제 로그인해서 테스트할 것.

## 프론트엔드 컨벤션 (frontend/)

- Vite + React + TypeScript + `react-router-dom`(`/`, `/oauth/callback`, `/e/:id`, `/i/:slug`)
- 로그인 상태는 `src/auth/AuthContext.tsx`(Context) + `src/auth/token.ts`(localStorage 저장)로 관리한다. `api/client.ts`의 모든 요청이 토큰이 있으면 자동으로 `Authorization` 헤더를 붙인다.
- 백엔드 API 베이스는 `http://localhost:8080`, 개발 중에는 직접 fetch(프록시 설정 없음 — CORS는 백엔드 `SecurityConfig`에서 `localhost:5173` 허용)

## 테스트

- 백엔드: `./gradlew test` (JUnit 5). 리포지토리 계층은 `@DataJpaTest`, 컨트롤러는 `@WebMvcTest` 우선 — 굳이 `@SpringBootTest` 풀 컨텍스트를 매번 띄우지 않는다.
- 로컬 DB는 `docker compose up -d`로 PostgreSQL을 먼저 띄운 뒤 백엔드를 실행한다.

## Git 컨벤션

**저장소**: [github.com/h-like/invi](https://github.com/h-like/invi) (private). 로컬 git 계정은 이 저장소에만 로컬로 설정돼 있다(`git config user.name/email`, 전역 설정 아님) — 다른 저장소를 새로 클론하면 다시 설정해야 한다.

**브랜치**
- `main`: 항상 빌드/컴파일이 되는 상태로 유지한다. Phase 0 스캐폴딩 이후로는 여기 직접 커밋하지 않는다.
- 기능 단위로 `phase-N/기능명` 형식의 브랜치를 판다. 예: `phase-1/invitation-editor`, `phase-1/rsvp-api`.
- 완료되면 PR을 올리고 `/code-review` 통과 후 머지한다. 1인 개발이라 리뷰해줄 사람은 없지만, PR을 남겨두면 변경 이력이 기능 단위로 정리되고 나중에 포트폴리오에서 보여주기도 좋다.

**커밋 메시지**: Conventional Commits 형식.
```
<type>(<scope>): <설명>
```
- type: `feat` `fix` `refactor` `test` `chore` `docs`
- scope: 모듈명(`invitation`, `rsvp`, `editor` 등) — 애매하면 생략
- 예: `feat(invitation): add slug uniqueness check`, `test(rsvp): cover guest-count validation`

**커밋 단위**: 항상 빌드가 되고 테스트가 통과하는 상태에서 커밋한다. "일단 커밋하고 나중에 고치기"는 하지 않는다 — 에이전트 루프가 자동으로 커밋할 때는 특히 이 원칙이 더 중요해진다(아래 참고).

**에이전트 루프와 git** — 아키텍처 문서 08번 "에이전트 루프 사용 원칙"의 연장:
- 로컬 커밋(`git add` + `git commit`)까지는 루프가 자동으로 남겨도 된다. 단, 테스트 통과·빌드 성공 상태에서만 — 원칙 3(검증 가능한 산출물)과 직결된다.
- `git push`, PR 생성/머지, `main`으로의 직접 반영, force-push는 루프가 자동으로 하지 않는다. 사람이 직접 트리거한다 — 원칙 2(되돌리기 어려운 액션은 자동 실행에서 제외)와 같은 논리다.
- 루프가 반복 작업 중에 남긴 커밋은 메시지에 그 사실을 남긴다. 예: `test(rsvp): tighten guest-count validation via TDD loop` — 나중에 이 커밋이 루프 산출물인지 직접 짠 건지 구분할 수 있게.

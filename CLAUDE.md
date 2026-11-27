# invi — 모바일 청첩장 서비스

1인 개발 · 포트폴리오 겸 실서비스. 하객 100명 이상 실사용 확보가 목표.
전체 아키텍처 배경은 프로젝트 기획서(구글 문서)와 별도로 정리한 아키텍처 문서를 참고 — 이 파일은 그중 "코드를 어떻게 짜는가"의 원칙만 다룬다. 단계별 절차는 아래 프로젝트 스킬과 `docs/`에 있다.

## 프로젝트 스킬 (`.claude/skills/`)

작업이 해당하면 코드를 쓰기 전에 먼저 연다.

| 스킬 | 언제 |
|---|---|
| `invitation-write-path` | 청첩장·RSVP·방명록·미디어를 읽고 쓰는 엔드포인트/서비스, 편집기 저장 경로 |
| `schema-change` | 엔티티·컬럼·제약·enum 변경, Flyway 마이그레이션 |
| `template-design-system` | 테마·파츠·토큰·폰트·봉투, page_data의 design/content 필드 |

## 레포 구조

```
invi/
  backend/    Spring Boot (Gradle) — REST API
  frontend/   Vite + React + TypeScript — 에디터 SPA + 하객뷰
  docker-compose.yml   로컬 개발용 PostgreSQL
```

## 백엔드 컨벤션 (backend/)

**패키지**: `com.invi.api.<domain>` 단위로 기능별 분리. domain 하나에 entity·repository·dto를 같이 둔다(계층별 패키지 아님). 아직 컨트롤러/서비스가 없는 도메인은 entity + repository까지만 있어도 된다.

- `common` — BaseEntity, JPA/Security 설정, 배포 가드, 아직 도메인이 정해지지 않은 공용 코드
- `account` — Member(신랑신부 계정), 인증
- `invitation` — Invitation(청첩장 본체), 하객 응답 뷰, 만료 배치
- `template` — Template(템플릿 프리셋)
- `rsvp` — Rsvp(참석 응답)
- `guestbook` — GuestbookEntry(방명록)
- `media` — MediaAsset, R2 presigned 업로드, 잔여 파일 정리
- `payment` — Phase 3에서 추가 예정, 아직 생성하지 않음

**엔티티**: 모두 `BaseEntity`를 상속해 `UUID id`·`createdAt`·`updatedAt`을 자동으로 갖는다(다시 선언하지 않음). UUID PK인 이유는 슬러그·초대 링크처럼 외부에 노출되는 리소스라 순차 ID를 숨겨야 해서다. Lombok은 `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`/정적 팩토리만 쓴다. `@Setter`/`@Data` 금지 — 상태 변경은 의도가 드러나는 메서드(`publish()` 등)로. DTO는 Java `record`.

**JSONB 컬럼**: `page_data`처럼 자유도 높은 구조는 POJO로 고정하지 않고 `JsonNode`를 그대로 `jsonb`에 저장한다(아키텍처 문서 03번 — 에디터와 하객뷰가 같은 JSON을 공유 렌더링하므로 백엔드가 스키마를 고정하면 안 된다).

**Jackson 2 / 3 경계 (조용히 깨지는 함정)**: Spring Boot 4의 HTTP 레이어는 Jackson 3(`tools.jackson.databind`), Hibernate JSON 매핑은 Jackson 2(`com.fasterxml.jackson.databind`)다. 클래스명이 같아서 섞으면 오류 없이 이상한 JSON이 나간다. 규칙: **엔티티 = Jackson 2 `JsonNode`, 응답 DTO = `@JsonRawValue String`, 요청 바디(Jackson 3) → 엔티티 변환은 서비스 레이어**(`InvitationService.toEntityJson()`, `JacksonConfig`의 `entityJsonMapper`). 상세는 `invitation-write-path` 스킬.

**삭제 정책**: "삭제 권한은 있지만 감사 추적이 필요한" 데이터(방명록)는 `deletedAt` 소프트 삭제. 예외로 무료 만료(예식일+10일 배치, `InvitationExpiryService`)는 개인정보 보관 최소화가 우선이라 레코드를 실제로 지우고 R2 이미지는 `MediaLifecycle`로 넘긴다. 기본 dry-run, 규칙은 `docs/INVITATION_EXPIRY.md`.

**스키마 (Flyway)**: 스키마는 `db/migration`의 Flyway 마이그레이션으로만 바꾸고 Hibernate는 `ddl-auto: validate`만 한다. 엔티티 변경과 같은 커밋에 다음 번호 `V{n}__설명.sql`, 이미 적용된 파일은 수정 금지. **Java enum에 값을 늘리거나 줄이면 그 컬럼의 CHECK 제약을 새 마이그레이션에서 다시 만든다** — 안 하면 새 값 저장이 운영에서 제약 위반(500)으로 실패한다. `baseline-on-migrate`는 모든 프로필에서 꺼져 있다 — `ddl-auto` 시절 DB의 일회성 baseline 절차는 `docs/HTTPS_TEST_DEPLOY.md`(`FlywayLegacyBaselineTest`). 변경 절차는 `schema-change` 스킬.

**로컬 DB가 Flyway 오류로 기동을 거절할 때** (`bootRun`·`./gradlew test`가 docker의 같은 DB를 쓴다): "non-empty schema ... without a schema history table"이면 Flyway 이전 `ddl-auto`로 만든 DB다. 데이터가 필요 없으면 `docker compose down -v && docker compose up -d`(볼륨 삭제), 남기려면 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`로 한 번 기동한 뒤 변수 없이 다시 띄운다. 이 변수는 **한 번 쓰고 반드시 지운다** — 켜 둔 채면 잘못 가리킨 다른 비어 있지 않은 DB도 V1로 표시하고 V2를 실행해 버린다. Flyway 이전에 딴 브랜치는 `ddl-auto: update`로 마이그레이션 밖 변경을 남기므로 main을 받은 뒤 실행한다.

**저장 충돌 (낙관적 잠금)**: `Invitation.version`(`@Version`)은 청첩장이 바뀔 때마다 오른다(페이지 저장·예식일·공개·비공개). `PUT /page-data`는 편집기가 마지막으로 받은 `version`을 보내고, 다르면 409 + `code: STALE_VERSION` — 다른 탭·기기의 저장을 덮어쓰지 않기 위해서다. 쓰기는 미디어 잠금 안에서 검사하고 `flushedDto()`로 flush 후 응답한다. 편집기는 같은 탭의 쓰기를 `createInvitationPersistence` 한 가드로 하나씩 보내고, 응답이 끊긴 경우 "version이 정확히 1 올랐고 이 탭의 쓰기가 보일 때만" 성공으로 본다(`editor/persistence.ts`). 충돌 시 초안은 유지하고 "최신 내용 새 탭에서 보기"·"최신 내용으로 바꾸기"만 제공한다(강제 덮어쓰기는 MVP 제외). 쓰기별 세부 규칙은 `invitation-write-path` 스킬.

**확인 링크와 비공개 전환**: 소유자가 `POST /{id}/review-link`로 추측 불가 토큰(`reviewToken`)을 만들어 `/r/{token}`으로 공유한다. 상태와 무관하게 저장본을 하객 화면 그대로(숨긴 블록 제외) 보여주되 RSVP·방명록은 받지 않는다. `DELETE`하면 즉시 404, 다시 만들면 새 주소. 토큰은 `@Version`을 올리지 않도록 벌크 업데이트(`updateReviewToken`)로만 쓴다. 공개 후 비공개로 되돌려도 응답·방명록은 보존된다.

**테이블명 예외**: `user`는 PostgreSQL 예약어라 엔티티명을 `Member`로 한다(기획서의 "User"와 동일 개념).

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

- `type`: `hero | gallery | rsvp | map | account | guestbook | countdown`. 모든 블록이 `id`·`type`·`order`·`visible`·`content`·`style`을 갖고 `content`만 타입별로 다르다.
- 백엔드는 구조를 검증하지 않고 그대로 저장/반환한다. 계약의 기준은 프론트 `src/api/types.ts`와 `src/editor/model.ts`의 정규화 함수다(런타임 스키마 라이브러리는 아직 없음 — 필요해지면 프론트에 먼저 만들고 백엔드는 최소 sanity check만).
- **하객 응답은 숨긴 블록(`visible !== true`)을 서버에서 뺀다** — 공개 JSON은 누구나 읽으므로 렌더러에서만 거르면 숨긴 계좌·혼주 정보가 샌다. `GuestPageView` 한 곳에서 공개 청첩장·확인 링크·링크 미리보기·`langVariants`에 같은 규칙을 쓴다. 숨긴 RSVP·방명록은 신규 제출 403, 기존 데이터 보존.
- **편집기는 모르는 필드를 지우지 않는다**(이전 버전 탭이 새 설정을 날리지 않게). `design.look`은 보안상 `sanitizeLook` 결과만 남긴다.
- `Invitation.langVariants`: 블록별로 언어 코드(`ko`/`en`)를 키로 갖는 텍스트 오버라이드만 저장.
- `hero.content` 선택 필드(혼주 `groom/brideFather·Mother·Relation`, 실링 이니셜 `monogram`)의 읽기·정규화 규칙은 `frontend/src/blocks/heroDetails.ts` 한 곳.

## 데이터 모델

| 엔티티 | 테이블 | 핵심 필드 | 비고 |
|---|---|---|---|
| `Member` | `member` | provider(LOCAL/KAKAO/GOOGLE), providerId, email, name, passwordHash | providerId+provider 유니크. `LOCAL`은 providerId가 이메일이고 `passwordHash` 사용, 소셜은 비어 있음 |
| `Invitation` | `invitation` | member, slug(unique), template, weddingDate, plan(FREE/PAID), status(DRAFT/PUBLISHED/ARCHIVED), pageData(jsonb), langVariants(jsonb), expiresAt, version, reviewToken(unique, nullable) | slug는 서브도메인과 1:1. `expiresAt`은 만료 배치가 쓰지 않는다 |
| `Template` | `template` | code(unique), name, category, defaultPageData(jsonb), thumbnailUrl | 원본은 `frontend/src/editor/themes.json` 하나(에디터와 `TemplateSeeder`가 공유). `code` = `category` = `design.theme`. 부팅마다 code 기준 생성·동기화, 목록에서 빠진 행도 지우지 않음 |
| `Rsvp` | `rsvp_response` | invitation, guestName, attending, guestCount, message, side(GROOM/BRIDE), meal(YES/NO/UNDECIDED) | 로그인 없이 제출. side·meal nullable(이전 클라이언트 호환), 불참이면 meal 저장 안 함 |
| `GuestbookEntry` | `guestbook_entry` | invitation, author, message, deletedAt | 관리자 소프트 삭제 |
| `MediaAsset` | `media_asset` | invitation, r2Key, type(GALLERY/OG/COVER), sortOrder | 업로드 attach 시 생성. 청첩장 만료 시 실제 삭제 |
| `TrackedMediaObject` | `tracked_media_object` | bucket, r2Key, state(ACTIVE/DELETING/DELETED), unreferencedSince, safeAfter, objectEtag, deletedAt | bucket+r2Key 유니크. R2 잔여 파일 정리용(`docs/MEDIA_CLEANUP.md`) |

목록 조회에는 항상 끝까지 정해지는 `ORDER BY`를 둔다 — 없으면 PostgreSQL이 순서를 보장하지 않는다. 하객 응답(RSVP·방명록)은 `createdAt desc, id desc`로 읽고 DTO에 `createdAt`을 담는다. 사용자가 정한 순서가 있는 목록은 그 순서를 따른다(미디어는 `sortOrder`).

## 인증 (JWT + Kakao / Google / 이메일)

`com.invi.api.account`. 세션이 아니라 JWT — SPA가 매 요청 `Authorization: Bearer <token>`을 보낸다.

- **소셜 로그인 흐름**: `GET /oauth2/authorization/{kakao|google}` → `CustomOAuth2UserService`가 제공자별 응답을 `(provider, providerId, email, name)`으로 매핑해 `Member` find-or-create → `OAuth2LoginSuccessHandler`가 JWT를 발급해 `{FRONTEND_BASE_URL}/oauth/callback?token=...`로 → `OAuthCallbackPage`가 저장 후 `/api/auth/me`.
- **요청 인증**: `JwtAuthenticationFilter`가 `Authentication#getName()`에 `Member.id`를 심고, 컨트롤러는 `CurrentMember.requireId(authentication)`으로 꺼낸다. memberId를 요청 바디로 받지 않는다.
- **소유권**: 로그인만으로는 부족하다. 청첩장에 종속된 데이터는 서비스가 `getOwnedOrThrow`로 소유자인지 확인하고 아니면 403 — 이걸 빼먹는 게 제일 흔한 실수다.
- **공개 엔드포인트**(`SecurityConfig`): `GET /api/templates/**`, `GET /api/invitations/slug/**`, `GET /api/invitations/slug-available`, `GET /api/invitations/review/*`(토큰이 곧 권한), `POST /api/invitations/*/rsvps`, `GET`/`POST /api/invitations/*/guestbook`(게시된 청첩장에서 방명록이 보일 때만 글을 준다). 소유자용 방명록 전체 조회는 `GET .../guestbook/manage`, RSVP 열람 `GET .../rsvps`는 로그인 필요. 그 외 `/api/**`는 전부 로그인 필요. 공개 쓰기는 `GuestWriteRateLimiter`로 한도(429, `docs/GUEST_RATE_LIMIT.md`).
- **인증 실패**: `/api/**`는 로그인 페이지 302 대신 401 JSON(SPA fetch가 HTML 리다이렉트에 깨지지 않게).
- **비밀값**: OAuth client-id·secret, JWT 키, R2 키는 gitignored `backend/src/main/resources/application-local.yml`. 새 clone에서는 직접 만든다. JWT 시크릿이 없으면 개발용 기본값으로 폴백 — 운영에서는 반드시 실제 값.
- **외부 콘솔 설정(사람이 직접)**: Kakao/Google 콘솔에 리다이렉트 URI `http://localhost:8080/login/oauth2/code/{kakao|google}` 등록. Kakao는 "카카오 로그인" 활성화 + 동의항목(닉네임, 이메일)을 켜야 실제 이메일이 온다(안 켜면 `{providerId}@kakao.invi.local`).
- 로그인 우회용 dev 엔드포인트/시더는 만들지 않는다 — 로컬 계정으로 실제 로그인해서 테스트한다.

## 이미지 업로드 (Cloudflare R2)

`com.invi.api.media`. 백엔드를 거치지 않는 presigned URL 방식(아키텍처 문서 02번).

- **흐름**: 프론트가 최적화한 파일 정보로 `POST /api/invitations/{id}/media/presign` → 소유권 확인 후 `invitations/{invitationId}/{uuid}.{ext}` presigned PUT(5분) 발급 → 프론트가 R2에 **직접** PUT → `POST /api/invitations/{id}/media`로 attach해 `MediaAsset` 생성.
- **R2 미설정 시**: `invi.r2.account-id`가 비면 S3 빈을 만들지 않고, 업로드 시점에만 503(`StorageUnavailableException`) — CI·새 clone에서도 앱이 기동해야 하므로.
- **외부 설정(사람이 직접)**: R2 버킷·API 토큰 발급, **버킷 CORS에 프론트 오리진의 `PUT` 허용**(배포 도메인도), `public-base-url` 결정.
- 대표 사진·갤러리의 형식·크기·최적화·검증 규칙은 `docs/PHOTO_UPLOAD.md`.
- **잔여 파일 정리**: `tracked_media_object`로 추적하고 모든 청첩장 참조를 보호하며 기본 7일 유예, 실제 삭제는 기본 꺼짐. **이미지 참조를 쓰는 새 경로는 트랜잭션 시작 시 `MediaLifecycle.lock()`을 잡고 `referencesChanged`/`assertUsable`을 거친다.** 상세는 `docs/MEDIA_CLEANUP.md`.

## 배포 (https-test / prod)

- `Dockerfile`은 React 정적 빌드와 boot JAR만 담아 비root JRE21로 실행한다. 기본은 https-test, `APP_MODE=prod`로 운영 빌드. 개발 서버/bootRun/포트 포워딩은 외부에 공개하지 않는다.
- 배포 프로필은 안전 조건을 어기면 시작을 거절한다. 두 프로필 공통(`DeploymentChecks`와 각 가드): `local` 프로필, 로컬 버킷 `invi-media`(`application.yml`의 `bucket` 기본값), TLS 검증 없는 DB, 약한 JWT, R2 정리 켜짐, actuator 상세 노출. https-test(`HttpsTestDeploymentGuard`)는 가입·소셜을 닫고 만료 삭제·R2 정리를 dry-run으로 고정한다. prod(`ProdDeploymentGuard`)는 이름에 `test`가 든 DB·버킷과 `https-test` 프로필을 거절하고, 가입·소셜은 `true`/`false` 형식만 검사한다(켜는 건 `application-prod.yml`). 시작 거절은 가드를 약하게 하지 말고 설정을 고친다.
- VITE 변수나 Docker ARG에 비밀값을 넣지 않는다. 문의처는 빌드 인자 `VITE_CONTACT_EMAIL`(저장소에 넣지 않음).
- 하객 링크 직접 접속은 index.html로 보내되 API/누락 asset 오류는 HTML로 삼키지 않는다. 준비 확인은 `/actuator/health/readiness`.
- 운영: 계정당 청첩장 3개(`InvitationQuota` — R2가 유일하게 과금될 수 있어서), 이메일 가입은 서버에서도 약관 동의(`agreedToPolicies`) 요구. **무료 플랜만 쓰고 비용이 생길 수 있는 설정은 먼저 묻는다.**
- **처리방침·약관(`/privacy`, `/terms`)은 실제 수집 항목·보관 기간·위탁 업체를 적은 문서다.** 수집 필드·삭제 규칙·호스팅을 바꾸면 문구와 `site.ts`의 `POLICY_EFFECTIVE_DATE`를 같이 고친다.
- 코드/이미지 빌드만으로 배포 완료라고 하지 않는다. 절차는 `docs/HTTPS_TEST_DEPLOY.md`, `docs/PROD_DEPLOY.md`.

## 프론트엔드 컨벤션 (frontend/)

- Vite + React + TypeScript + `react-router-dom` Data Router(`src/App.tsx`). 라우트를 추가·삭제하면 이 목록도 고친다.
  - 공개: `/`, `/templates`, `/templates/:id`, `/login`, `/oauth/callback`, `/privacy`, `/terms`
  - 로그인 필요(`RequireAuth`): `/create`, `/invitations`, `/e/:id`(편집기), `/e/:id/guests`(응답 관리)
  - 하객용(공통 레이아웃 밖): `/i/:slug`(공개 청첩장), `/r/:token`(확인 링크)
- **하객 기기에서 제3자 요청이 나가지 않게 한다.** 웹폰트는 npm 패키지로 자체 호스팅(CDN 금지)하고 `blocks/fonts.ts`가 쓰는 서체만 지연 로딩한다.
- **템플릿의 생김새는 데이터다.** 새 테마는 `themes.json`만으로, 새 디자인 요소는 파츠 슬롯 값 + `part-<slot>-<value>` CSS로 추가한다(테마 이름 셀렉터 금지). `parts`/`tokens`/`fonts`는 `design` 밖에 둬서 기존 청첩장도 템플릿 개선을 받는다. 사용자 꾸미기(`design.look`)는 `sanitizeLook` 허용값만 받는다. 절차는 `template-design-system` 스킬.
- 로그인 상태는 `src/auth/AuthContext.tsx` + `src/auth/token.ts`(localStorage). `api/client.ts`가 토큰이 있으면 `Authorization`을 자동으로 붙인다.
- API 베이스는 `VITE_API_BASE_URL`(기본 `http://localhost:8080`, `api/baseUrl.ts`가 Origin만 허용). 프록시 없음. 백엔드 CORS는 `invi.frontend.base-url`의 정확한 Origin만 허용. 휴대폰 실기기 테스트는 gitignored `.env.mobile.local` + `--mode mobile`(`docs/MOBILE_PHOTO_CHECK.md`).

## 테스트

- 백엔드: `./gradlew test` (JUnit 5). 리포지토리는 `@DataJpaTest`, 컨트롤러는 `@WebMvcTest` 우선 — `@SpringBootTest` 풀 컨텍스트를 매번 띄우지 않는다. 로컬 DB는 `docker compose up -d`로 먼저 띄운다.
- 프론트: `npm test`는 `package.json`의 `scripts.test`에 **나열된 파일만** 실행한다 — 새 `*.test.ts`는 목록에 추가한다. 타입 체크는 `npm run build`.

## Git 컨벤션

**저장소**: [github.com/h-like/invi](https://github.com/h-like/invi) (public — 비밀값·실제 개인정보 커밋 금지). 로컬 git 계정은 이 저장소에만 로컬로 설정돼 있다(전역 아님).

**브랜치**
- `main`: 항상 빌드되는 상태. 직접 커밋하지 않는다.
- 기능 단위로 `phase-N/기능명` 브랜치(예: `phase-1/rsvp-api`). 예외: Claude 앱이 만든 `claude/...` 브랜치는 이름을 바꾸지 않고 PR 제목으로 기능을 드러낸다.
- 완료되면 PR → `/code-review` 통과 후 머지. 1인 개발이지만 기능 단위 이력과 포트폴리오를 위해 PR을 남긴다.

**커밋 메시지**: Conventional Commits `<type>(<scope>): <설명>`. type은 `feat` `fix` `refactor` `test` `chore` `docs`, scope는 모듈명(`invitation`, `rsvp`, `editor` 등, 애매하면 생략). 예: `feat(invitation): add slug uniqueness check`.

**커밋 단위**: 항상 빌드·테스트가 통과하는 상태에서 커밋한다. "일단 커밋하고 나중에 고치기" 금지.

**에이전트 루프와 git** (아키텍처 문서 08번):
- 로컬 커밋은 루프가 자동으로 해도 된다 — 테스트 통과·빌드 성공 상태에서만.
- 기능 브랜치 `git push`도 루프가 해도 된다(`wip:` 커밋 제외). PR 생성/머지, `main` 반영, force-push는 루프가 하지 않는다(되돌리기 어려운 액션). `main`과 force-push는 GitHub ruleset으로도 막혀 있다.
- 루프가 남긴 커밋은 메시지에 표시한다. 예: `test(rsvp): tighten guest-count validation via TDD loop`.

자세한 절차(브랜치 생성·push 시점, `wip:` 커밋, GitHub 보호 설정)는 [docs/GIT.md](docs/GIT.md).

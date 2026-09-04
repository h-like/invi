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

## 프론트엔드 컨벤션 (frontend/)

- Vite + React + TypeScript, 아직 라우터/상태관리 라이브러리는 도입하지 않음(Phase 1에서 필요해지면 추가)
- 백엔드 API 베이스는 `http://localhost:8080`, 개발 중에는 직접 fetch(프록시 설정 없음 — CORS는 백엔드 `SecurityConfig`에서 `localhost:5173` 허용)

## 테스트

- 백엔드: `./gradlew test` (JUnit 5). 리포지토리 계층은 `@DataJpaTest`, 컨트롤러는 `@WebMvcTest` 우선 — 굳이 `@SpringBootTest` 풀 컨텍스트를 매번 띄우지 않는다.
- 로컬 DB는 `docker compose up -d`로 PostgreSQL을 먼저 띄운 뒤 백엔드를 실행한다.

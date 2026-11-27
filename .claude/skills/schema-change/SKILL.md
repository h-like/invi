---
name: schema-change
description: JPA 엔티티의 필드·컬럼·제약·enum 값을 추가/변경/삭제하거나, 새 엔티티(테이블)를 만들거나, Flyway 마이그레이션(backend/src/main/resources/db/migration)을 작성할 때 사용. 마이그레이션 번호·CHECK 제약·삭제 정책(소프트 삭제 vs 만료 시 실제 삭제)·개인정보 처리방침 연동·검증까지 한 번에 맞추는 절차.
---

# 스키마 변경 절차

스키마의 기준은 Flyway 마이그레이션이다. Hibernate는 `ddl-auto: validate`로 엔티티와 DB가 일치하는지만 본다. 엔티티만 고치고 마이그레이션을 빼먹으면 로컬은 운 좋게 돌아도 운영(Render + Neon)은 기동을 거절한다.

## 0. 먼저 결정한다 (코드 쓰기 전에)

1. **어떤 데이터인가?**
   - 개인정보(이름·연락처·메시지·IP 등)인가? → 6번 필수.
   - 하객에게 노출되는가? → `invitation-write-path` 스킬 5번(숨긴 블록·공개 응답)도 따른다.
2. **page_data(JSONB)에 넣을까, 컬럼으로 뺄까?**
   - 에디터·하객뷰가 렌더링하는 콘텐츠/디자인이면 **JSONB에 둔다** — 마이그레이션 없음, 프론트 `api/types.ts`·`editor/model.ts`가 계약. 이 스킬 대신 `template-design-system` 스킬로.
   - 서버가 조회·정렬·유니크·권한 판단에 쓰는 값이면 컬럼.
3. **삭제 정책**
   - 소유자가 지울 수 있지만 감사 추적이 필요 → `deletedAt Instant` (nullable) 소프트 삭제. 조회 쿼리에서 `deletedAt is null` 조건 확인.
   - 청첩장에 딸린 데이터 → **무료 만료 배치가 청첩장과 함께 실제로 지워야 한다**: `InvitationExpiryTransactions.deleteIfExpired`에 `deleteAllByInvitationId` 추가(FK에 `ON DELETE CASCADE`가 없어 빠뜨리면 만료 삭제가 FK 오류로 실패한다). 규칙은 `docs/INVITATION_EXPIRY.md`.
   - R2 키를 담는 컬럼 → `MediaReferences`가 그 참조를 보호하게 한다(`docs/MEDIA_CLEANUP.md`).
4. **기존 행은?** 새 NOT NULL 컬럼은 기본값이나 백필 후 제약 추가. 이전 클라이언트가 값을 안 보낼 수 있으면 nullable로(`Rsvp.side`/`meal` 선례).

## 1. 엔티티

- `BaseEntity` 상속(`id`/`createdAt`/`updatedAt` 재선언 금지). 패키지는 `com.invi.api.<domain>`에 entity·repository·dto 함께.
- `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Builder`/정적 팩토리. `@Setter`/`@Data` 금지 — 상태 변경은 의도가 드러나는 메서드.
- enum은 `@Enumerated(EnumType.STRING)`.
- JSONB: Jackson 2 `com.fasterxml.jackson.databind.JsonNode` + `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")`.
- `Invitation`에 필드를 추가하면 그 필드 변경이 `@Version`을 올린다는 점을 고려한다. 올리면 안 되면 벌크 업데이트로만 쓴다(`InvitationRepository.updateReviewToken` 선례).
- 테이블명이 PostgreSQL 예약어면 피한다(`user` → `Member`).

## 2. 마이그레이션 파일

- 위치: `backend/src/main/resources/db/migration/`. 현재 마지막 번호를 `ls`로 확인하고 **다음 번호** `V{n}__snake_case_설명.sql`. 엔티티 변경과 **같은 커밋**에 넣는다.
- **이미 적용된 파일(V1…기존 마지막)은 절대 수정하지 않는다.** 체크섬이 바뀌어 운영 DB가 기동을 거절한다. 잘못됐으면 새 번호로 고친다.
- 파일 맨 위에 "왜 바꾸는지" 주석 몇 줄(V4 참고).
- 제약 이름을 명시한다: `uk_<table>_<column>`, `<table>_<column>_check`, FK도 이름 지정.
- **enum 값을 늘리거나 줄이면** 그 컬럼의 CHECK 제약을 다시 만든다:
  ```sql
  ALTER TABLE rsvp_response DROP CONSTRAINT rsvp_response_meal_check;
  ALTER TABLE rsvp_response ADD CONSTRAINT rsvp_response_meal_check
      CHECK (meal IN ('YES', 'NO', 'UNDECIDED', 'NEW_VALUE'));
  ```
  기존 CHECK 이름은 `V1__baseline.sql`/`V2`에서 확인한다.
- 조회 패턴에 맞는 인덱스가 필요하면 함께 추가한다(예: `invitation_id` + `created_at desc`).
- 운영 DB(Neon)에 바로 적용된다 — 큰 테이블 잠금·데이터 손실이 생길 수 있는 `DROP COLUMN`/타입 변경은 사용자에게 먼저 확인한다.

## 3. 리포지토리·조회

- 목록 조회에는 끝까지 정해지는 `ORDER BY`를 둔다(PostgreSQL은 순서를 보장하지 않는다). 하객 응답류는 `createdAt desc, id desc` + DTO에 `createdAt`, 사용자가 정한 순서가 있으면 그 컬럼(미디어 `sortOrder`).
- 소프트 삭제 엔티티는 조회 메서드 이름/쿼리에 `DeletedAtIsNull` 포함.

## 4. DTO·API

- DTO는 Java `record`. 엔티티를 그대로 응답하지 않는다.
- 새 필드를 API로 받거나 내보내면 `invitation-write-path` 스킬의 체크리스트를 따른다.
- 프론트 `src/api/types.ts`의 타입도 같이 고친다.

## 5. 문서

- CLAUDE.md "데이터 모델" 표의 해당 행(핵심 필드·비고)을 고친다. 버전 V{n}을 비고에 적으면 추적이 쉽다.

## 6. 개인정보 처리방침 연동 (운영 중 — 생략 불가)

다음 중 하나라도 해당하면 `/privacy`(`PrivacyPage`)와 `/terms`(`TermsPage`)의 문구를 실제와 맞추고 `frontend/src/site.ts`의 `POLICY_EFFECTIVE_DATE`를 바꾼다:
- 수집 항목 추가/삭제(하객 입력 필드 포함, 하객 화면 `GuestPrivacyNote`도 확인)
- 보관 기간·삭제 규칙 변경(만료 배치, 소프트 삭제 보존 기간)
- 새 외부 위탁/호스팅 업체
문구 변경은 사용자 확인을 받은 뒤 반영한다(법적 고지).

## 7. 검증

```powershell
docker compose up -d
```
```powershell
cd backend; ./gradlew test
```
- [ ] 테스트 컨텍스트 기동 시 Flyway 적용 + `validate` 통과(엔티티↔마이그레이션 불일치가 여기서 잡힌다).
- [ ] `@DataJpaTest`로 새 제약(유니크·CHECK·NOT NULL)과 정렬 조회 테스트.
- [ ] 청첩장 종속 테이블이면 만료 삭제 테스트(`InvitationExpiryTransactionsIntegrationTest`)에 그 테이블 행이 지워지는 경우 추가.
- [ ] `FlywayLegacyBaselineTest`가 여전히 통과(V2 이후 변경이 legacy baseline 경로와 충돌하지 않는지).
- [ ] 로컬 DB가 "non-empty schema without a schema history table"로 거절하면 CLAUDE.md의 "로컬 DB가 Flyway 오류로 기동을 거절할 때" 절차. `docker compose down -v`는 로컬 데이터를 지우므로 사용자에게 먼저 묻는다.
- [ ] 프론트 타입을 바꿨으면 `cd frontend; npm run build`.

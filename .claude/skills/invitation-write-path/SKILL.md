---
name: invitation-write-path
description: 청첩장(Invitation)이나 그에 딸린 데이터(page_data·langVariants·예식일·공개 상태·확인 링크·RSVP·방명록·미디어)를 읽거나 쓰는 백엔드 엔드포인트/서비스 메서드를 새로 만들거나 고칠 때, 또는 편집기에서 그 API를 호출하는 저장 경로를 바꿀 때 사용. 소유권 검사, Jackson 2/3 경계, 낙관적 잠금(version), 미디어 잠금, 하객 응답의 숨긴 블록 제거, 편집기 쓰기 가드를 빠짐없이 맞추는 체크리스트.
---

# 청첩장 쓰기 경로 체크리스트

청첩장 엔드포인트 하나는 백엔드·프론트 여러 층의 규칙에 동시에 걸린다. 하나라도 빠지면 컴파일·대부분의 테스트는 통과하면서 **조용히** 깨진다(남의 청첩장 수정, 숨긴 계좌 노출, 다른 탭 저장 덮어쓰기, R2 이미지 조기 삭제). 코드를 쓰기 전에 아래 결정을 먼저 내리고, 끝나면 맨 아래 검증 목록을 확인한다.

기준 구현: `backend/src/main/java/com/invi/api/invitation/InvitationService.java`
(`updatePageData`, `updateWeddingDate`, `publish`, `unpublish`, `issueReviewLink`).

## 0. 먼저 분류한다

| 질문 | 답에 따라 |
|---|---|
| 누가 부르나? 소유자 / 하객(익명) / 토큰 소지자 | 1번(소유권) 또는 5번(공개 응답) |
| 청첩장 엔티티 필드가 바뀌나? | 바뀌면 `@Version`이 오른다 → 3번 전체 적용 |
| JSONB(`pageData`·`langVariants`)를 받거나 내보내나? | 2번 |
| 이미지(R2 키) 참조가 늘거나 줄거나, 쓰일 수 있게 되나? | 4번 |
| 응답이 하객/외부에 나가나? | 5번 |
| 스키마가 바뀌나? | `schema-change` 스킬도 같이 따른다 |

## 1. 인증과 소유권 (소유자용 엔드포인트)

- 컨트롤러는 `CurrentMember.requireId(authentication)`으로 memberId를 꺼낸다. **memberId를 요청 바디/파라미터로 받지 않는다.**
- 서비스는 반드시 `getOwnedOrThrow(memberId, id)`를 거친다 → 아니면 `ForbiddenException`(403). 로그인만 확인하고 소유권을 빼먹는 게 이 프로젝트에서 제일 흔한 실수다.
- RSVP·방명록·미디어처럼 청첩장에 딸린 데이터도 같다(`RsvpService`/`GuestbookService`/`MediaService`의 같은 패턴). 하위 리소스 id만 받았다면 그 리소스가 **그 청첩장에 속하는지**까지 확인한다.
- 공개(비로그인) 엔드포인트를 추가하면 `common/SecurityConfig`의 `permitAll` 목록에 정확한 메서드+경로로만 연다. 공개 **쓰기**면 `GuestWriteRateLimiter`를 적용한다(`docs/GUEST_RATE_LIMIT.md`).
- `/api/**` 인증 실패는 401(JSON)이어야 한다 — 기존 entry point 설정을 건드리지 않는다.

## 2. JSONB와 Jackson 2/3 경계

같은 클래스명(`JsonNode`, `ObjectMapper`)이 두 패키지에 있다. import를 하나씩 확인한다.

| 위치 | 타입 |
|---|---|
| 엔티티 필드 | Jackson 2 `com.fasterxml.jackson.databind.JsonNode` |
| 요청 DTO(record) | Jackson 3 `tools.jackson.databind.JsonNode` (Spring HTTP가 Jackson 3) |
| 응답 DTO | `@JsonRawValue String` 에 `node.toString()` (`InvitationDto`, `GuestInvitationDto`) |

- 요청 → 엔티티 변환은 **서비스 레이어**에서 `entityJsonMapper.readTree(node.toString())` (`InvitationService.toEntityJson()`을 재사용하거나 같은 방식). `entityJsonMapper`는 `JacksonConfig`의 Jackson 2 빈이다.
- 응답 DTO에 `JsonNode`를 그대로 넣지 않는다 — Jackson 3가 Jackson 2 노드의 getter를 직렬화해 이상한 객체가 나간다.
- 백엔드는 page_data 구조를 검증하지 않는다(계약 기준은 프론트 `api/types.ts` + `editor/model.ts`). 서버에서 블록 내용을 해석해야 하면 최소한으로, 모르는 필드는 건드리지 않는다.

## 3. 낙관적 잠금 (`Invitation.version`)

엔티티를 바꾸는 모든 쓰기는 `@Version`을 올리고, 편집기의 다른 탭은 다음 저장에서 409를 받는다. 그래서:

- **엔티티 상태 변경은 의도가 드러나는 메서드로**(`publish()`, `updateWeddingDate()` …). `@Setter` 금지.
- 클라이언트가 덮어쓸 수 있는 내용을 보내는 쓰기라면 `expectedVersion`을 받아 미디어 잠금 **안에서** 비교하고, 다르면 `StaleVersionException`(409, `code: STALE_VERSION`).
- 응답은 `flushedDto(invitation)` — flush 전에 DTO를 만들면 옛 version이 나가서 편집기의 다음 저장이 늘 충돌한다.
- **버전을 올리면 안 되는 부수 데이터**(예: `reviewToken`)는 엔티티를 고치지 말고 리포지토리 벌크 `@Modifying` 업데이트로 쓴다(`InvitationRepository.updateReviewToken`). `@OptimisticLock(excluded = true)`는 이 환경에서 효과가 없었다.
- 멱등이어야 하는 전환(예: 이미 DRAFT인데 `unpublish`)은 변화가 없으면 version도 안 오르게 엔티티 메서드에서 조기 반환한다.

## 4. 미디어 잠금과 참조 추적

R2 정리 작업과 경합하지 않도록, 청첩장 쓰기는 모두 트랜잭션 시작 시 공통 잠금을 잡는다.

```java
@Transactional
public InvitationDto something(UUID memberId, UUID id, ...) {
    lifecycle.lock();                              // 맨 먼저 — 소유권 조회보다도 앞
    Invitation invitation = getOwnedOrThrow(memberId, id);
    ...
}
```

- 이미지 참조가 바뀌면: `before = MediaReferences.ofInvitation(invitation)`, `after = MediaReferences.extract(next…)`(+ `langVariants` 키 합치기) → `lifecycle.referencesChanged(before, after)` → 그 다음 엔티티 변경.
- 이미지를 "쓰이는 상태"로 만드는 전환(공개 등)이면 `lifecycle.assertUsable(keys)`.
- 새 참조 위치(새 필드·새 JSON 경로)를 만들면 `MediaReferences`가 그걸 찾는지 확인한다 — 못 찾으면 7일 뒤 정리 작업이 사용 중인 이미지를 지운다. 자세한 건 `docs/MEDIA_CLEANUP.md`.

## 5. 하객·외부로 나가는 응답

공개 JSON은 누구나 읽는다. 렌더러에서 숨기는 것만으로는 숨긴 계좌·혼주 정보가 샌다.

- page_data는 `GuestPageView.visibleOnly(pageData)`, langVariants는 `GuestPageView.langVariantsFor(langVariants, pageData)`를 거쳐 내보낸다. 공개 청첩장·확인 링크·링크 미리보기(`LinkPreview`)가 모두 이 한 곳을 쓴다 — 새 경로도 여기를 쓰고 필터를 따로 만들지 않는다.
- 블록 종속 하객 쓰기(RSVP·방명록 류)는 두 단계로 막는다(`RsvpService` 참고):
  - 공개되지 않은 청첩장은 없는 id와 똑같이 **404**(`findById(...).filter(PUBLISHED)` → `NotFoundException`). 초안이 있다는 사실도 드러내지 않기 위해서다.
  - 공개됐지만 그 블록이 숨겨져 있으면 **403**(`GuestPageView.hasVisibleBlock(pageData, "<type>")`가 false → `ForbiddenException`). 기존 데이터는 지우지 않는다. 하객용 조회는 빈 목록.
  - 접속 한도(`rateLimiter.acquire`)는 이 조회 **뒤**에 건다 — 없는 id로 한도 항목이 생기지 않게.
- 확인 링크(`/review/*`)는 상태와 무관하게 보여주지만 쓰기는 받지 않는다.
- 저장본·편집용(소유자) 응답은 필터하지 않는다.
- 응답 DTO에 member 정보·내부 id·`reviewToken`이 섞이지 않았는지 본다.
- 하객 응답에 개인정보 필드가 새로 들어가면 `/privacy` 문구와 `site.ts` 시행일 점검(`schema-change` 스킬 참고).

## 6. 편집기 쪽 (frontend/src/editor/persistence.ts)

- version을 올리는 요청은 전부 `createInvitationPersistence`의 단일 가드로 **하나씩** 보낸다. 같은 탭에서 동시에 보내면 자기 저장끼리 409가 난다. 이런 요청은 `InvitationBuilderPage`에서 `api/client`로 직접 부르지 않는다. version을 올리지 않는 요청(확인 링크 `api.issueReviewLink`/`revokeReviewLink` 등)은 가드 밖에서 직접 불러도 된다 — 저장 뒤에 줄 세울 이유가 없다.
- 응답을 받으면 편집기의 version을 응답 값으로 갱신한다.
- 응답이 끊긴 경우의 규칙을 새 쓰기에도 정한다: 재조회했을 때 version이 **정확히 1** 올랐고 이 탭의 내용(`canonicalJson`으로 키 순서 무시)이나 상태가 보일 때만 성공으로 본다(`ownWriteLanded`, `own`, `ownWriteUnder*` 참고). 멱등 요청(다시 보내도 version이 안 오르는 것)은 "2 오름"의 설명이 될 수 없다는 점도 반영한다.
- 현재 쓰기별 규칙(새 쓰기를 설계할 때 같은 기준으로 맞춘다):
  - **예식일**: 응답은 version이 정확히 1 올랐을 때만 반영한다(그 사이 다른 곳의 저장을 놓치지 않게). 예외로, 이 탭의 응답 끊긴 쓰기가 먼저 반영돼 2 오른 경우에는 응답에 그 쓰기가 보이면 반영한다(`own`).
  - **공개**: 응답을 못 받으면 다시 읽어서 `PUBLISHED`이고 version이 정확히 1 올랐을 때만 공개 완료로 본다. 그렇지 않으면 재시도가 자기 공개 때문에 충돌한다.
  - **저장·공개 재시도가 409**: 저장본을 한 번 읽어 version이 정확히 1 앞서 있고, 이 탭이 보낸 내용(키 순서 무시)이나 공개 상태가 보일 때만 그 version 위에 다시 저장한다(`ownWriteLanded`).
  - **비공개(`POST /unpublish`, 이미 DRAFT면 변화 없음)**: 같은 가드로 보내고, 응답이 끊기면 예식일처럼 기억한다(`persistence.unpublish`). 이 탭의 끊긴 쓰기 위에 비공개가 반영돼 2 오른 경우에도 그 쓰기가 보이면 반영한다. 끊긴 비공개 자신은 이 계산에서 뺀다. 다시 보내도 version이 안 오르므로, 2 올랐다면 다른 곳의 저장이다.
- 409 시 초안은 그대로 두고 "최신 내용 새 탭에서 보기"/"최신 내용으로 바꾸기"만 제공한다. 강제 덮어쓰기는 만들지 않는다.
- 동작 규칙을 바꿨다면 위 목록과 CLAUDE.md "저장 충돌" 요약을 함께 고친다.

## 7. 검증 (끝내기 전에 전부 확인)

백엔드 (`./gradlew test`, docker DB 필요):
- [ ] `@WebMvcTest` 컨트롤러 테스트: 비로그인 401, **남의 청첩장 403**, 정상 200 응답 JSON 모양(page_data가 문자열/이상한 객체가 아니라 실제 JSON인지).
- [ ] version을 올리는 쓰기: 잘못된 `version` → 409 + `code: STALE_VERSION`; 응답의 version이 증가해 있는지(`InvitationVersionIntegrationTest` 패턴).
- [ ] version을 올리면 안 되는 쓰기: 호출 전후 version 동일.
- [ ] 하객 응답: `visible: false` 블록이 응답에 없는지(`GuestPageViewTest` 패턴), 미공개 청첩장에 하객 쓰기 404, 숨긴 블록에 하객 쓰기 403.
- [ ] 참조가 바뀌는 쓰기: `referencesChanged`/`assertUsable` 호출 검증(`InvitationServiceTest` 패턴).

프론트 (`frontend/`):
- [ ] `npm test` — `editor/persistence.test.ts`에 새 쓰기의 동시성·응답 끊김·409 경우 추가. `npm test`는 `package.json`에 **나열된 파일만** 돌리므로 새 `*.test.ts`를 만들었으면 `scripts.test` 목록에도 추가한다.
- [ ] `npm run build`(타입 체크).

문서:
- [ ] 공개 엔드포인트를 늘렸으면 CLAUDE.md "인증 > 공개 엔드포인트" 목록 갱신.

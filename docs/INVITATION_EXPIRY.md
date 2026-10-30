# 무료 청첩장 만료 삭제

## 규칙

- 대상: `plan = FREE`인 청첩장. 상태(DRAFT/PUBLISHED/ARCHIVED)와 무관하다. PAID는 지우지 않는다.
- 기준: **예식일 + 10일이 지난 다음 날**(한국 시간). 예: 6/12 예식이면 6/22까지 유지되고 6/23부터 삭제 대상이 된다.
- 만료일을 따로 저장하지 않는다. 예식일은 에디터의 [변경] 버튼(`PUT /api/invitations/{id}/wedding-date`, 소유자만, 오늘 이후 날짜만)으로 바로 바뀌므로 매 실행마다 `weddingDate < 오늘(KST) - 10일`로 계산한다. `Invitation.expiresAt` 컬럼은 이 배치에서 쓰지 않는다.
- 개인정보 보관 최소화가 우선이라 소프트 삭제하지 않는다. 청첩장 행, RSVP, 방명록(이미 소프트 삭제된 글 포함), `media_asset` 행을 실제로 지운다.

## R2 사진

- 배치는 R2 파일을 직접 지우지 않는다. 다른 사진 참조 변경과 같은 `MediaLifecycle.lock()`(PostgreSQL advisory lock)을 먼저 잡고, 청첩장 pageData·langVariants·media_asset의 키를 `referencesChanged(키들, 없음)`로 "버려짐" 처리한다.
- 실제 파일 삭제는 기존 R2 정리 작업이 유예(기본 7일) 뒤에 한다. 템플릿이나 다른 청첩장이 같은 키를 쓰면 정리 작업이 다시 보호한다. 자세한 내용은 [MEDIA_CLEANUP.md](MEDIA_CLEANUP.md).
- 따라서 R2 정리가 꺼진 환경(`invi.r2.cleanup.enabled=false`, HTTPS 테스트 포함)에서는 DB 기록은 지워져도 **R2 파일은 남는다.**

## 설정 (`invi.invitation.expiry.*`)

| 속성 | 기본값 | 설명 |
|---|---|---|
| `enabled` | `true` | 자동 실행 여부 |
| `dry-run` | `true` | `true`면 삭제 대상 id만 로그에 남기고 아무것도 지우지 않는다 |
| `retention-days` | `10` | 예식일 이후 보관 일수(1~365) |
| `batch-size` | `100` | 한 번 실행에서 처리할 최대 건수 |
| `interval` | `PT3H` | 깨어 있는 동안 반복 간격 |
| `initial-delay` | `PT2M` | 서버 시작 후 첫 실행까지 대기 |

- 로그 예: `Invitation expiry: InvitationExpiryReport[dryRun=true, weddingDateBefore=..., expired=[uuid...], deleted=0, failed=[]]`. 슬러그에는 신랑·신부 이름이 들어가는 경우가 많아 id만 남긴다.
- 대상 목록 조회 후 **청첩장마다 별도 트랜잭션**(`InvitationExpiryTransactions.deleteIfExpired`)으로 지운다. 잠금은 청첩장 하나를 지우는 동안만 잡는다. 잠금을 잡은 뒤 무료 플랜·예식일 조건을 다시 확인해, 그사이 날짜가 미뤄졌으면 지우지 않는다. 예식일 변경도 같은 잠금을 잡는다.
- 한 건이 실패하면 그 건만 롤백되어 `failed`에 id가 남고, 나머지는 계속 처리한다. 실패한 건은 다음 주기에 다시 시도한다.

## 무료 호스팅에서의 실행 시점

Render Free는 15분 동안 요청이 없으면 잠들고, 잠든 동안 타이머가 돌지 않는다. 그래서 서버가 깨어난 뒤 2분 후 실행되는 첫 실행이 주된 트리거다. 접속이 없으면 삭제가 며칠 늦어질 수 있다. 개발 기간에는 이 지연을 허용한다. 정시 삭제가 필요해지면 유료 인스턴스나 외부 주기 호출을 다시 검토한다.

## 실서비스 전 결정 사항

- HTTPS 테스트 프로필은 `invi.invitation.expiry.dry-run=true`를 요구한다. 시작 검사가 이를 강제한다.
- 실서비스에서 `dry-run=false`로 바꾸기 전에, dry-run 로그로 대상 목록이 맞는지 며칠간 확인한다.
- 실제 R2 파일까지 지우려면 같은 시점에 R2 정리(`invi.r2.cleanup.enabled/dry-run`)도 함께 결정한다.

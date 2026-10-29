# R2 잔여 파일 안전 정리

## 범위와 기본값

- 미저장·취소·실패·교체 뒤 사용되지 않는 파일을 추적한다. 대표·갤러리·OG의 저장된 참조를 모두 보호한다.
- 자동 작업은 기본 `enabled=false`, 실제 삭제 없는 점검은 `dry-run=true`, 유예는 `P7D`(7일)이다. 유예는 24시간 미만으로 설정할 수 없다.
- 점검은 R2 파일을 지우지 않지만 **DB에 최초 발견·미사용 시각을 기록**한다. 완전한 read-only 요청은 아니다.
- 공개본과 초안은 현재 별도 스냅샷이 아니라 같은 Invitation.pageData를 쓴다. 모든 상태(DRAFT/PUBLISHED/ARCHIVED), 숨긴 블록, 알 수 없는 필드, langVariants, Template.defaultPageData·thumbnailUrl의 사진을 보호한다.
- URL 호스트 변경, CDN 경로·query·fragment, 원시 키, 1~2단계 퍼센트 인코딩과 브라우저가 정규화하는 dot segment(`./`, `../`)·역슬래시·탭/개행 별칭을 보수적으로 탐색한다. 키가 텍스트/필드명에 포함된 경우도 보호해 오탐 삭제보다 과잉 보호를 우선한다.

## 추적과 유예

`tracked_media_object`는 BaseEntity의 UUID·감사 시각 외에 bucket/key, ACTIVE/DELETING/DELETED, unreferencedSince, safeAfter, R2 수정 시각·ETag, 삭제 claim/lease, 삭제 시각을 저장한다. bucket+key는 유일하다. Hibernate update가 새 테이블을 추가하며 기존 데이터/버킷을 비우지 않는다.

1. presign 응답을 내보내기 전에 발급 기록을 DB에 커밋한다. PUT URL 5분 유효기간과 추가 1시간 안전 구간보다 먼저 삭제할 수 없다. 기본 7일 유예도 별도로 만족해야 한다.
2. attach 실패·미호출·PUT 미실행도 발급 기록으로 남는다. 성공 attach/재시도는 저장 기회를 주기 위해 유예를 새로 시작한다.
3. 저장 시 새 참조는 보호하고 제거된 참조의 미사용 시각을 그 저장 시각으로 다시 설정한다. 저장 실패는 이 변경도 롤백하므로 기존 사진을 보호한다.
4. 기존 파일은 R2 전체 목록과 기존 MediaAsset에서 발견한다. 오래전에 업로드됐더라도 **처음 발견한 날부터 다시 7일** 기다린다. 기존 발급 기록이 없다는 이유로 즉시 삭제하지 않는다.
5. 스캔에서 현재 참조가 발견되면 유예를 해제한다. R2 수정 시각/ETag가 바뀌면 유예를 새로 계산한다. LIST/HEAD 시간 정밀도 차이는 초 단위로 정규화한다.
6. R2에 파일이 없는 오래된 발급 기록도 7일 뒤 삭제 표식으로 전환할 수 있다. 삭제 표식은 남겨 오래 열린 편집기가 이미 삭제된 URL을 다시 저장·attach하지 못하게 한다(409, 사진 재업로드 안내).

## 삭제와 저장의 경쟁 방지

- 청첩장 생성·저장·공개, 템플릿 시드, presign·attach·삭제 예약, 정리 트랜잭션은 모두 **엔티티를 읽기 전에** 동일한 PostgreSQL transaction-scoped advisory lock을 잡는다. 앱 인스턴스가 여러 개여도 공유된다.
- 스토리지 목록/HEAD 요청은 전역 잠금 밖에서 수행한다. 삭제 직전 별도 트랜잭션에서 모든 저장 참조·유예·최신 HEAD를 다시 확인하고 `DELETING`을 먼저 커밋한다.
- 이후 해당 키를 새로 저장/attach/공개하는 요청은 거절한다. R2 삭제와 DB 정리를 한 트랜잭션처럼 가정하지 않는다.
- 실제 R2 삭제 성공 뒤에만 대응 MediaAsset을 제거하고 DELETED 표식을 남긴다. 응답 유실·R2 오류·DB 완료 실패는 DELETING 보호를 유지하며 재시도한다. 이미 없는 파일의 404는 DB 정리를 재개할 수 있다.
- claim lease는 30분이다. 다른 작업자가 진행 중인 키를 동시에 처리하지 않고, 중단된 작업은 lease 만료 후 재개한다. 이전 작업자의 늦은 완료는 새 claim을 덮어쓰지 못한다.
- 후보마다 독립 처리하며 오류 하나가 뒤의 후보를 막지는 않는다. 인벤토리·전체 참조 조회가 실패하면 부분 목록으로 삭제하지 않고 작업을 중단한다.

## 스토리지 조건부 삭제 안전 게이트

R2의 [S3 호환표](https://developers.cloudflare.com/r2/api/s3/api/)는 DeleteObject의 If-Match 지원을 명시하지 않는다. HEAD 후 무조건 DELETE로 파일 변경 경쟁을 무시하지 않는다.

- 실제 삭제가 켜지고 후보가 있을 때, 먼저 `invi-cleanup-probes/{랜덤 UUID}`에 작은 일회용 검증 객체를 만든다.
- 임시 객체를 다른 내용으로 덮어쓴 뒤 **실제 이전 ETag**의 If-Match DELETE가 **412**로 거절되고 최신 객체가 남는지 확인한다. 최신 ETag 삭제 후에는 HEAD 404까지 확인한다. 미지원/조건 무시/권한·통신 오류라면 **관리 대상 사진은 한 장도 삭제하지 않는다**.
- 관리 대상 파일은 항상 HEAD에서 확인한 ETag를 If-Match 헤더로 보내 삭제하고, 삭제 후 HEAD 404까지 확인한 뒤 DB 정리를 완료한다. 412나 다른 오류에서 무조건 삭제로 폴백하지 않는다. 헤더 호환 검증은 공급자의 동작 보장을 대신하지 않으므로 운영 전 실제 R2 동작을 확인해야 한다.
- 무조건 DELETE는 전용 임시 검증 객체 정리에만 사용한다. 임시 객체 정리까지 실패한 경우 운영자가 `invi-cleanup-probes/`만 별도 정리할 수 있다. 이 경로는 청첩장 업로드·자동 관리 대상이 아니다.
- 점검 모드에서는 검증 객체를 만들지도 않고 DELETE/claim도 하지 않는다. If-Match가 지원되지 않는 버킷/엔드포인트에서는 점검·유예 추적만 사용하고, 안전한 삭제 방법이 확인되기 전에는 실제 삭제를 활성화하지 않는다.

## 키·버킷 경계

- 자동 대상은 정확한 소문자 `invitations/{UUID}/{UUID-v4}.{jpg|png|webp}` 키와 현재 설정 bucket뿐이다. 다른 폴더, 잘못된 키, 임의 외부 이미지 URL은 삭제하지 않는다.
- 기존 MediaAsset에는 bucket 정보가 없다. 버킷을 변경/복사하거나 별도의 도구가 같은 namespace에 쓰기 전에 정리를 끄고 별도 검토한다. raw SQL로 참조를 변경하거나 다른 서비스가 잠금 없이 저장하면 보호 계약을 우회하므로 지원하지 않는다.
- ETag가 같은 동일 내용 덮어쓰기까지 구별하는 버전 잠금은 아니다. 관리 namespace는 이 앱만 쓰며 원시 R2 쓰기 권한을 다른 서비스와 공유하지 않는 전제다. 외부 writer/수동 DB 변경을 함께 허용하려면 불변 경로·버전 관리 등 별도 설계가 필요하다.
- 수명이 짧은 presigned PUT이 attach 후에도 덮어쓸 수 있는 기존 업로드 구조는 유지한다. 유예/안전 시각은 이를 고려하지만 불변 경로 승격을 새로 구현한 것은 아니다.
- R2 인벤토리는 기본 최대 10,000개다. 초과/잘못된 pagination/메타데이터 누락이면 실제 삭제를 하지 않는다. 후보 전체 수를 보고하고 물리 작업은 기본 최대 100개씩 처리한다. 참조 조회는 현재 MVP 규모의 전체 DB 스캔이며 대규모 운영 전 별도 인덱싱/확장이 필요하다.

## API

기존 인증·청첩장 소유권 검사를 유지한다.

- `POST /api/invitations/{id}/media/cleanup-preview`: 본인 청첩장 namespace만 점검한다. 자동 작업 설정과 관계없이 **항상 dryRun=true**이며 실제 삭제를 요청할 수 없다. tracked/referenced/waiting/eligible/deleting/deleted/failed 등의 수치만 응답하며 다른 청첩장의 키/내용을 노출하지 않는다.
- `DELETE /api/invitations/{id}/media/{mediaId}`: 즉시 R2/DB 삭제 대신 사용 여부 확인 후 유예 추적을 예약하고 **202 Accepted**를 응답한다. 사용 중이거나 확인할 수 없는 키는 409다. 갤러리/대표 편집기는 사진 배열을 수정·수동 저장하는 기존 흐름을 유지한다.
- 저장된 초안에서 사진 연결을 해제하지 않은 채 삭제 API만 호출해 파일을 지울 수 없다. 브라우저 미저장 상태는 서버가 알 수 없으므로 7일 넘게 미저장한 파일은 정리될 수 있다.

## 설정과 실행

공개 설정인 application.yml 기본값:

```yaml
invi:
  r2:
    cleanup:
      enabled: false
      dry-run: true
      grace-period: P7D
      interval: PT1H
      initial-delay: PT5M
      max-objects: 10000
      batch-size: 100
```

환경변수는 `R2_CLEANUP_ENABLED`, `R2_CLEANUP_DRY_RUN`, `R2_CLEANUP_GRACE_PERIOD`를 지원한다. 자동 작업을 쓸 때도 먼저 enabled=true/dry-run=true로 시작해 로그 수치를 확인한다. 실제 삭제 전에는 참조·대기·삭제 예정 목록을 검토하고 조건부 삭제 검증이 해당 R2에서 동작하는지 확인한다. 유예를 줄이거나 기존 객체의 날짜를 인위적으로 과거로 바꿔 실버킷 삭제를 시험하지 않는다.

키·JWT·R2 비밀값은 기존 gitignored application-local.yml에만 둔다. 로그는 수치와 오류 종류만 남기고 SDK 본문·signed URL·토큰을 기록하지 않는다. 현재 Wi-Fi 사설 IP 하나에 바인딩하는 실행 원칙도 바꾸지 않는다. 개발 서버를 외부에 개방하지 않는다.

## 검증

저장소 루트 PowerShell:

```powershell
.\backend\gradlew.bat -p backend test --console=plain
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
git diff --check
```

서비스/스케줄 설정 변경은 백엔드 재시작이 필요하다. 기존 서버만 정상 종료하고 같은 local 프로필·Wi-Fi Origin으로 다시 시작한다. `Started InviApiApplication`과 health를 확인한다. 테스트 통과나 Gradle BUILD SUCCESSFUL만으로 서버 기동까지 성공했다고 판단하지 않는다.

실연결 점검:

1. 기존 갤러리 테스트 청첩장 preview에서 저장된 사진은 referenced, 미저장/교체 잔여 파일은 waiting, deleted=0인지 확인한다. 첫 발견 파일은 eligible=0이어야 한다.
2. presign 후 PUT하지 않은 테스트 기록, PUT/attach 뒤 저장하지 않은 사진, 저장·교체 사진을 구분해 추적한다. 점검을 반복해도 변경 없는 파일의 최초 유예 시각이 반복 연장되지 않는지 테스트/DB로 확인한다.
3. 본인 아닌 청첩장 preview/삭제 예약은 403, 인증 없는 요청은 401인지 확인한다. 사용 중인 MediaAsset 삭제 요청은 409이고 공개 이미지가 그대로 로드돼야 한다.
4. 저장·사진 교체·갤러리 재시도·공개가 기존대로 동작하는지 확인한다. 실제 삭제/유예 시간 경과/경쟁 조건은 고정 Clock과 mock R2 테스트로 검증한다.
5. 실제 조건부 삭제 지원과 7일 경과 후 작업은 아직 수행하지 않았다면 그 상태를 명시한다. 테스트 성공을 실제 버킷 파일 정리 완료로 표현하지 않는다.

## 현재 검증 상태

구현·회귀 테스트 작성을 완료하고 읽기 전용 검토에서 발견된 URL 별칭 보호와 조건부 삭제/삭제 완료 확인 문제를 보완했다. 2026-10-28 사용자 로컬 Gradle 전체 테스트가 BUILD SUCCESSFUL이며 생성된 XML 26개 suite 집계는 372 tests, failures/errors/skipped 각 0이다. 프론트는 사용자 제공 결과로 63개 테스트·빌드 성공, lint 오류 0개와 기존 경고 3개를 확인했다. 테스트 예외 헬퍼의 SDK 반환 타입 오류를 수정한 뒤 백엔드를 재실행한 결과다. 백엔드 local 프로필·기존 Wi-Fi Origin으로 재시작한 뒤 health 200/UP 및 실제 R2 cleanup-preview 200을 확인했다. 실제 사진 보호·발급 후 미업로드·미저장 업로드·저장 전후 참조 변화·별칭 참조 보호·삭제 예약 202·공개 사진 보존까지 확인했으며 아래 실연결 기록을 따른다. R2 조건부 삭제 호환 검증과 실제 파일 삭제는 수행하지 않았고 운영/현재 버킷의 실제 삭제는 기본 비활성 상태로 유지한다.

## 2026-10-28 실제 R2 점검 기록

- 사용자 backend 재시작 후 LAN health 200/UP, 새 owner-scoped cleanup-preview 200을 확인했다. 실제 삭제 설정은 활성화하지 않았다.
- 기존 gallery-check-20261028의 첫 점검: dryRun=true, graceHours=168, scanned/tracked=3, referenced=2, waiting=1, eligible/deleting/deleted/failed=0. 오래된 잔여 사진도 최초 발견일부터 유예 대상이었다.
- 사용 중인 기존 MediaAsset DELETE는 409, 인증 없는 preview는 401이었다. 실제 사진은 삭제되지 않았다.
- URL만 발급하고 PUT하지 않은 새 테스트 기록을 만들었다. R2 scanned=3은 그대로인데 tracked=4, waiting=2로 증가해 미업로드 발급 기록도 추적됨을 확인했다. signed URL/토큰/키는 문서에 기록하지 않는다.
- 합성 PNG(D, 640×800)를 Worker에서 약 5KB WebP로 실제 업로드·attach했다. 저장 전 referenced=2/waiting=3, 저장 후 referenced=3/waiting=2로 전환됐다. 저장한 새 MediaAsset DELETE도 409였다.
- 같은 사진을 `/(unused)/../` 별칭 주소로 입력·저장해 실제 이미지 로드(640×800), referenced=3 유지, DELETE 409를 확인했다. 브라우저 정규화 별칭도 원래 키와 같은 보호 대상으로 처리됐다.
- 검증용 사진을 청첩장에서 제거하고 저장한 후 DELETE를 요청하자 202가 반환됐다. 직후 해당 R2 파일 GET은 200이고 MediaAsset도 남아 있어 즉시 파일/DB 삭제가 아니라 유예 예약임을 확인했다.
- 최종 점검: dryRun=true, graceHours=168, scanned=4, tracked=5, referenced=2, waiting=3, eligible/deleting/deleted/failed=0. 대기 3건은 기존 잔여 사진, 새 미업로드 발급 기록, 새 합성 업로드 사진이다. 실제 버킷 파일은 한 장도 삭제하지 않았다.
- 테스트 청첩장 사진은 원래 2장과 순서로 복원했다. 인증 없는 공개 API 200, 원래 두 이미지 fresh GET 각각 200, 원래 URL 배열 일치를 확인했다. 기존 test-wedding 청첩장은 이번 검증에서 수정하지 않았다.
- 실제 R2 조건부 DELETE capability probe와 7일 경과 후 물리 삭제는 수행하지 않았다. 활성화 전 별도 운영 확인이 남아 있으며 기본 비활성/점검 상태를 유지한다. PostgreSQL advisory-lock SQL이 실제 저장/발급/점검 경로에서 동작한 것은 확인했지만 복수 서버 동시 실행 스트레스 테스트를 대신하지는 않는다.

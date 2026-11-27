# 운영 배포 (prod)

실제 하객이 쓰는 운영 환경이다. 테스트 배포(`HTTPS_TEST_DEPLOY.md`)와 같은 이미지 구조·안전장치를 쓰고, 가입·소셜 로그인만 연다. **돈이 나가는 설정(결제수단 등록, 유료 플랜, 도메인 구매)은 하지 않는다.** 필요해 보이면 금액과 함께 먼저 묻는다.

## 무엇이 다른가

| | https-test | prod |
|---|---|---|
| 가입 | 막음, 테스트 계정 1개 | 이메일 가입(약관·처리방침 동의 필수) + Kakao·Google |
| 만료 삭제 | dry-run 고정 | 기본 dry-run, `INVITATION_EXPIRY_DRY_RUN=false`로 켬 |
| R2 자동 정리 | 끔 고정 | 끔 고정(조건부 DELETE 미검증, `MEDIA_CLEANUP.md`) |
| 계정당 청첩장 | 기본 20 | 3 (`INVITATION_MAX_PER_MEMBER`) |
| 검색 차단·`no-referrer` | 적용 | 적용(청첩장·확인 링크·로그인 토큰이 URL에 있다) |

`ProdDeploymentGuard`는 `local`/`https-test` 프로필과 함께 켜지거나, DB 이름이나 R2 버킷 이름에 `test`가 들어 있거나, R2 정리가 켜져 있으면 기동을 거절한다. TLS 검증 DB·강한 JWT·health만 노출·`ddl-auto=validate` 같은 공통 규칙은 `DeploymentChecks`로 테스트 배포와 같다.

## 이미지 빌드

같은 `Dockerfile`을 쓴다. 빌드 인자(공개값, 비밀 금지):

- `APP_MODE=prod`: 프론트 `build:prod` + Spring `prod` 프로필. 기본값은 `https-test`라 기존 테스트 서비스와 CI는 그대로다.
- `VITE_CONTACT_EMAIL`: 개인정보처리방침의 문의처. 운영 빌드는 이 값이 없으면 실패한다. 저장소에 넣지 않는다.

Render는 서비스 환경변수를 Docker 빌드 인자로도 넘기므로 두 값을 서비스 환경변수에 넣으면 된다.

## 런타임 환경변수

- `DATABASE_JDBC_URL`(`sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`, 자격증명 없이), `DATABASE_USERNAME`, `DATABASE_PASSWORD`: **새 운영 DB**. 테스트 DB를 쓰지 않는다. 빈 DB라 Flyway가 V1부터 만든다.
- `JWT_SECRET`: 새로 만든 64바이트 이상. 테스트·로컬과 다른 값.
- `INVI_R2_ACCOUNT_ID`, `INVI_R2_ACCESS_KEY_ID`, `INVI_R2_SECRET_ACCESS_KEY`, `R2_BUCKET`, `R2_PUBLIC_BASE_URL`: **새 운영 버킷**과 그 버킷만 쓰는 키. 버킷 CORS에 운영 Origin의 PUT/GET/HEAD.
- `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`: 없으면 기동하지 않는다.
- `KAKAO_REST_API_KEY`: 편집기 예식장 검색용 Kakao REST API 키(Kakao Developers 앱 `wedding-invite` > 플랫폼 키 > REST API 키 `invi-venue-search`). 로그인용 `KAKAO_CLIENT_ID`와 다른 키다. 없어도 기동하지만(로그에 경고) 장소 검색이 503이라 커플은 주소를 직접 입력해야 한다. 같은 앱의 "유료 API > 카카오맵"은 **사용 안 함**으로 둔다 — 그래야 무료 한도(키워드 검색 하루 100,000건)를 넘으면 차단만 되고 청구되지 않는다. 앱 자체 한도는 `invi.place-search.*`(계정당 10분 30회, 하루 Kakao 호출 50,000건, 1시간 캐시).
- 선택: `APP_PUBLIC_ORIGIN`(Render 기본 주소면 생략), `INVITATION_EXPIRY_DRY_RUN`, `INVITATION_MAX_PER_MEMBER`.

## 사람이 콘솔에서 할 일

1. **Kakao Developers**: 운영 앱(또는 기존 앱)에 사이트 도메인과 리다이렉트 URI `https://<운영 주소>/login/oauth2/code/kakao` 등록, 동의항목 닉네임·카카오계정(이메일).
2. **Google Cloud Console**: 리다이렉트 URI `https://<운영 주소>/login/oauth2/code/google` 등록. OAuth 동의 화면에 개인정보처리방침 URL `https://<운영 주소>/privacy`를 넣고 **프로덕션으로 게시**(테스트 상태면 등록한 사용자만 로그인된다).
3. **서비스용 메일** 만들고 `VITE_CONTACT_EMAIL`에 넣기.

## 출시 전에 정할 것 (처리방침과 맞추기)

- 처리방침과 약관은 "예식일 10일 뒤 삭제"라고 약속한다. 운영 첫 며칠 dry-run 로그로 대상을 확인한 뒤 `INVITATION_EXPIRY_DRY_RUN=false`로 켠다. 첫 커플 예식일+10일 전에 켜야 한다.
- R2 자동 정리가 꺼져 있으면 지운 청첩장의 사진 파일이 버킷에 남는다(주소는 추측 불가지만 처리방침의 "사진 파일도 삭제"와 어긋난다). 출시 전에 조건부 DELETE 호환을 검증해 켜거나, 수동 삭제 절차를 정한다.
- 처리방침의 위탁 업체 저장 위치(싱가포르, 아시아·태평양)는 실제 생성한 리소스의 지역과 맞는지 확인한다. 시행일(`frontend/src/site.ts`)은 공개일로 바꾼다.
- 약관에 계정당 청첩장 수(현재 3개)가 적혀 있다. `INVITATION_MAX_PER_MEMBER`를 바꾸면 약관 문구도 고친다.
- 이 문구는 법률 검토를 받지 않은 초안이다.

## 무료 한도 (2026-11-24 확인)

- Render Free: 워크스페이스당 월 750시간. 카드를 등록하지 않으면 한도 초과 시 과금 없이 정지된다. 테스트 서비스와 운영 서비스를 동시에 계속 켜 두면 한도를 넘는다.
- Neon Free: 프로젝트당 월 100 CU-hours, 1GB. 초과 과금 없이 정지. 복구 기록은 6시간뿐이라 예식 전 `pg_dump` 백업을 직접 받는다.
- R2: 월 10GB·쓰기 100만·읽기 1,000만 건 무료. **유일하게 초과 과금이 가능한 곳**이라 계정당 청첩장 수를 제한한다.

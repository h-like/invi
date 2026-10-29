# HTTPS 테스트 배포

## 목적과 현재 상태

- 같은 Wi-Fi 밖의 모바일 데이터·다른 기기에서 하객 링크와 공유 흐름을 확인하는 **테스트 배포**다. 실제 하객 서비스 운영 승인을 뜻하지 않는다.
- Vite dev/preview, Spring bootRun, 공유기 포트 포워딩이나 로컬 개발 서버 터널을 공개하지 않는다. Docker에서 컴파일한 React 정적 파일과 Spring Boot JAR만 배포한다.
- 현재 소스 브랜치는 `phase-1/https-test-deploy`, 시작점은 사용자가 확인한 최신 main `8be877c`다.
- 2026-10-29 사용자가 무료 Render·Neon 진행을 승인했고 두 서비스의 Google 로그인 연결을 확인했다. Render Hobby/등록 카드 없음 상태에서 Docker·Singapore·Free($0), health `/actuator/health/readiness`, Auto-Deploy Off로 서비스를 생성했다. `51e6e1b`의 첫 이미지 빌드와 배포가 성공했고 12:14:48 KST에 Live가 됐다. 첫 빌드부터 Live까지 5분 7초였으며, 장기 안정성이나 휴면 후 기동 시간의 보장은 아니다.
- Neon Free 테스트 프로젝트 `invi-https-test`, Singapore, PostgreSQL 16, DB `invi_https_test`를 사용한다. 기존 DB/사진을 복사하지 않았다. 새 스키마와 전용 테스트 계정 초기화, TLS 검증 설정을 통한 DB 연결과 로그인을 확인했다. 기본 branch 이름 `production`은 Neon의 기본 이름일 뿐 이 프로젝트는 격리 테스트용이다.
- 사용자는 프론트 테스트·lint·일반/HTTPS 빌드 성공을 보고했다. SPA 테스트의 미로그인 차단 기대값을 403에서 401로 바로잡은 뒤 백엔드 XML 32개 suite에서 620 tests / failures 0 / errors 0 / skipped 0을 확인했다. 사용자 출력으로 `51e6e1b` 커밋·원격 push·clean 상태를 확인한 뒤 그 커밋을 배포했다. 이 문서의 후속 배포 결과 기록은 별도 커밋 대상이며 런타임 코드 변경은 없다.
- R2 테스트 버킷 `invi-media-https-test`(APAC/Standard)와 해당 버킷만 Object Read & Write 가능한 사용자 토큰 `invi-https-test-upload`를 만들었다. 토큰은 **2026-11-28 만료**이며 계속 업로드를 테스트하려면 만료 전에 교체해야 한다. 기존 `invi-media` 버킷·키·CORS·객체는 변경하지 않았다. 실제 객체 삭제도 하지 않았다.

## 2026-10-29 외부 HTTPS 확인 기록

- 앱: https://invi-https-test.onrender.com/
- 공개 테스트 초대장: https://invi-https-test.onrender.com/i/https-test-20261029
- Render의 실제 배포 상태 `Deploy succeeded | Live` 및 준비 API 200 / `UP` 확인. HTTP 주소가 HTTPS로 이동하고 HSTS 헤더가 반환되는 것을 확인했다.
- `/`, `/templates`는 HTML 200, `/api/templates`는 JSON 200, 인증 없는 `/api/invitations`는 401, 누락 `/assets/missing.js`는 JSON 404다. 검색 차단 헤더와 `no-referrer`도 확인했다.
- 로그인 화면에서 가입·소셜 버튼이 숨겨지고, 이미 존재하는 테스트 계정으로 수행한 유효한 등록 요청도 서버가 403 `Test signup is disabled.`로 차단했다. 이 점검은 새 계정을 만들지 않았다.
- 테스트 계정으로 청첩장을 만들고 가짜 이름·장소·안내문을 입력했다. PNG 합성 이미지로 대표 1장·갤러리 3장 업로드, 갤러리 순서 변경, 저장 및 에디터 새로고침 유지를 확인했다.
- 공개 DTO에 포함된 이미지 네 개는 새 테스트 버킷 공개 주소만 사용하며 모두 WebP 200으로 열렸다(2,642~2,740 bytes). 기존 로컬 버킷 이미지 참조는 없었다.
- 공개 버튼 성공, 새 탭의 `/i/https-test-20261029` 직접 접속·새로고침, 인증 없는 공개 HTML/API 200, HTTPS 링크 복사 성공 메시지를 확인했다.
- 가짜 하객 응답 1건(불참)과 테스트 방명록 1건을 전송했다. 공개 방명록 새로고침 유지 및 소유자 관리 화면의 응답/방명록 각 1건을 확인했다.
- 브라우저 390×844 viewport 확인 후, 사용자가 실휴대폰에서도 접속을 확인했다고 보고했다(2026-10-29). 기종·브라우저·통신망의 상세 결과와 휴대폰에서의 편집/업로드까지 확인한 것으로 확대해 기록하지 않는다. iPhone/Safari, 실제 HEIC 사진 품질, 무료 휴면 뒤 재기동, 장시간/최대 부하 메모리 사용량은 별도 미검증이다.
- 런타임 비밀값은 Render와 로컬 `backend/.env.https-test.local`에만 보관했다. 이 파일은 `.gitignore`의 `.env.*.local`과 Docker 제외 규칙에 해당한다. 강제 add·채팅 복사·프론트 배포를 하지 않는다. 앱의 `TEST_OWNER_PASSWORD`는 Google 비밀번호와 별도로 생성한 테스트 비밀번호다.
- 일회성 기동 확인 예약은 정상 기동 확인 후 삭제했다. 휴면 방지용 주기 요청은 설정하지 않았다. 결제수단 추가나 유료 전환도 하지 않았다.

## 테스트 환경 격리

- 로컬 DB를 외부로 노출하거나 그대로 복사하지 않는다. 새 테스트 DB를 사용한다.
- 로컬 `invi-media` 버킷과 키를 재사용하지 않는다. 새 테스트 버킷과 그 버킷만 읽기/쓰기가 가능한 R2 자격 증명을 사용한다. 기존 버킷 CORS·키·파일은 변경하지 않는다.
- 새 DB는 다른 환경 사진의 참조를 알 수 없으므로 R2 정리는 **enabled=false/dry-run=true**로 고정한다. 환경변수로 이 안전 조건을 어기면 HTTPS-test 시작 검사가 실패한다.
- 테스트 초대장·사진·이름·장소·응답은 합성/가짜 데이터만 사용한다. 비밀번호·JWT·DB/R2 키는 채팅, 문서, Git, VITE 변수, Docker ARG, JAVA_TOOL_OPTIONS에 넣지 않는다.
- 기본 로컬 모드와 기존 `--mode mobile`은 그대로 유지한다. HTTPS 테스트 이미지를 로컬/LAN 개발 서버 대신 사용하지 않는다.

## 구성

1. Node 빌드 단계에서 `npm ci`와 `npm run build:https-test`를 실행한다.
2. JDK 21 단계에서 frontend/dist를 backend 정적 리소스에 넣고 bootJar를 만든다. 컨테이너 빌드는 테스트 DB 없이 수행하며 테스트는 별도로 먼저 실행한다.
3. JRE 21 최종 이미지에는 JAR만 복사한다. root가 아닌 uid 10001로 실행한다. 기본 메모리 옵션은 512MB 테스트 인스턴스를 고려한 시작값이지 실제 RSS 적합성을 검증한 결과는 아니다.
4. 이미지 진입점은 `--spring.profiles.active=https-test`를 명시한다. `local` 프로필과 함께 쓰면 시작을 거절한다.
5. 호스팅 프록시가 외부 TLS를 처리하고 컨테이너 내부의 `0.0.0.0:$PORT`로 전달한다. 이것은 격리된 배포 컨테이너의 수신 설정이며 개인 PC의 개발 서버 바인딩을 바꾸는 지시가 아니다.

`.dockerignore`는 Git/개인 도구 설정, node_modules·빌드 결과, local 백엔드 프로필, 개인 .env·키·인증 파일 등을 제외한다. 공개 `.env.https-test`와 `application-https-test.yml`만 허용한다. 배포 비밀값을 받는 Docker ARG는 없다. Render는 서비스 환경변수를 빌드 인자로도 전달할 수 있으므로 Dockerfile에서 해당 값을 참조하지 않는다.

## 단일 Origin과 화면 경로

- HTTPS 테스트 모드의 API 주소는 `window.location.origin`이다. 배포 주소를 빌드 시점에 추측해 넣지 않는다.
- `VITE_API_SAME_ORIGIN=true`와 별도의 `VITE_API_BASE_URL`을 동시에 지정하면 실패한다. 공용 HTTP Origin도 실패한다. 로컬 컨테이너 QA용 localhost/127.0.0.1/::1 HTTP만 예외다.
- `.env.https-test`의 명시적인 빈 API 주소는 일반 `.env.local`의 LAN 주소가 이 모드로 흘러드는 것을 막는다. 셸에 별도로 지정한 충돌 값은 지우고 다시 빌드한다.
- `/i/:slug`, `/e/:id`, `/e/:id/guests`, 템플릿·로그인 등 알려진 React 경로만 index.html로 전달한다. GET/HEAD로 직접 열기·새로고침을 지원한다.
- `/api/**`와 존재하지 않는 JS/CSS를 index.html로 삼키지 않는다. private API는 계속 JWT와 소유권 검사가 필요하다. HTML 편집 화면 껍데기가 공개인 것과 데이터 공개는 다르다.
- 테스트 프로필은 검색 차단 robots.txt, X-Robots-Tag, no-referrer를 설정한다. 비인가/CORS 거절 응답에도 적용되도록 개인정보 헤더 필터를 보안 필터보다 먼저 실행한다.
- 외부 URL·HTTPS 리다이렉트·프록시 헤더 신뢰는 실제 배포 후 확인한다. 앱이 임의의 직접 수신 연결까지 HTTPS로 바꾸는 자체 인증서 서버는 아니다.

## 전용 테스트 계정

- HTTPS-test에서는 서버가 `/api/auth/register`를 403으로 거절한다. 프론트에서도 가입 전환과 소셜 로그인 버튼을 숨긴다. 서버의 OAuth 로그인 연결도 비활성화해 우회 가입을 막는다. UI 플래그가 아니라 서버 설정/시작 검사로 등록 제한을 강제한다.
- `TEST_OWNER_EMAIL`과 `TEST_OWNER_PASSWORD`로 전용 로컬 로그인 계정을 시작 시 준비한다. 없을 때만 생성하고, 이미 있으면 동일 비밀번호로 로그인 검증만 한다. 재시작이나 환경변수 변경으로 기존 비밀번호를 몰래 재설정하지 않는다.
- 테스트 계정의 비밀번호가 기존 DB와 맞지 않으면 시작을 중단한다. 자동 비밀번호 초기화/외부 HTTP 관리 엔드포인트는 제공하지 않는다.
- 테스트 비밀번호는 8~64자, UTF-8 72바이트 이하다. 테스트 계정의 인증정보도 비밀 관리자/배포 Secret에 보관하며 공개 공유 링크와 함께 전달하지 않는다.
- 하객은 계정 없이 공개 초대장을 열고 기존 참석 응답·방명록 흐름을 사용할 수 있다. 일반 로컬/모바일 모드의 기존 가입은 기본 활성 상태를 유지한다.

## 런타임 환경변수

아래 값은 호스팅의 런타임 환경에서만 설정한다. 예시 호스트/비밀번호를 그대로 복사하지 않는다.

- `APP_PUBLIC_ORIGIN`: 실제 할당된 HTTPS Origin. Render에서는 생략 시 자동 제공되는 `RENDER_EXTERNAL_URL`을 사용한다. 다른 호스팅이나 커스텀 도메인은 명시한다. 경로·인증정보·query·fragment를 넣지 않는다.
- `TEST_DATABASE_JDBC_URL`: 새 테스트 DB의 JDBC 주소. 사용자명/비밀번호를 URL에 포함하지 않는다. PostgreSQL TLS `sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`가 필요하다. hostname verifier/factory argument 같은 우회 옵션은 금지한다.
- `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD`: 새 DB 자격 증명. 로컬 DB 자격 증명과 분리한다.
- `JWT_SECRET`: 새로 만든 강한 서버 서명 키. UTF-8 64바이트 이상이며 개발용 기본값을 사용할 수 없다. 테스트 계정 비밀번호와도 다른 값이다.
- `INVI_R2_ACCOUNT_ID`, `INVI_R2_ACCESS_KEY_ID`, `INVI_R2_SECRET_ACCESS_KEY`: 테스트 버킷 전용 자격 증명. 코드에 기록하지 않는다.
- `TEST_R2_BUCKET`: 새 테스트 버킷 이름. 로컬 `invi-media`는 거절된다.
- `TEST_R2_PUBLIC_BASE_URL`: 해당 테스트 버킷의 실제 HTTPS 공개 이미지 루트. presigned PUT URL이 아니다.
- `TEST_OWNER_EMAIL`, `TEST_OWNER_PASSWORD`: 이 환경 전용 로그인 계정.
- `PORT`: 호스팅이 주입하는 내부 수신 포트. 미지정 시 8080이다. 개발 PC의 5173/8080을 외부로 전달하지 않는다.

프로필은 DB 연결 풀을 작게 유지하고 SQL/bind 로그와 상세 서버 오류를 숨긴다. actuator 노출은 health뿐이다. `ddl-auto=update`는 **새로운 격리 테스트 DB**를 위한 것으로 실제 운영 마이그레이션 정책을 확정한 것은 아니다.

## 호스팅 선택과 비용 확인

2026-10-29 공식 문서 확인 기준:

- Render Free Web Service는 15분 무요청 시 휴면하고 다음 요청 시 기동에 수십 초~수분이 걸릴 수 있다. 이번 첫 배포에서는 JVM 초기화에도 시간이 걸렸으므로 1분 이내 기동을 보장하지 않는다. 750 무료 인스턴스 시간/월과 트래픽·빌드 한도가 있다. 결제수단이 등록돼 있으면 일부 초과 사용은 과금될 수 있어 계정/워크스페이스 요금·한도를 확인한다. 유료 인스턴스나 결제수단을 확인 없이 추가하지 않는다.
- Render 무료 Postgres는 30일 만료 조건이 있어 장기 보존 테스트 DB로 선택하지 않는다.
- Neon Free는 기간 제한·카드 없이 제공되며 프로젝트당 1GB 및 월 100 CU-hours 등의 한도가 있다. 실제 생성 화면의 Free 선택과 한도도 다시 확인한다.
- R2도 기존 계정의 사용량·무료 한도와 실제 과금 조건을 생성 전에 확인한다. 버킷 격리는 데이터 보호용이며 비용 차단을 보장하지 않는다. 자동 초과 과금 조건이 있으면 추가 확인한다.
- 이 구성은 장시간 첫 로딩·중단·메모리/트래픽 한도 때문에 실제 하객 운영용으로 보장하지 않는다. 512MB에서 메모리 초과가 발생해도 유료로 자동 변경하지 않고 사용자에게 확인한다.

출처: [Render Free](https://render.com/docs/free), [Render Docker](https://render.com/docs/docker), [Render 기본 환경변수](https://render.com/docs/environment-variables), [Neon Pricing](https://neon.com/pricing).

## 실제 배포 순서

계정 사용 동의 및 코드 검증 후에만 진행한다.

1. Render/Neon 로그인 계정과 Free 플랜·결제 상태를 확인한다. GitHub 로그인은 올바른 계정인지 확인하고 필요 이상의 저장소 권한을 승인하지 않는다. 저장소가 public이므로 Render의 Public Git Repository 연결을 우선 검토한다.
2. 별도 Neon 테스트 프로젝트/DB와 별도 R2 테스트 버킷·버킷 한정 키를 만든다. 가능한 한 가까운 지역을 고르되 실제 지원/무료 선택지를 확인한다.
3. 테스트 버킷 공개 주소를 활성화하고 CORS에는 실제 배포 Origin의 PUT/GET/HEAD, Content-Type만 허용한다. 로컬 버킷의 정책은 바꾸지 않는다.
4. 검증된 기능 브랜치 커밋을 push한 후 Render의 Docker 서비스 소스로 선택한다. 루트 Dockerfile, Free 인스턴스, 런타임 비밀값, health check `/actuator/health/readiness`를 설정한다. 명시적으로 확인하지 않은 자동 배포는 끈다.
5. 준비 상태는 `readinessState,db`를 함께 확인한다. 테스트 계정 초기화까지 끝나야 준비 완료가 된다. 빌드 성공/프로세스 시작 로그만으로 배포 완료라고 하지 않는다.
6. 실제 할당 URL과 최종 배포 commit을 확인하고 HTTPS·로그인·하객 화면·업로드·공유를 검증한다. 실제 주소는 확인 전 문서/응답에 추측해서 기록하지 않는다.

## 로컬 검증

실제 배포 비밀값을 주입하지 않은 저장소 루트 PowerShell에서 실행한다. 한 명령이 실패하면 멈추고 해당 출력을 확인한다.

```powershell
.\backend\gradlew.bat -p backend test --console=plain
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
npm --prefix frontend run build:https-test
git diff --check
```

Docker Desktop이 실행 중이면 새 이미지도 실제 빌드한다. 이 빌드에는 DB/R2/JWT/테스트 계정 비밀값을 넣지 않는다.

```powershell
docker build -t invi-https-test .
```

이미지 실행은 실제 격리 DB·테스트 버킷·전용 계정 환경이 준비된 뒤 수행한다. 로컬/LAN 서버를 대체하거나 공개하지 않는다. CI도 기존 백엔드 테스트와 두 프론트 모드 빌드 후 비밀값 없이 이미지를 빌드한다.

## 외부 HTTPS 검증 체크리스트

- 할당 주소 HTTPS 인증서·HTTP→HTTPS 이동·`/actuator/health/readiness` 200 확인.
- 일반 브라우저와 인증 없는 새 탭에서 템플릿/공개 하객 링크 열기, `/i/:slug` 직접 접속·새로고침 200 확인.
- 테스트 계정 이메일 로그인·제작·저장·공개·같은 URL 최신 내용 확인. 신규 가입은 서버 403이며 소셜 버튼은 숨겨져 있어야 한다.
- private API 인증 없는 요청 401, 다른 소유자 접근 차단, 존재하지 않는 API/JS가 HTML로 바뀌지 않는지 확인.
- 합성 JPG/PNG·갤러리 여러 장·실패 재시도·대표 사진 교체·순서/삭제·새로고침 유지 확인. 새 버킷 URL만 사용하고 기존 로컬 버킷 객체는 건드리지 않는다.
- HTTPS 클립보드 복사 성공, 복사한 하객 링크를 다른 기기/메신저에서 열기 확인. 현재 정적 SPA의 메신저 OG 미리보기 한계와 링크 자체 접근 성공은 구분한다.
- PC 브라우저 모바일 viewport 검사는 실기기를 대신하지 않는다. 사용자가 휴대폰 Wi-Fi를 끄고 모바일 데이터로 열어 본 결과를 별도 기록한다.
- Free 휴면 후 첫 접속 지연과 실제 메모리 사용량/오류를 확인한다. 휴면 방지용 무한 요청이나 외부 장기 폴링은 하지 않는다.
- R2 자동 삭제가 비활성/점검 상태인지 확인한다. 테스트 종료 후 서비스/프로젝트/키의 유지·삭제도 사용자가 결정한다.

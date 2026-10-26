# Git 관리 가이드

CLAUDE.md "Git 컨벤션"의 규칙을 실제 명령어와 절차로 풀어 쓴 문서다. 규칙이 서로 다르면 CLAUDE.md를 따른다.

## 1. 원칙 요약

- `main`은 항상 빌드되는 상태로 두고, 직접 커밋하거나 push하지 않는다. GitHub ruleset으로 막혀 있다(12절).
- 기능 하나 = 브랜치 하나 = PR 하나.
- 새 브랜치는 항상 최신 `origin/main`에서 딴다.
- 커밋은 빌드와 테스트가 통과할 때만 한다. 예외는 switch용 `wip:` 커밋 하나뿐이다(5절).
- 기능 브랜치 push는 사람도 에이전트도 자주 해도 된다. force-push는 아무도 하지 않는다(서버에서 막혀 있다).
- PR 생성과 머지는 사람이 직접 한다.

## 2. 이 저장소는 public이다

[github.com/h-like/invi](https://github.com/h-like/invi)는 공개 저장소다. push한 것은 전부 누구나 볼 수 있고, 공개 저장소의 키를 자동으로 수집하는 봇도 있다. 그래서 다음을 지킨다.

- **비밀값은 절대 커밋하지 않는다.** OAuth 시크릿, JWT 키, R2 키는 `application-local.yml`(gitignored)이나 `.env.local`에만 둔다.
- **실제 개인정보를 커밋하지 않는다.** 테스트 픽스처나 시드 데이터에 실제 하객 이름, 전화번호, 계좌번호를 쓰지 않는다. 가짜 값을 쓴다.
- GitHub secret scanning push protection이 켜져 있어 알려진 서비스의 키(Google, Cloudflare 등)가 든 push는 거부된다. 하지만 **직접 만든 JWT 시크릿 같은 임의 문자열은 감지하지 못한다.** 마지막 방어선이 아니라 보조 장치로 생각한다.

## 3. 새 브랜치를 만드는 때

새 기능의 첫 파일을 고치기 직전에 만든다.

| 상황 | 새 브랜치? |
|---|---|
| 이전 PR이 머지되고 다음 기능을 시작할 때 | 만든다 |
| PR이 리뷰 중인데 그것과 상관없는 기능을 시작할 때 | 만든다 (base는 `origin/main`) |
| 리뷰 지적 사항을 고칠 때 | 만들지 않는다. 기존 브랜치에 커밋을 추가한다 |
| 작업 중 지금 기능과 무관한 버그를 발견했을 때 | 만든다 |
| 작업 중 지금 기능 때문에 생긴 버그를 발견했을 때 | 만들지 않는다. 같은 브랜치에서 고친다 |
| 이미 머지된 브랜치에 작업을 더 하고 싶을 때 | 만든다. 머지된 브랜치는 재사용하지 않는다 (8절) |
| 아직 머지되지 않은 기능 A에 의존하는 작업을 할 때 | A가 머지될 때까지 기다리거나 A에 포함시킨다. A에서 브랜치를 따지 않는다 |

## 4. 새 브랜치 만들기

```bash
git fetch origin
git switch -c phase-1/rsvp-api origin/main
```

- 로컬 `main`을 거치지 않고 `origin/main`에서 바로 딴다. Claude 앱 worktree처럼 다른 곳에 `main`이 체크아웃돼 있어도 동작한다(`git switch main`은 `already used by worktree` 에러로 실패할 수 있다).
- 브랜치 이름은 `phase-N/기능명` 형식으로, 영어 kebab-case를 쓴다. 예: `phase-1/invitation-editor`
- **Claude 앱이 만든 `claude/...` 브랜치는 이름을 바꾸지 않는다.** 앱이 그 이름으로 브랜치와 PR을 추적하기 때문이다. 이런 브랜치는 PR 제목으로 기능을 드러낸다(8절). 앱 밖의 터미널에서 작업할 때만 `phase-N/기능명`으로 직접 만든다.

## 5. switch하는 때와 switch 전 체크

switch가 필요한 순간은 다음 네 가지다.

1. 새 기능을 시작할 때: 4절대로 `origin/main`에서 새 브랜치를 만든다.
2. PR이 머지된 직후: 다음 작업용 새 브랜치로 이동한다(8절).
3. 리뷰 지적 사항을 고치러 돌아갈 때: `git switch phase-1/rsvp-api`
4. 급한 수정이 끼어들 때: 하던 브랜치를 두고 hotfix 브랜치를 만든다.

**switch 직전에는 반드시 `git status`가 깨끗해야 한다.** `git switch`는 커밋 안 된 변경을, 충돌만 없으면 조용히 새 브랜치로 들고 간다. 그러면 미완성 코드가 엉뚱한 PR에 섞인다.

테스트가 깨진 작업 중간이라 정식 커밋을 할 수 없을 때는 `wip:` 임시 커밋으로 상태를 비운다.

```bash
# 떠나기 전
git status
git add -A
git commit -m "wip: rsvp 폼 검증 작업 중"
git switch -c fix/xxx origin/main

# 돌아온 직후
git switch phase-1/rsvp-api
git reset --soft HEAD~1
```

- `git add -A` 전에 `git status`로 비밀값 파일이 목록에 없는지 확인한다(2절).
- `wip:` 커밋은 **push하지 않는다.** push 전에 `git log -1`로 마지막 커밋이 `wip:`인지 확인한다. 실수로 push했다면 reset하지 말고(force-push가 필요해지는데 서버에서 막힌다), 이어서 작업한 뒤 정식 커밋을 쌓는다. squash merge라 main 기록에는 남지 않는다.
- `reset --soft`는 커밋만 풀고 변경 내용은 스테이징된 상태로 되돌린다. 코드는 사라지지 않는다.
- `wip:`는 Conventional Commits type이 아니다. 이 용도로만 쓰는 예외다.

## 6. 커밋하기

커밋 전에 바뀐 쪽을 검증한다.

```bash
cd backend && ./gradlew test
cd frontend && npm run build
```

```bash
git status
git add <파일들>
git commit -m "feat(rsvp): add guest-count validation"
```

- 형식: `<type>(<scope>): <설명>`. type은 `feat` `fix` `refactor` `test` `chore` `docs` 중 하나다.
- 커밋 하나에는 논리적인 변경 하나만 담는다. 백엔드 API와 그 API를 쓰는 프론트 화면은 한 커밋에 묶어도 된다.
- `git add .`보다 파일을 지정해서 추가한다. 비밀값 파일이 섞이지 않게 하기 위해서다.

## 7. push는 언제 하나

**작업 중간에도 언제든 push해도 되고, 오히려 권장한다.** 기능 브랜치의 push는 원격 백업일 뿐이고 `main`에는 영향이 없다. 조건은 세 가지다.

- 마지막 커밋이 테스트 통과 상태의 정식 커밋이어야 한다. `wip:` 커밋은 push하지 않는다.
- `main`이 아닌 내 기능 브랜치여야 한다.
- push할 커밋에 비밀값이나 실제 개인정보가 없어야 한다. 저장소가 public이다(2절).

최소한 다음 시점에는 반드시 push한다.

1. 하루 작업을 마칠 때
2. PR을 올리기 직전
3. 리뷰 지적 사항을 고친 뒤

```bash
# 처음 push할 때 (원격 브랜치 연결)
git push -u origin phase-1/rsvp-api

# 그다음부터
git push
```

- force-push(`--force`, `--force-with-lease` 모두)는 하지 않는다. 모든 브랜치에서 서버가 거부한다(12절).
- push가 secret scanning에 걸려 거부되면, 우회하지 말고 해당 커밋에서 비밀값을 빼고 다시 커밋한다(10절).

## 8. PR 올리고 머지하기

```bash
gh pr create --base main --title "feat(rsvp): add RSVP submission API" --body "변경 내용 / 테스트 방법"
```

- **PR 제목도 Conventional Commits 형식으로 쓴다.** squash merge를 하면 PR 제목이 `main`의 커밋 메시지가 된다.

1. PR을 올리고 `/code-review`를 실행한다.
2. 지적 사항을 고치고 커밋한 뒤 다시 push한다.
3. 리뷰를 통과하면 squash merge하고, 브랜치를 바로 지운다.

```bash
gh pr merge --squash --delete-branch
git fetch origin
```

- **머지된 브랜치는 재사용하지 않는다.** squash merge를 하면 원래 브랜치의 커밋이 `main`에 그대로 들어가지 않는다. 그 브랜치에 작업을 더 해서 PR을 다시 올리면, 이미 머지된 변경이 diff에 다시 나오고 충돌이 난다. 추가 작업은 4절대로 새 브랜치에서 한다.

## 9. 작업 중에 main이 앞서 나갔을 때

```bash
git fetch origin
git merge origin/main
```

- rebase 대신 merge를 쓴다. rebase 후 push하려면 force-push가 필요한데, 서버에서 막힌다.
- 충돌이 나면 해결하고, 테스트를 돌린 뒤 커밋한다.

## 10. 실수 되돌리기

| 상황 | 명령어 |
|---|---|
| 스테이징 취소 | `git restore --staged <파일>` |
| 마지막 커밋 메시지 수정 (push 전만) | `git commit --amend` |
| `wip:` 커밋 풀기 (push 전만) | `git reset --soft HEAD~1` |
| 이미 push한 커밋 되돌리기 | `git revert <sha>` (기록을 남기면서 취소) |
| push 전에 비밀값 커밋을 발견함 | 파일에서 비밀값을 빼고 `git commit --amend`. 여러 커밋 전이면 `git reset --soft <비밀값 이전 sha>` 후 다시 커밋한다 |
| **비밀값이 이미 push됨** | **즉시 해당 키를 폐기하고 새로 발급한다.** 커밋을 지우는 것으로는 부족하다. public 저장소라 이미 수집됐다고 가정한다 |

## 11. 에이전트 루프와 git

- 할 수 있는 것: 테스트와 빌드가 통과한 상태에서 `git add`, `git commit`, 기능 브랜치 `git push`.
- 하지 않는 것: force-push, `main` push, 원격 브랜치 삭제, PR 생성·머지.
- **커밋과 push 전에 `git branch --show-current`를 확인하고, `main`이면 멈춘다.**
- push 전에 `git log -1`을 확인하고, `wip:` 커밋이면 push하지 않는다.
- 루프가 만든 커밋은 메시지에 그 사실을 적는다. 예: `test(rsvp): tighten guest-count validation via TDD loop`

## 12. GitHub 보호 설정 (현재 상태)

에이전트를 포함해 누가 어떤 명령을 쓰든, 아래 규칙은 GitHub 서버가 강제한다. 우회 목록(bypass list)은 비워둔다. 저장소 소유자 계정도 우회할 수 없어야 에이전트의 실수가 막힌다.

| 설정 | 대상 | 내용 |
|---|---|---|
| Ruleset `Protect main` | `main` | PR 필수(승인 0명), force-push 차단, 삭제 차단 |
| Ruleset `Protect all branches` | 모든 브랜치 | force-push 차단만. 삭제 차단은 넣지 않는다(머지 후 `--delete-branch`가 막히기 때문) |
| Secret scanning + push protection | 저장소 전체 | 알려진 서비스 키가 든 push 거부 |

설정 상태는 아래 명령으로 확인한다.

```bash
gh api repos/h-like/invi/rulesets
gh api repos/h-like/invi --jq '.security_and_analysis'
```

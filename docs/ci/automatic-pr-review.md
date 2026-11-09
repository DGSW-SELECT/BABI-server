# PR 자동 리뷰

- PR 최초 생성(`opened`): 자동 리뷰. draft는 `ready_for_review`로 해제할 때 리뷰한다.
- 이후 PR 일반 댓글 또는 inline 댓글에 `@claude`를 새로 작성하면 현재 head를 재리뷰한다. 댓글 수정·reopen은 리뷰하지 않는다.
- 새 push(`synchronize`): 기존 자동 APPROVE만 취소하고 모델은 호출하지 않는다. 다시 승인받으려면 `@claude` 댓글을 작성한다.

동일 저장소의 열린 non-draft PR과 write 권한이 있는 사람의 요청만 리뷰한다. fork·bot·일반 issue·권한 없는 요청은 모델 호출 전에 제외한다. 승인 취소는 draft 또는 bot의 push에도 실행한다. 일반 댓글 이벤트는 [GitHub 기본 branch의 workflow](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#issue_comment)를 사용하므로 이 workflow를 main에 merge해야 멘션 기능이 활성화된다.

새 push와 재리뷰 시작 시 이 workflow가 남긴 `github-actions[bot]`의 APPROVED 리뷰만 DISMISSED로 바꾼다. 사람이나 다른 자동화의 승인은 변경하지 않는다. 취소에 실패하면 오류를 숨기거나 새 승인을 추가하지 않는다. main의 기존 stale approval 규칙도 유지한다. 권한 확인을 마친 요청만 동일 PR의 이전 리뷰를 취소할 수 있다. 멘션 리뷰도 고정된 PR head의 파일을 별도 checkout하여 읽는다.

| 조건 | 결과 | 자동 APPROVE |
|---|---|---|
| Claude 정상 리뷰, 지적 없음, 현재 commit 일치, CI 통과 | Claude 리뷰 | 가능 |
| Claude 지적·검토 불충분·CI 실패 또는 대기 | 리뷰 의견 | 없음 |
| Claude 인증 없음 또는 실행 실패, DeepSeek 키 있음 | DeepSeek fallback 리뷰 | 없음 |
| 둘 다 사용 불가 또는 fallback 응답 불완전 | SKIPPED / PASS | 없음 |
| 리뷰 도중 PR head 변경 | SKIPPED / PASS | 없음 |

`PASS`는 리뷰 완료나 승인을 뜻하지 않는다. Step summary에 SKIPPED와 무승인을 표시한다. helper 테스트 실패는 정상적인 CI 실패이며 숨기지 않는다.

## 설정

- GitHub Secret `CLAUDE_CODE_OAUTH_TOKEN`: 선택. 복사 중 들어간 공백·개행을 제거하고 마스킹한다.
- GitHub Secret `DEEPSEEK_API_KEY`: 선택. 없으면 fallback을 호출하지 않는다. 키를 채팅·Git·환경변수 예시에 넣지 않는다.
- GitHub Variable `DEEPSEEK_MODEL`: 선택, 기본 `deepseek-flash`.
- GitHub Actions의 `Allow GitHub Actions to create and approve pull requests` 허용이 있어야 정식 APPROVE를 생성할 수 있다. 거부되면 COMMENT만 등록한다. 기본 token 권한은 read를 유지하고 job에서 필요한 read·pull-request write 권한만 선언한다.

기존 develop/main 승인 개수와 마지막 push 승인 조건은 유지한다. 자동 merge는 수행하지 않는다.

DeepSeek는 [공식 Chat Completions API](https://api-docs.deepseek.com/)와 [JSON mode](https://api-docs.deepseek.com/guides/json_mode/)를 사용한다. `DEEPSEEK_API_KEY` 등록은 이 저장소의 PR diff와 base AGENTS.md를 외부 DeepSeek API로 전송하는 fallback을 활성화한다. 비공개 코드도 동일하게 전송되므로 저장소 관리자가 전송 대상을 확인한 뒤 키를 등록해야 한다. 인증키는 prompt에 넣지 않는다. API 요청 45초·응답 2MB·diff 120,000자·출력 4,096 토큰 상한, 재시도 없음. 유료 API 키를 등록하면 fallback 호출은 해당 계정에 과금될 수 있다.

Claude의 구조화된 결과도 commit·필드·지적 배열을 다시 검증한다. main은 `build`, develop은 `test` check가 반드시 success여야 한다. 빈 목록·review job만 있는 목록·필수 check 미생성·skipped도 통과로 간주하지 않는다. 다른 CI가 진행 중이면 최대 180초 기다리고, 실패·대기·조회 불가 시 COMMENT만 남긴다. 그 외 base branch와 DeepSeek는 항상 COMMENT만 생성한다.

## 검증

```bash
python3 -m unittest discover -s scripts -p 'test_*review*.py' -v
```

테스트는 model HTTP와 GitHub API를 mock으로 검증한다. 실제 Secret을 지우거나 유료 API를 호출해 누락·실패 경로를 테스트하지 않는다.

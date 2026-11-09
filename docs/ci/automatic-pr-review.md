# PR 자동 리뷰

PR 생성·push·reopen·draft 해제 시 실행한다. 동일 저장소의 non-draft PR과 write 권한이 있는 작성자만 대상이다.

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

DeepSeek는 [공식 Chat Completions API](https://api-docs.deepseek.com/)와 [JSON mode](https://api-docs.deepseek.com/guides/json_mode/)를 사용한다. PR diff와 base의 AGENTS.md만 보내며 인증키는 prompt에 넣지 않는다. API 요청 45초·응답 2MB·diff 120,000자·출력 4,096 토큰 상한, 재시도 없음. 유료 API 키를 등록하면 fallback 호출은 해당 계정에 과금될 수 있다.

Claude의 구조화된 결과도 commit·필드·지적 배열을 다시 검증한다. 다른 CI가 진행 중이면 최대 180초 기다리고, 실패·대기·조회 불가 시 COMMENT만 남긴다. DeepSeek는 항상 COMMENT만 생성한다.

## 검증

```bash
python3 -m unittest discover -s scripts -p 'test_*review*.py' -v
```

테스트는 model HTTP와 GitHub API를 mock으로 검증한다. 실제 Secret을 지우거나 유료 API를 호출해 누락·실패 경로를 테스트하지 않는다.

# AWS 운영

- 계정 `380536267838`, 리전 `ap-northeast-3`, EC2 `i-0215d1d42af778314` 한 대에서 API·MySQL·Caddy·Redis를 실행한다.
- 공개 주소: `https://api.babi.prot.kr`. API `8080`, MySQL `3307`, Redis `6379`는 loopback 전용이다. DNS는 임시 공인 IP를 사용하므로 EC2 중지·시작 후 IP 변경 시 수정이 필요하다.
- `select-1-babi-start`, `select-1-babi-stop`은 사용자 요청으로 모두 `DISABLED`다. 6만 원은 예산 기준이며 자동 비용 차단은 없다. 직접 중지할 때까지 요금이 발생한다.
- MySQL의 기존 20개 테이블·`active_baby_members` 뷰를 유지한다. `aws` 프로필은 `spring.sql.init.mode=never`, Hibernate `validate`를 사용한다. 자동 배포는 DB schema나 Volume을 변경하지 않는다.

## 자동 배포

1. `main` PR에서 Java 21 build, 실제 Redis 통합 테스트, 배포 스크립트 실패·rollback 테스트를 실행한다.
2. 기존 보호 규칙에 따른 승인 2명과 마지막 push 승인 후 PR을 merge한다.
3. `AWS main build` workflow가 성공하면 해당 commit의 JAR를 `aws-<전체 SHA>` prerelease로 게시한다. 수동 재실행은 GitHub Actions의 `workflow_dispatch`를 사용한다.
4. EC2의 `babi-deploy.timer`가 5분마다 현재 `main`과 해당 release를 확인한다. SHA-256, 파일 크기, JAR의 `build.commit`, H2 미포함을 검증한 뒤 JAR를 교체한다.
5. API를 재시작하고 로그인·회원가입·이메일 API 문서 및 무인증 리포트 `401`을 확인한다. 시작 실패 시 이전 JAR로 복구한다. 실패한 commit은 `failed-revision`에 기록하고 자동 재시도하지 않는다.

AWS access key, EC2의 GitHub token, 추가 인스턴스, 공개 SSH/DB 포트, self-hosted Actions runner를 사용하지 않는다. 공개 release에는 JAR만 포함하며 비밀번호·설정 파일은 넣지 않는다. GitHub API의 비인증 조회 제한을 고려해 5분 주기로 확인한다.

`main`에 새 변경이 있지만 성공한 release가 없으면 기존 버전을 유지한다. 새 JAR가 다른 schema를 요구하면 자동 migration하지 않고 시작 실패·rollback으로 처리한다. DB migration, `pull_deploy.py`, systemd unit 변경은 별도 검증·설치가 필요하다.

운영 확인:

```bash
sudo systemctl status babi-server babi-deploy.timer --no-pager
sudo journalctl -u babi-deploy -n 30 --no-pager
sudo cat /opt/babi/deployed-revision
sudo systemctl start babi-deploy.service
```

실패 원인을 수정한 뒤 같은 commit을 다시 시도할 때만 `/opt/babi/failed-revision`을 제거하고 service를 실행한다. `/opt/babi/babi-server.previous.jar`는 직전 버전 복구용이다. JAR 교체 중에는 짧은 API 중단이 있다.

## 환경 변수와 데모

`/etc/babi/app.env`는 `root:root`, `0600`으로 유지한다. 기존 `MYSQL_PASSWORD`, `JWT_SECRET`을 유지하며 독립적인 무작위 `EMAIL_VERIFICATION_SECRET`을 추가한다. `/etc/babi`는 `0700`, 배포 파일은 디렉터리 `0755`·파일 `0644`를 사용한다. `/opt/babi`와 배포 스크립트는 root가 소유한다.

SMTP 계정이 없는 데모 운영에서는 `MAIL_USERNAME`, `MAIL_PASSWORD`를 설정하지 않는다. 기존·데모 계정 로그인은 가능하지만 인증 메일 발송은 `503 EMAIL_SEND_FAILED`로 실패하며 회원가입의 이메일 인증 검증은 그대로 유지한다. SMTP 설정 후 실제 메일 발송·가입을 별도로 검증해야 한다. 보안 그룹 아웃바운드가 HTTPS만 허용된 현재 구성에서는 SMTP 포트 허용도 별도 승인이 필요하다.

Redis는 `deploy/compose.redis.yml`로 기존 EC2에 추가한다. 128 MiB 메모리 제한과 64 MiB 데이터 제한을 적용하며 이메일 인증용 TTL 데이터만 저장한다. Redis 재시작 시 진행 중인 인증은 다시 해야 한다.

데모 계정은 두 개의 일반 계정만 생성하고 비밀번호는 저장소·release·로그에 남기지 않는다. 운영 DB 백업은 서버 `/var/backups/babi`에 `root:root`, `0600`으로 보관한다. 일회성 배포 전 백업은 정기 백업을 대신하지 않는다.

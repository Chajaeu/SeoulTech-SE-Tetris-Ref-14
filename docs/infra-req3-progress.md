# Infra / 3차 요구사항 진행 상황 (2026-11-21 작업본)

원본: `Tetris-main-final-check.zip` (3차 개발 이전 main 스냅샷). Core·UI의 이후 변경사항은 미반영.

| GitHub 이슈 | 단독으로 추가한 코드 | 남은 연동/검증 |
| --- | --- | --- |
| #77 통신 프로토콜 | MessageType, NetworkMessage, MessageCodec, DTO, 명세 문서 | Core·UI 셀/공격/결과 타입 합의 |
| #78 TCP 서버/클라이언트 | P2PServer, P2PClient, TcpLink, NetworkService | 실제 두 PC와 UI 로비 연결 |
| #79 최근 IP | RecentIpRepository, 연결 성공 시 자동 저장 | UI에 목록 표시·선택 |
| #80 게임 상태 동기화 | GameStateSnapshot 및 encode/decode, 송수신·콜백 | Core snapshot 생성, UI 렌더, 실화면 200ms 측정 |
| #81 공격 패턴 전송 | AttackPatternDTO 및 encode/decode, 송수신·콜백 | Core 공격 패턴 이벤트/적용 |
| #82 종료·재대전 | GAME_OVER 메시지 및 양측 WAITING/Ready 재시작 | Core 승패/시간제한 종료, UI 결과 화면 |
| #83 자동화 테스트 | Codec/IP/네트워크 JUnit 소스 + standalone TCP smoke | macOS Gradle에서 전체 테스트 실행, 실제 2PC 통합 테스트 |
| #84 회귀 및 커버리지 | JUnit 추가, 기존 테스트 코드 유지 | 최종 병합 기준 Line Coverage 70% 확인 |
| #85 배포/협업 | 기존 scripts/package-macos.sh 유지 | 최종 통합 후 신규 패키지와 GitHub/Trello 기록 확인 |
| #86 양측 Ready | 로비 상태, 서버 MODE_SELECT, READY/START | UI 버튼·Core 경기 시작 연결 |
| #87 랙/단절 | PING/PONG RTT, 500ms 랙/3초 단절 기본값, 상태 전달 | UI 경고/초기 화면, 임계치 팀 합의, 실환경 검증 |

> JUnit 소스는 프로젝트에 추가했지만 이 작업 환경에는 Gradle 9.8.1 배포판이 없어 전체 Gradle/JUnit을 실행하지 못함. 별도 Java 21 스모크 테스트는 실제 소켓 통신으로 통과함. 임시 검증 결과를 JUnit 통과 결과처럼 보고하지 말 것.

## 사용 방법

1. 최신 main과 작업 시작 지점이 같은지 확인 (`git status -sb`, `git log -1 --oneline`).
2. 브랜치 생성: `git switch -c feature/infra-req3-p2p-base`.
3. 새로 추가한 `src/main/java/seoultech/se/tetris/infra/network/`, `src/main/java/seoultech/se/tetris/infra/persistence/RecentIpRepository.java`, `src/test/java/seoultech/se/tetris/infra/network/`, `docs/`, `scripts/`를 복사/병합.
4. `./gradlew clean test jacocoTestReport` 실행.
5. `bash scripts/test-p2p-standalone.sh` 실행.
6. `docs/p2p-protocol.md`를 Core·UI 담당과 공유하고 셀·공격·승패 API를 먼저 확정.
7. API 합의 후 Core·UI의 최신 브랜치를 병합해 최종 연동/테스트 진행.

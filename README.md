# NOOS 백엔드

NOOS AI 모듈의 Spring Boot 백엔드입니다.

## 담당 역할

- `/api/ai/**`와 `/api/eeg/results` 엔드포인트 제공
- 인식과 개입 계획을 위해 Python `noos_ai` CLI 실행
- 생성된 오디오 파일을 `/api/ai/audio`로 프록시
- 원격 ACE-Step 작업자 사전 준비와 생성 요청 호출
- 선택적으로 로컬 Gemma 보조 서비스 호출
- 로컬 UDP로 WiZ 전구 제어
- 로컬 프로토타입 사용을 위해 프론트엔드가 호출하는 주요 엔드포인트를 인증 없이 허용

## 주요 파일

- `src/main/java/com/noos/backend/BackendApplication.java`: Spring Boot 진입점
- `src/main/java/com/noos/backend/ai/controller/NoosAiController.java`: AI 라우트
- `src/main/java/com/noos/backend/ai/service/NoosAiService.java`: Python CLI, ACE-Step, 오디오 프록시, 선택적 Gemma 호출
- `src/main/java/com/noos/backend/lighting/controller/WizLightingController.java`: WiZ 라우트
- `src/main/java/com/noos/backend/lighting/service/WizLightingService.java`: WiZ CCT/RGB 명령 실행과 원상복구
- `src/main/resources/application.properties`: 로컬 실행 기본값
- `src/main/resources/mappers/auth/AuthMapper.xml`: MyBatis 인증 매퍼

## 명령

```bash
./gradlew bootRun
./gradlew test
```

## 로컬 설정

현재 기본값은 아래와 같습니다.

- 프론트엔드는 `http://localhost:8080`의 백엔드와 통신합니다.
- 백엔드는 `http://192.168.123.114:8011`의 ACE-Step 작업자를 호출합니다.
- Gemma는 기본적으로 비활성화되어 있습니다.
- WiZ 자동 적용은 설정된 전구 IP에 대해 활성화되어 있습니다.

로컬 전용 비밀값은 `application-secret.properties`에 둡니다. 인증 정보는 커밋하지 않습니다.

## 엔드포인트 지도

- `POST /api/eeg/results`: Muse EEG 또는 밴드 데이터를 요약합니다.
- `POST /api/ai/intervention/music`: 개입 계획을 만들고, 오디오를 생성하고, 조명을 적용합니다.
- `POST /api/ai/intervention/prewarm`: ACE-Step 상태를 확인하거나 사전 준비합니다.
- `GET /api/ai/audio?path=...`: 허용된 경로의 생성 오디오를 스트리밍합니다.
- `GET /api/lighting/wiz/status`: 현재 WiZ 동기화 작업 상태를 조회합니다.
- `GET /api/lighting/wiz/devices`: 설정된 WiZ 전구 상태를 읽습니다.
- `POST /api/lighting/wiz/apply-plan`: primary/secondary 조명 전환을 시작합니다.
- `POST /api/lighting/wiz/stop`: 활성 조명 작업을 멈추고 세션 시작 전에 저장한 WiZ 상태로 복구합니다.

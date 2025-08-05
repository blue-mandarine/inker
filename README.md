# 🚀 Spring API 문서 자동 생성기

KSP(Kotlin Symbol Processing)와 ASM을 사용하여 Spring Boot 프로젝트의 Controller를 자동으로 분석하고 API 문서를 생성하는 도구입니다.

## ✨ 주요 기능

- 🔍 **자동 Controller 탐지**: Spring `@Controller`와 `@RestController` 어노테이션을 자동으로 감지
- 📋 **완전한 엔드포인트 분석**: HTTP 메서드, 경로, 파라미터, 반환 타입 등 추출
- ⚡ **예외 분석**: `orElseThrow`, 직접 throw, Assert 등의 예외 패턴 자동 감지
- 🎯 **상태 코드 매핑**: 예외 타입을 HTTP 상태 코드로 자동 매핑 (400, 404, 500 등)
- 📄 **다양한 출력 형식**: JSON과 HTML 형식으로 API 문서 생성
- 🌐 **범용 프로젝트 지원**: 모든 Spring Boot 프로젝트에서 사용 가능
- 🔧 **멀티모듈 지원**: 복잡한 프로젝트 구조 자동 인식

## 🛠️ 기술 스택

- **Kotlin**: 메인 개발 언어
- **ASM**: 바이트코드 분석을 위한 라이브러리
- **KSP**: Kotlin Symbol Processing (향후 확장용)
- **Jackson**: JSON 직렬화
- **Spring Annotations**: Spring Web 어노테이션 지원

## 📊 Spring Petclinic 분석 결과

현재 Spring Petclinic 프로젝트를 분석한 결과:

### 📈 통계
- **총 Controller 수**: 6개
- **총 엔드포인트 수**: 17개

### 🎯 발견된 Controller들
1. **VetController** - 수의사 관련 API (2개 엔드포인트)
2. **PetController** - 반려동물 관련 API (4개 엔드포인트)
3. **OwnerController** - 반려동물 소유자 관련 API (7개 엔드포인트)
4. **VisitController** - 방문 관련 API (2개 엔드포인트)
5. **WelcomeController** - 메인 페이지 (1개 엔드포인트)
6. **CrashController** - 오류 테스트용 (1개 엔드포인트)

## 🚀 사용 방법

### 1. 프로젝트 클론 및 빌드
```bash
git clone <repository>
cd inker
./gradlew build
```

### 2. 대상 Spring 프로젝트 컴파일
분석하려는 Spring 프로젝트를 먼저 컴파일해야 합니다:

```bash
# Maven 프로젝트의 경우
cd /path/to/spring-project
./mvnw compile

# Gradle 프로젝트의 경우
./gradlew compileJava
```

### 3. API 문서 생성 실행

#### 방법 1: 대화형 메뉴 사용
```bash
./gradlew run
```
실행 후 메뉴에서 프로젝트를 선택하세요:
- **Spring Petclinic**: 기본 제공 샘플 프로젝트
- **Zzimkkong**: 우아한테크코스 프로젝트
- **사용자 정의 경로**: 직접 프로젝트 경로 입력

#### 방법 2: 명령행 인수 사용
```bash
# 프로젝트 경로만 지정
./gradlew run --args="/path/to/spring-project"

# 프로젝트 경로와 이름 모두 지정
./gradlew run --args="/path/to/spring-project 'Project Name'"
```

### 4. 결과 확인
생성된 파일들:
- `{project-name}-api-documentation.json` - JSON 형식의 API 문서
- `{project-name}-api-documentation.html` - HTML 형식의 API 문서 (브라우저에서 확인 가능)

## 📝 생성되는 정보

각 API 엔드포인트에 대해 다음 정보가 추출됩니다:

### 🔍 기본 정보
- **HTTP 메서드**: GET, POST, PUT, DELETE, PATCH
- **경로**: URL 패턴 (`/owners/{ownerId}/pets/new`)
- **Controller 클래스**: 풀 클래스명
- **메서드명**: 실제 Java/Kotlin 메서드명
- **기본 매핑**: 클래스 레벨 `@RequestMapping`

### 📥 요청 정보
- **파라미터**: 타입, 이름, 필수여부, 소스(PathVariable, RequestParam 등)
- **요청 본문**: RequestBody 타입 및 필수 여부

### 📤 응답 정보
- **성공 응답**: 200 OK와 반환 타입
- **실패 응답**: 자동 감지된 예외 정보
  - **상태 코드**: 400, 404, 500 등
  - **예외 타입**: IllegalArgumentException, RuntimeException 등
  - **설명**: 한국어 에러 설명
  - **감지 위치**: "orElseThrow pattern", "Direct throw" 등

## 🔧 설정 가능한 항목

`ApiDocumentationGenerator.kt`에서 다음 설정을 변경할 수 있습니다:

```kotlin
companion object {
    private const val PETCLINIC_PROJECT_PATH = "/Users/user/spring-petclinic"
}
```

다른 Spring 프로젝트를 분석하려면 경로를 변경하세요.

## 📋 지원되는 Spring 어노테이션

- `@Controller`
- `@RestController`
- `@RequestMapping`
- `@GetMapping`
- `@PostMapping`
- `@PutMapping`
- `@DeleteMapping`
- `@PatchMapping`
- `@PathVariable`
- `@RequestParam`
- `@RequestBody`

## 🌟 예시 출력

### JSON 형식 예시
```json
{
  "title": "Spring Petclinic API Documentation",
  "version": "1.0.0",
  "controllers": [
    {
      "className": "org.springframework.samples.petclinic.owner.OwnerController",
      "endpoints": [
        {
          "path": "/owners/{ownerId}",
          "method": "GET",
          "methodName": "showOwner",
          "parameters": [
            {
              "name": "ownerId",
              "type": "int",
              "required": true,
              "source": "PATH_VARIABLE"
            }
          ],
          "responses": {
            "success": {
              "statusCode": 200,
              "type": "org.springframework.web.servlet.ModelAndView",
              "description": "View와 Model을 포함한 응답"
            },
            "failures": [
              {
                "statusCode": 400,
                "exceptionType": "java.lang.IllegalArgumentException",
                "description": "잘못된 요청 파라미터",
                "detectedAt": "orElseThrow pattern"
              }
            ]
          }
        }
      ]
    }
  ]
}
```

### 🔍 실제 감지된 예외 사례
다음과 같은 코드에서 예외를 자동으로 감지합니다:

```java
@GetMapping("/owners/{ownerId}")
public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {
    Optional<Owner> optionalOwner = this.owners.findById(ownerId);
    Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
        "Owner not found with id: " + ownerId));
    // ... 
}
```

**분석 결과:**
- 🟠 **400 Bad Request**: IllegalArgumentException 
- 📍 **감지 위치**: orElseThrow pattern
- 💬 **설명**: 잘못된 요청 파라미터

### HTML 출력 특징
- 🎨 **깔끔한 UI**: 모던한 웹 디자인
- 🏷️ **HTTP 메서드별 색상 구분**: GET(녹색), POST(빨강), PUT(주황) 등
- 📱 **반응형 디자인**: 모바일 친화적
- 🔍 **상세 정보**: 파라미터, 반환 타입 등 모든 정보 표시

## 🚧 향후 개선 계획

- [ ] KSP 기반 컴파일 타임 분석 추가
- [ ] OpenAPI 3.0 스펙 호환 출력
- [ ] 커스텀 어노테이션 지원
- [ ] 요청/응답 스키마 자동 추출
- [ ] Swagger UI 연동
- [ ] 다중 프로젝트 분석 지원

## 📞 문의

프로젝트에 대한 문의나 개선 제안이 있으시면 이슈를 남겨주세요! 
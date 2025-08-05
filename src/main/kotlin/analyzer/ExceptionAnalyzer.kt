package analyzer

import model.FailureResponse
import org.objectweb.asm.tree.*

/**
 * 예외 분석기
 * 메서드의 바이트코드를 분석하여 발생 가능한 예외들을 찾아냅니다.
 */
class ExceptionAnalyzer {
    
    companion object {
        /**
         * 예외 타입별 HTTP 상태 코드 매핑
         */
        private val EXCEPTION_STATUS_MAPPING = mapOf(
            // Standard Java Exceptions
            "java.lang.IllegalArgumentException" to 400,
            "java.lang.IllegalStateException" to 400,
            "java.lang.NullPointerException" to 500,
            "java.lang.RuntimeException" to 500,
            "java.lang.Exception" to 500,
            
            // Spring Framework Exceptions
            "org.springframework.web.bind.MethodArgumentNotValidException" to 400,
            "org.springframework.web.HttpRequestMethodNotSupportedException" to 405,
            "org.springframework.web.HttpMediaTypeNotSupportedException" to 415,
            "org.springframework.web.bind.MissingServletRequestParameterException" to 400,
            "org.springframework.web.method.annotation.MethodArgumentTypeMismatchException" to 400,
            "org.springframework.security.access.AccessDeniedException" to 403,
            "org.springframework.security.authentication.AuthenticationException" to 401,
            "org.springframework.dao.DataAccessException" to 500,
            "org.springframework.dao.EmptyResultDataAccessException" to 404,
            "org.springframework.dao.DataIntegrityViolationException" to 409,
            
            // Common Custom Exceptions (추론된 패턴들)
            "BadRequestException" to 400,
            "NotFoundException" to 404,
            "NotFoundExceptionException" to 404,
            "AuthException" to 401,
            "AuthenticationException" to 401,
            "AuthorizationException" to 403,
            "ForbiddenException" to 403,
            "UnauthorizedException" to 401,
            "ConflictException" to 409,
            "ValidationException" to 400,
            "InvalidRequestException" to 400,
            "ServiceException" to 500,
            "InternalServerException" to 500,
            "BusinessException" to 400,
            "DomainException" to 400,
            
            // JWT/Security Related
            "JwtException" to 401,
            "TokenExpiredException" to 401,
            "InvalidTokenException" to 401,
            "SecurityException" to 403,
            
            // Database/JPA Related  
            "EntityNotFoundException" to 404,
            "OptimisticLockException" to 409,
            "PersistenceException" to 500,
            "ConstraintViolationException" to 400,
            "DataConstraintViolationException" to 409,
            
            // File/Upload Related
            "FileNotFoundException" to 404,
            "MaxUploadSizeExceededException" to 413,
            "MultipartException" to 400,
            
            // Network/External API
            "ConnectException" to 503,
            "SocketTimeoutException" to 504,
            "TimeoutException" to 504,
            "HttpClientErrorException" to 400,
            "HttpServerErrorException" to 500
        )
        
        /**
         * 예외 타입별 기본 설명
         */
        private val EXCEPTION_DESCRIPTIONS = mapOf(
            "java.lang.IllegalArgumentException" to "잘못된 인수가 전달됨",
            "java.lang.IllegalStateException" to "객체가 잘못된 상태에 있음",
            "java.lang.NullPointerException" to "Null 참조 접근",
            "BadRequestException" to "잘못된 요청",
            "NotFoundException" to "리소스를 찾을 수 없음",
            "NotFoundExceptionException" to "리소스를 찾을 수 없음",
            "AuthException" to "인증 실패",
            "AuthenticationException" to "인증 실패",
            "AuthorizationException" to "권한 없음",
            "ForbiddenException" to "접근 권한 없음",
            "UnauthorizedException" to "인증되지 않은 접근",
            "ConflictException" to "리소스 충돌",
            "ValidationException" to "유효성 검사 실패",
            "InvalidRequestException" to "잘못된 요청 형식",
            "ServiceException" to "서비스 처리 오류",
            "InternalServerException" to "내부 서버 오류",
            "BusinessException" to "비즈니스 로직 오류",
            "DomainException" to "도메인 규칙 위반",
            "EntityNotFoundException" to "엔티티를 찾을 수 없음",
            "JwtException" to "JWT 토큰 오류",
            "TokenExpiredException" to "토큰이 만료됨",
            "InvalidTokenException" to "유효하지 않은 토큰",
            "MaxUploadSizeExceededException" to "업로드 파일 크기 초과",
            "ConnectException" to "외부 서비스 연결 실패",
            "TimeoutException" to "요청 시간 초과"
        )
    }
    
    /**
     * 메서드에서 발생할 수 있는 예외들을 분석합니다.
     */
    fun analyzeMethodExceptions(methodNode: MethodNode): List<FailureResponse> {
        val exceptions = mutableListOf<FailureResponse>()
        
        // 1. 직접적인 throw 문 분석
        exceptions.addAll(analyzeDirectThrows(methodNode))
        
        // 2. orElseThrow, Assert 등의 패턴 분석  
        exceptions.addAll(analyzeThrowPatterns(methodNode))
        
        // 3. 메서드 시그니처의 throws 절 분석
        exceptions.addAll(analyzeDeclaredExceptions(methodNode))
        
        // 4. 중복 제거 (같은 예외 타입은 하나만)
        return exceptions.distinctBy { "${it.exceptionType}-${it.statusCode}" }
    }
    
    /**
     * 직접적인 throw 문을 분석합니다.
     */
    private fun analyzeDirectThrows(methodNode: MethodNode): List<FailureResponse> {
        val exceptions = mutableListOf<FailureResponse>()
        var currentExceptionType: String? = null
        var exceptionMessage: String? = null
        
        methodNode.instructions?.forEach { instruction ->
            when (instruction) {
                is TypeInsnNode -> {
                    if (instruction.opcode == org.objectweb.asm.Opcodes.NEW) {
                        val className = instruction.desc.replace('/', '.')
                        if (isExceptionClass(className)) {
                            currentExceptionType = className
                        }
                    }
                }
                
                is LdcInsnNode -> {
                    // String 리터럴이 있으면 예외 메시지로 간주
                    if (instruction.cst is String && currentExceptionType != null) {
                        exceptionMessage = instruction.cst as String
                    }
                }
                
                is InsnNode -> {
                    if (instruction.opcode == org.objectweb.asm.Opcodes.ATHROW && currentExceptionType != null) {
                        val failureResponse = createFailureResponse(
                            currentExceptionType!!,
                            exceptionMessage,
                            "Direct throw"
                        )
                        exceptions.add(failureResponse)
                        
                        // 초기화
                        currentExceptionType = null
                        exceptionMessage = null
                    }
                }
            }
        }
        
        return exceptions
    }
    
    /**
     * orElseThrow, Assert.notNull 등의 패턴을 분석합니다.
     */
    private fun analyzeThrowPatterns(methodNode: MethodNode): List<FailureResponse> {
        val exceptions = mutableListOf<FailureResponse>()
        
        methodNode.instructions?.forEach { instruction ->
            when (instruction) {
                is MethodInsnNode -> {
                    val className = instruction.owner.replace('/', '.')
                    val methodName = instruction.name
                    
                    when {
                        // Optional.orElseThrow() 패턴
                        methodName == "orElseThrow" && className.contains("Optional") -> {
                            val exceptionType = findLambdaExceptionType(methodNode, instruction)
                            val failureResponse = createFailureResponse(
                                exceptionType,
                                null,
                                "orElseThrow pattern"
                            )
                            exceptions.add(failureResponse)
                        }
                        
                        // Assert 패턴들
                        className.contains("Assert") -> {
                            val exceptionType = when (methodName) {
                                "notNull", "notEmpty", "hasText", "isTrue" -> "java.lang.IllegalArgumentException"
                                "state" -> "java.lang.IllegalStateException"
                                else -> "java.lang.AssertionError"
                            }
                            val failureResponse = createFailureResponse(
                                exceptionType,
                                "Assertion failed: $methodName",
                                "Assert.$methodName"
                            )
                            exceptions.add(failureResponse)
                        }
                        
                        // Objects.requireNonNull 패턴
                        className == "java.util.Objects" && methodName == "requireNonNull" -> {
                            val failureResponse = createFailureResponse(
                                "java.lang.NullPointerException",
                                "Required object is null",
                                "Objects.requireNonNull"
                            )
                            exceptions.add(failureResponse)
                        }
                        
                        // Preconditions 패턴 (Guava)
                        className.contains("Preconditions") -> {
                            val exceptionType = when (methodName) {
                                "checkArgument" -> "java.lang.IllegalArgumentException"
                                "checkState" -> "java.lang.IllegalStateException"
                                "checkNotNull" -> "java.lang.NullPointerException"
                                else -> "java.lang.IllegalArgumentException"
                            }
                            val failureResponse = createFailureResponse(
                                exceptionType,
                                "Precondition failed: $methodName",
                                "Preconditions.$methodName"
                            )
                            exceptions.add(failureResponse)
                        }
                        
                        // Repository findById().orElseThrow() 패턴 감지
                        methodName.startsWith("findBy") && className.contains("Repository") -> {
                            // 다음 instruction이 orElseThrow인지 확인하는 로직이 필요
                            // 간소화하여 Repository 조회 시 NotFoundException 가능성 추가
                            val failureResponse = createFailureResponse(
                                "NotFoundException",
                                "Entity not found",
                                "Repository.$methodName"
                            )
                            exceptions.add(failureResponse)
                        }
                    }
                }
                
                is InvokeDynamicInsnNode -> {
                    // Lambda 표현식에서의 예외 생성 패턴
                    if (instruction.name == "get" || instruction.name == "apply") {
                        val exceptionType = findLambdaExceptionType(methodNode, instruction)
                        if (exceptionType.isNotEmpty()) {
                            val failureResponse = createFailureResponse(
                                exceptionType,
                                null,
                                "Lambda exception supplier"
                            )
                            exceptions.add(failureResponse)
                        }
                    }
                }
            }
        }
        
        return exceptions
    }
    
    /**
     * 메서드 시그니처의 throws 절을 분석합니다.
     */
    private fun analyzeDeclaredExceptions(methodNode: MethodNode): List<FailureResponse> {
        val exceptions = mutableListOf<FailureResponse>()
        
        methodNode.exceptions?.forEach { exceptionType ->
            val className = exceptionType.replace('/', '.')
            if (isExceptionClass(className)) {
                val failureResponse = createFailureResponse(
                    className,
                    null,
                    "Declared in method signature"
                )
                exceptions.add(failureResponse)
            }
        }
        
        return exceptions
    }
    
    /**
     * 예외 메시지를 추출합니다.
     */
    private fun extractExceptionMessage(methodNode: MethodNode, beforeInstruction: AbstractInsnNode): String? {
        // 이전 instruction들에서 String 리터럴 찾기
        var current = beforeInstruction.previous
        var depth = 0
        
        while (current != null && depth < 10) { // 최대 10개 instruction만 역추적
            if (current is LdcInsnNode && current.cst is String) {
                return current.cst as String
            }
            current = current.previous
            depth++
        }
        
        return null
    }
    
    /**
     * Lambda에서 생성되는 예외 타입을 찾습니다.
     */
    private fun findLambdaExceptionType(methodNode: MethodNode, invokeDynamic: AbstractInsnNode): String {
        // InvokeDynamic 이후의 instruction들에서 예외 타입 찾기
        var current = invokeDynamic.next
        var depth = 0
        
        while (current != null && depth < 20) { // 최대 20개 instruction 탐색
            when (current) {
                is TypeInsnNode -> {
                    if (current.opcode == org.objectweb.asm.Opcodes.NEW) {
                        val className = current.desc.replace('/', '.')
                        if (isExceptionClass(className)) {
                            return className
                        }
                    }
                }
                is MethodInsnNode -> {
                    val className = current.owner.replace('/', '.')
                    if (isExceptionClass(className) && current.name == "<init>") {
                        return className
                    }
                }
            }
            current = current.next
            depth++
        }
        
        // 기본값으로 일반적인 예외 반환
        return "java.lang.IllegalArgumentException"
    }
    
    /**
     * FailureResponse를 생성합니다.
     */
    private fun createFailureResponse(
        exceptionType: String,
        message: String?,
        detectedAt: String
    ): FailureResponse {
        val cleanExceptionType = exceptionType.substringAfterLast('.')
        val fullExceptionType = if (exceptionType.contains('.')) exceptionType else "unknown.$exceptionType"
        
        val statusCode = EXCEPTION_STATUS_MAPPING[fullExceptionType] 
            ?: EXCEPTION_STATUS_MAPPING[cleanExceptionType] 
            ?: EXCEPTION_STATUS_MAPPING[exceptionType]
            ?: 500
            
        val description = message 
            ?: EXCEPTION_DESCRIPTIONS[fullExceptionType]
            ?: EXCEPTION_DESCRIPTIONS[cleanExceptionType]
            ?: EXCEPTION_DESCRIPTIONS[exceptionType]
            ?: "예외 발생"
        
        // detectedAt을 사용자 친화적으로 변경
        val userFriendlyLocation = when {
            detectedAt.contains("Service:") -> "Service Layer"
            detectedAt.contains("Direct throw") -> "Controller Layer"
            detectedAt.contains("orElseThrow") -> "Data Access Layer"
            detectedAt.contains("Assert") -> "Validation Layer"
            detectedAt.contains("Preconditions") -> "Validation Layer"
            detectedAt.contains("Repository") -> "Data Access Layer"
            detectedAt.contains("Lambda") -> "Business Logic Layer"
            detectedAt.contains("Declared") -> "Method Signature"
            else -> "Application Layer"
        }
        
        return FailureResponse(
            statusCode = statusCode,
            exceptionType = cleanExceptionType,
            description = description,
            detectedAt = userFriendlyLocation
        )
    }
    
    /**
     * 클래스가 예외 클래스인지 확인합니다.
     */
    private fun isExceptionClass(className: String): Boolean {
        return className.endsWith("Exception") || 
               className.endsWith("Error") ||
               className.contains("Exception") ||
               className.contains("Error") ||
               className == "java.lang.Throwable" ||
               EXCEPTION_STATUS_MAPPING.containsKey(className)
    }
} 
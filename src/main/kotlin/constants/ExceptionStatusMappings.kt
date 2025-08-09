package constants

/**
 * Exception Status Code Mappings
 * 예외 타입별 HTTP 상태 코드 매핑 상수들
 * 프로젝트 코드에서 @ResponseStatus나 명시적 상태 코드가 없을 때 사용하는 기본 매핑
 */
object ExceptionStatusMappings {
    
    /**
     * 예외 타입별 HTTP 상태 코드 매핑
     * 예외 클래스명을 키로 하고 해당하는 HTTP 상태 코드를 값으로 하는 매핑
     */
    val EXCEPTION_TO_STATUS_CODE = mapOf(
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
     * 예외 타입에 대한 HTTP 상태 코드를 반환합니다.
     * 우선순위: Full 클래스명 > Simple 클래스명 > 원본 이름 > null
     */
    fun getStatusCode(exceptionType: String): Int? {
        val cleanExceptionType = exceptionType.substringAfterLast('.')
        val fullExceptionType = if (exceptionType.contains('.')) exceptionType else "unknown.$exceptionType"
        
        return EXCEPTION_TO_STATUS_CODE[fullExceptionType]
            ?: EXCEPTION_TO_STATUS_CODE[cleanExceptionType]
            ?: EXCEPTION_TO_STATUS_CODE[exceptionType]
    }
    
    /**
     * 주어진 클래스명이 매핑에 포함되어 있는지 확인합니다.
     */
    fun containsException(className: String): Boolean {
        return EXCEPTION_TO_STATUS_CODE.containsKey(className)
    }
}
package constants

/**
 * 에러 설명 상수들
 * 프로젝트 코드에서 명시적으로 정의되지 않은 경우 사용하는 기본 설명들
 */
object ErrorDescriptions {
    
    /**
     * 예외 타입별 설명
     * 예외 클래스에 @ResponseStatus나 명시적 메시지가 없을 때 사용
     */
    val EXCEPTION_TYPE_DESCRIPTIONS = mapOf(
        // Java Standard Exceptions
        "java.lang.IllegalArgumentException" to "Invalid argument provided",
        "java.lang.IllegalStateException" to "Object is in invalid state",
        "java.lang.NullPointerException" to "Null reference access",
        "java.lang.RuntimeException" to "Runtime error",
        "java.lang.Exception" to "General error",
        
        // Spring Framework Exceptions
        "org.springframework.web.bind.MethodArgumentNotValidException" to "Request data validation failed",
        "org.springframework.web.HttpRequestMethodNotSupportedException" to "HTTP method not supported",
        "org.springframework.web.HttpMediaTypeNotSupportedException" to "Media type not supported",
        "org.springframework.web.bind.MissingServletRequestParameterException" to "Required request parameter missing",
        "org.springframework.web.method.annotation.MethodArgumentTypeMismatchException" to "Request parameter type mismatch",
        "org.springframework.security.access.AccessDeniedException" to "Access denied",
        "org.springframework.security.authentication.AuthenticationException" to "Authentication failed",
        "org.springframework.dao.DataAccessException" to "Data access error",
        "org.springframework.dao.EmptyResultDataAccessException" to "Data not found",
        "org.springframework.dao.DataIntegrityViolationException" to "Data integrity violation",
        
        // Common Custom Exception Patterns
        "BadRequestException" to "Bad request",
        "NotFoundException" to "Resource not found",
        "NotFoundExceptionException" to "Resource not found",
        "AuthException" to "Authentication failed",
        "AuthenticationException" to "Authentication failed",
        "AuthorizationException" to "Authorization failed",
        "ForbiddenException" to "Access forbidden",
        "UnauthorizedException" to "Unauthorized access",
        "ConflictException" to "Resource conflict",
        "ValidationException" to "Validation failed",
        "InvalidRequestException" to "Invalid request format",
        "ServiceException" to "Service processing error",
        "InternalServerException" to "Internal server error",
        "BusinessException" to "Business logic error",
        "DomainException" to "Domain rule violation",
        "EntityNotFoundException" to "Entity not found",
        
        // Security Related Exceptions
        "JwtException" to "JWT token error",
        "TokenExpiredException" to "Token expired",
        "InvalidTokenException" to "Invalid token",
        
        // File/Network Related Exceptions
        "MaxUploadSizeExceededException" to "Upload file size exceeded",
        "ConnectException" to "External service connection failed",
        "TimeoutException" to "Request timeout"
    )
    
    /**
     * HTTP 상태 코드별 설명
     * ResponseEntity에서 명시적 메시지가 없을 때 사용
     */
    val HTTP_STATUS_DESCRIPTIONS = mapOf(
        // 1xx Informational
        100 to "Continue",
        101 to "Switching Protocols",
        102 to "Processing",
        103 to "Early Hints",
        
        // 2xx Success
        200 to "OK",
        201 to "Created",
        202 to "Accepted",
        203 to "Non-Authoritative Information",
        204 to "No Content",
        205 to "Reset Content",
        206 to "Partial Content",
        207 to "Multi-Status",
        208 to "Already Reported",
        226 to "IM Used",
        
        // 3xx Redirection
        300 to "Multiple Choices",
        301 to "Moved Permanently",
        302 to "Found",
        303 to "See Other",
        304 to "Not Modified",
        307 to "Temporary Redirect",
        308 to "Permanent Redirect",
        
        // 4xx Client Error
        400 to "Bad Request",
        401 to "Unauthorized",
        402 to "Payment Required",
        403 to "Forbidden",
        404 to "Not Found",
        405 to "Method Not Allowed",
        406 to "Not Acceptable",
        407 to "Proxy Authentication Required",
        408 to "Request Timeout",
        409 to "Conflict",
        410 to "Gone",
        411 to "Length Required",
        412 to "Precondition Failed",
        413 to "Payload Too Large",
        414 to "URI Too Long",
        415 to "Unsupported Media Type",
        416 to "Range Not Satisfiable",
        417 to "Expectation Failed",
        418 to "I'm a teapot",
        422 to "Unprocessable Entity",
        423 to "Locked",
        424 to "Failed Dependency",
        425 to "Too Early",
        426 to "Upgrade Required",
        428 to "Precondition Required",
        429 to "Too Many Requests",
        431 to "Request Header Fields Too Large",
        451 to "Unavailable For Legal Reasons",
        
        // 5xx Server Error
        500 to "Internal Server Error",
        501 to "Not Implemented",
        502 to "Bad Gateway",
        503 to "Service Unavailable",
        504 to "Gateway Timeout",
        505 to "HTTP Version Not Supported",
        506 to "Variant Also Negotiates",
        507 to "Insufficient Storage",
        508 to "Loop Detected",
        509 to "Bandwidth Limit Exceeded",
        510 to "Not Extended",
        511 to "Network Authentication Required"
    )
    
    /**
     * 예외 타입에 대한 설명을 반환합니다.
     * 우선순위: 명시적 메시지 > 사전 정의된 설명 > 기본 메시지
     */
    fun getExceptionDescription(
        exceptionType: String,
        explicitMessage: String? = null,
        defaultMessage: String = "Exception occurred"
    ): String {
        if (!explicitMessage.isNullOrBlank()) {
            return explicitMessage
        }
        
        val cleanExceptionType = exceptionType.substringAfterLast('.')
        val fullExceptionType = if (exceptionType.contains('.')) exceptionType else "unknown.$exceptionType"
        
        return EXCEPTION_TYPE_DESCRIPTIONS[fullExceptionType]
            ?: EXCEPTION_TYPE_DESCRIPTIONS[cleanExceptionType]
            ?: EXCEPTION_TYPE_DESCRIPTIONS[exceptionType]
            ?: defaultMessage
    }
    
    /**
     * HTTP 상태 코드에 대한 설명을 반환합니다.
     * 우선순위: 명시적 메시지 > 사전 정의된 설명 > 기본 메시지
     */
    fun getStatusCodeDescription(
        statusCode: Int,
        explicitMessage: String? = null
    ): String {
        if (!explicitMessage.isNullOrBlank()) {
            return explicitMessage
        }
        
        return HTTP_STATUS_DESCRIPTIONS[statusCode] ?: "HTTP $statusCode Response"
    }
}
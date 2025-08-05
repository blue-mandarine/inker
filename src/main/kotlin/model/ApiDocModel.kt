package model

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * API 엔드포인트 정보를 담는 데이터 클래스
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiEndpoint(
    val path: String,
    val method: String,
    val controllerClass: String,
    val methodName: String,
    val description: String? = null,
    val parameters: List<ApiParameter> = emptyList(),
    val responses: ApiResponses? = null,
    val produces: List<String> = emptyList(),
    val consumes: List<String> = emptyList()
)

/**
 * API 응답 정보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponses(
    val success: SuccessResponse? = null,
    val failures: List<FailureResponse> = emptyList()
)

/**
 * 성공 응답 정보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SuccessResponse(
    val statusCode: Int = 200,
    val type: String,
    val description: String? = null
)

/**
 * 실패 응답 정보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FailureResponse(
    val statusCode: Int,
    val exceptionType: String,
    val description: String? = null,
    val detectedAt: String? = null // 예외가 발견된 위치 (바이트코드 오프셋 등)
)

/**
 * API 파라미터 정보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiParameter(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val source: ParameterSource,
    val description: String? = null,
    val defaultValue: String? = null
)

/**
 * 파라미터 소스 타입
 */
enum class ParameterSource {
    PATH_VARIABLE,
    REQUEST_PARAM,
    REQUEST_HEADER,
    REQUEST_BODY
}

/**
 * 요청 본문 정보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RequestBodyInfo(
    val type: String,
    val required: Boolean = true,
    val description: String? = null
)

/**
 * 컨트롤러 정보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ControllerInfo(
    val className: String,
    val baseMapping: String = "",
    val description: String? = null,
    val endpoints: List<ApiEndpoint> = emptyList()
)

/**
 * 전체 API 문서
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiDocumentation(
    val title: String,
    val version: String = "1.0.0",
    val description: String? = null,
    val controllers: List<ControllerInfo> = emptyList(),
    val generatedAt: String = java.time.LocalDateTime.now().toString()
) 
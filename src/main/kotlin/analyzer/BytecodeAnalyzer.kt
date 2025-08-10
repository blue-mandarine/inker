package analyzer

import constants.ErrorDescriptions
import model.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.*
import utils.ResponseEntityParser
import java.io.File
import java.io.FileInputStream

/**
 * 바이트코드 분석기
 * Spring Controller와 Service Layer를 분석하여 API 엔드포인트와 예외 정보를 추출합니다.
 */
class BytecodeAnalyzer {
    private val adviceAnalyzer = AdviceAnalyzer()
    private val exceptionAnalyzer = ExceptionAnalyzer(adviceAnalyzer)
    private val callGraphAnalyzer = CallGraphAnalyzer(exceptionAnalyzer)
    
    companion object {
        /**
         * 클래스 파일을 분석하여 ClassNode를 반환합니다.
         */
        fun analyzeClassFile(classFile: File): ClassNode {
            val classReader = ClassReader(FileInputStream(classFile))
            val classNode = ClassNode()
            classReader.accept(classNode, 0)
            return classNode
        }
    }
    
    /**
     * 클래스 파일들을 분석하여 Controller 정보를 추출합니다.
     */
    fun analyzeControllers(classFiles: List<File>): List<ControllerInfo> {
        val controllers = mutableListOf<ControllerInfo>()
        
        println("📊 전체 분석 시작...")
        
        // 1단계: ControllerAdvice 클래스들 먼저 로드
        adviceAnalyzer.loadAdviceClasses(classFiles)
        
        // 2단계: @ResponseStatus 어노테이션 정보 수집
        exceptionAnalyzer.collectResponseStatusInfo(classFiles)
        
        // 3단계: 비즈니스 로직 클래스들 로드
        callGraphAnalyzer.loadBusinessLogicClasses(classFiles)
        
        // 4단계: Controller 클래스들 분석
        println("📊 Controller 분석 시작...")
        classFiles.forEach { classFile ->
            try {
                val classNode = analyzeClassFile(classFile)
                
                if (isControllerClass(classNode)) {
                    val className = classNode.name.replace('/', '.')
                    val baseMapping = extractClassLevelMapping(classNode)
                    val endpoints = extractEndpoints(classNode, className, baseMapping)
                    
                    if (endpoints.isNotEmpty()) {
                        controllers.add(ControllerInfo(className, baseMapping, null, endpoints))
                        println("✅ Controller 발견: $className")
                        println("   - 기본 매핑: $baseMapping")
                        println("   - 엔드포인트 수: ${endpoints.size}")
                        endpoints.forEach { endpoint ->
                            println("     • ${endpoint.method} ${endpoint.path} -> ${endpoint.methodName}()")
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ 클래스 분석 실패: ${classFile.name} - ${e.message}")
            }
        }
        
        // 3단계: 통계 정보 출력
        val stats = callGraphAnalyzer.getStatistics()
        println("\n📊 분석 결과:")
        println("   - 프로젝트: ${System.getProperty("project.name", "Unknown")}")
        println("   - 경로: ${System.getProperty("project.path", "Unknown")}")
        println("   - 총 Controller 수: ${controllers.size}")
        println("   - 총 엔드포인트 수: ${controllers.sumOf { it.endpoints.size }}")
        println("   - 비즈니스 로직 클래스 수: ${stats["businessLogicClasses"]}")
        println("   - 비즈니스 로직 메서드 수: ${stats["businessLogicMethods"]}")
        val totalExceptions = controllers.sumOf { controller ->
            controller.endpoints.sumOf { endpoint ->
                endpoint.responses?.failures?.size ?: 0
            }
        }
        println("   - 발견된 예외 수: $totalExceptions")
        
        return controllers
    }

    /**
     * Controller 클래스인지 확인합니다.
     */
    private fun isControllerClass(classNode: ClassNode): Boolean {
        classNode.visibleAnnotations?.forEach { annotation ->
            when (annotation.desc) {
                "Lorg/springframework/stereotype/Controller;",
                "Lorg/springframework/web/bind/annotation/RestController;" -> return true
            }
        }
        return false
    }

    /**
     * 클래스 레벨의 RequestMapping을 추출합니다.
     */
    private fun extractClassLevelMapping(classNode: ClassNode): String {
        classNode.visibleAnnotations?.forEach { annotation ->
            if (annotation.desc == "Lorg/springframework/web/bind/annotation/RequestMapping;") {
                val value = extractMappingValue(annotation.values)
                if (value.isNotEmpty()) return value
            }
        }
        return ""
    }

    /**
     * 엔드포인트들을 추출합니다.
     */
    private fun extractEndpoints(classNode: ClassNode, className: String, baseMapping: String): List<ApiEndpoint> {
        val endpoints = mutableListOf<ApiEndpoint>()

        for (method in classNode.methods) {
            val methodNode = method as MethodNode
            val mappingInfo = extractMethodMapping(methodNode)

            if (mappingInfo != null) {
                val parameters = extractParameters(methodNode)
                val fullPath = combinePaths(baseMapping, mappingInfo.path)
                val responses = analyzeResponses(methodNode, className) // className 추가

                val endpoint = ApiEndpoint(
                    path = fullPath,
                    method = mappingInfo.httpMethod,
                    controllerClass = className,
                    methodName = methodNode.name,
                    parameters = parameters,
                    responses = responses
                )
                endpoints.add(endpoint)
            }
        }
        return endpoints
    }

    /**
     * 메서드의 매핑 정보를 추출합니다.
     */
    private fun extractMethodMapping(methodNode: MethodNode): MappingInfo? {
        methodNode.visibleAnnotations?.forEach { annotation ->
            when (annotation.desc) {
                "Lorg/springframework/web/bind/annotation/RequestMapping;" -> {
                    val path = extractMappingValue(annotation.values)
                    val httpMethod = extractHttpMethod(annotation.values) ?: "GET"
                    return MappingInfo(path, httpMethod)
                }
                "Lorg/springframework/web/bind/annotation/GetMapping;" -> {
                    val path = extractMappingValue(annotation.values)
                    return MappingInfo(path, "GET")
                }
                "Lorg/springframework/web/bind/annotation/PostMapping;" -> {
                    val path = extractMappingValue(annotation.values)
                    return MappingInfo(path, "POST")
                }
                "Lorg/springframework/web/bind/annotation/PutMapping;" -> {
                    val path = extractMappingValue(annotation.values)
                    return MappingInfo(path, "PUT")
                }
                "Lorg/springframework/web/bind/annotation/DeleteMapping;" -> {
                    val path = extractMappingValue(annotation.values)
                    return MappingInfo(path, "DELETE")
                }
                "Lorg/springframework/web/bind/annotation/PatchMapping;" -> {
                    val path = extractMappingValue(annotation.values)
                    return MappingInfo(path, "PATCH")
                }
            }
        }
        return null
    }

    /**
     * 매핑 어노테이션에서 값을 추출합니다.
     */
    private fun extractMappingValue(values: List<Any>?): String {
        if (values == null) return ""
        
        for (i in values.indices step 2) {
            val key = values[i] as? String
            if (key == "value" || key == "path") {
                val valueArray = values.getOrNull(i + 1)
                if (valueArray is List<*> && valueArray.isNotEmpty()) {
                    return valueArray[0] as? String ?: ""
                }
            }
        }
        return ""
    }

    /**
     * HTTP 메서드를 추출합니다.
     */
    private fun extractHttpMethod(values: List<Any>?): String? {
        if (values == null) return null
        
        for (i in values.indices step 2) {
            val key = values[i] as? String
            if (key == "method") {
                val methodArray = values.getOrNull(i + 1)
                if (methodArray is List<*> && methodArray.isNotEmpty()) {
                    val methodEnum = methodArray[0] as? List<*>
                    if (methodEnum != null && methodEnum.size >= 2) {
                        return extractRequestMethodFromEnum(methodEnum[1] as String)
                    }
                }
            }
        }
        return null
    }

    /**
     * RequestMethod enum에서 HTTP 메서드를 추출합니다.
     */
    private fun extractRequestMethodFromEnum(enumValue: String): String {
        return when (enumValue) {
            "GET" -> "GET"
            "POST" -> "POST"
            "PUT" -> "PUT"
            "DELETE" -> "DELETE"
            "PATCH" -> "PATCH"
            "HEAD" -> "HEAD"
            "OPTIONS" -> "OPTIONS"
            "TRACE" -> "TRACE"
            else -> "GET"
        }
    }

    /**
     * 메서드 파라미터들을 추출합니다.
     */
    private fun extractParameters(methodNode: MethodNode): List<ApiParameter> {
        val parameters = mutableListOf<ApiParameter>()
        
        // 파라미터 어노테이션 분석
        methodNode.visibleParameterAnnotations?.forEachIndexed { paramIndex, annotations ->
            if (annotations != null) {
                for (annotation in annotations) {
                    when (annotation.desc) {
                        "Lorg/springframework/web/bind/annotation/PathVariable;" -> {
                            val name = extractAnnotationStringValue(annotation.values, "value") 
                                ?: extractAnnotationStringValue(annotation.values, "name") 
                                ?: "param${paramIndex + 1}"
                            val required = extractAnnotationBooleanValue(annotation.values, "required") ?: true
                            parameters.add(ApiParameter(name, "java.lang.String", required, ParameterSource.PATH_VARIABLE))
                        }
                        "Lorg/springframework/web/bind/annotation/RequestParam;" -> {
                            val name = extractAnnotationStringValue(annotation.values, "value") 
                                ?: extractAnnotationStringValue(annotation.values, "name") 
                                ?: "param${paramIndex + 1}"
                            val required = extractAnnotationBooleanValue(annotation.values, "required") ?: true
                            parameters.add(ApiParameter(name, "java.lang.String", required, ParameterSource.REQUEST_PARAM))
                        }
                        "Lorg/springframework/web/bind/annotation/RequestBody;" -> {
                            val type = extractParameterType(methodNode, paramIndex)
                            parameters.add(ApiParameter("requestBody", type, true, ParameterSource.REQUEST_BODY))
                        }
                    }
                }
            }
        }
        
        return parameters
    }

    /**
     * 파라미터의 타입을 추출합니다.
     */
    private fun extractParameterType(methodNode: MethodNode, paramIndex: Int): String {
        // TODO: 정확한 파라미터 타입 추출 필요
        // 메서드 시그니처에서 파라미터 타입 추출 (간단화된 버전)
        return "java.lang.Object" // 실제로는 더 복잡한 타입 추출 로직이 필요
    }

    /**
     * 어노테이션에서 문자열 값을 추출합니다.
     */
    private fun extractAnnotationStringValue(values: List<Any>?, key: String): String? {
        if (values == null) return null
        
        for (i in values.indices step 2) {
            if (values[i] == key) {
                return values.getOrNull(i + 1) as? String
            }
        }
        return null
    }

    /**
     * 어노테이션에서 불린 값을 추출합니다.
     */
    private fun extractAnnotationBooleanValue(values: List<Any>?, key: String): Boolean? {
        if (values == null) return null
        
        for (i in values.indices step 2) {
            if (values[i] == key) {
                return values.getOrNull(i + 1) as? Boolean
            }
        }
        return null
    }

    /**
     * 메서드의 반환 타입을 추출합니다.
     */
    private fun extractReturnType(descriptor: String): String {
        val returnTypeDesc = descriptor.substringAfterLast(')')
        return when {
            returnTypeDesc.startsWith("L") && returnTypeDesc.endsWith(";") -> {
                returnTypeDesc.substring(1, returnTypeDesc.length - 1).replace('/', '.')
            }
            returnTypeDesc == "V" -> "void"
            returnTypeDesc == "I" -> "int"
            returnTypeDesc == "Z" -> "boolean"
            returnTypeDesc == "J" -> "long"
            returnTypeDesc == "F" -> "float"
            returnTypeDesc == "D" -> "double"
            else -> returnTypeDesc
        }
    }

    /**
     * 기본 매핑과 메서드 매핑을 결합합니다.
     */
    private fun combinePaths(basePath: String, methodPath: String): String {
        val base = basePath.trimEnd('/')
        val method = methodPath.trimStart('/')
        
        return when {
            base.isEmpty() && method.isEmpty() -> "/"
            base.isEmpty() -> "/$method"
            method.isEmpty() -> base
            else -> "$base/$method"
        }
    }

    /**
     * 메서드의 응답 정보를 분석합니다. (Service Layer 예외 분석 포함)
     */
    private fun analyzeResponses(methodNode: MethodNode, controllerClass: String): ApiResponses {
        // 성공 응답 분석
        val returnType = extractReturnType(methodNode.desc)
        
        // ResponseEntity 패턴에서 상태 코드 추출
        val responseEntityStatusCodes = if (returnType.contains("ResponseEntity")) {
            ResponseEntityParser.extractStatusCodesFromMethod(methodNode)
        } else {
            emptyList()
        }
        
        // 성공 응답의 상태 코드 결정
        val successStatusCode = when {
            responseEntityStatusCodes.isNotEmpty() -> {
                // ResponseEntity에서 추출된 2xx 상태 코드 중 첫 번째
                responseEntityStatusCodes.firstOrNull { it in 200..299 } ?: 200
            }
            else -> 200
        }
        
        val successResponse = SuccessResponse(
            statusCode = successStatusCode,
            type = returnType,
            description = when {
                returnType.contains("ModelAndView") -> "Response with View and Model"
                returnType.contains("String") -> "View name or redirect path"
                returnType.contains("ResponseEntity") -> {
                    if (responseEntityStatusCodes.isNotEmpty()) {
                        "HTTP response entity (detected status codes: ${responseEntityStatusCodes.joinToString(", ")})"
                    } else {
                        "HTTP response entity"
                    }
                }
                returnType == "void" -> "No response body"
                else -> "Success response"
            }
        )

        // 실패 응답 분석
        val allFailureResponses = mutableListOf<FailureResponse>()
        
        // 1. Controller 메서드의 직접 예외들
        val controllerExceptions = exceptionAnalyzer.analyzeMethodExceptions(methodNode)
        allFailureResponses.addAll(controllerExceptions)
        
        // 2. 비즈니스 로직에서 발생하는 예외들 (Call Graph 분석)
        val businessLogicExceptions = callGraphAnalyzer.analyzeBusinessLogicExceptions(methodNode, controllerClass)
        allFailureResponses.addAll(businessLogicExceptions)
        
        // 3. ResponseEntity에서 추출된 에러 상태 코드들 (4xx, 5xx)
        responseEntityStatusCodes.filter { it >= 400 }.forEach { statusCode ->
            val errorResponse = FailureResponse(
                statusCode = statusCode,
                exceptionType = "ResponseEntity",
                description = ErrorDescriptions.getStatusCodeDescription(statusCode),
                detectedAt = "Controller Method"
            )
            allFailureResponses.add(errorResponse)
        }

        return ApiResponses(
            success = successResponse,
            failures = allFailureResponses
        )
    }

    /**
     * 매핑 정보를 담는 데이터 클래스
     */
    private data class MappingInfo(
        val path: String,
        val httpMethod: String
    )
} 

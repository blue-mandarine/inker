package utils

import org.objectweb.asm.tree.*

/**
 * ResponseEntity 패턴 파싱 유틸리티
 * Controller 메소드와 ExceptionHandler에서 공통으로 사용
 */
object ResponseEntityParser {
    
    /**
     * 메소드에서 ResponseEntity 패턴을 분석하여 가능한 상태 코드들을 추출합니다.
     */
    fun extractStatusCodesFromMethod(methodNode: MethodNode): List<Int> {
        val statusCodes = mutableSetOf<Int>()
        
        methodNode.instructions?.forEach { instruction ->
            when (instruction) {
                // ResponseEntity.status(HttpStatus.NOT_FOUND)
                is FieldInsnNode -> {
                    if (instruction.owner == "org/springframework/http/HttpStatus") {
                        ResponseStatusParser.springHttpStatusToCode(instruction.name)?.let {
                            statusCodes.add(it)
                        }
                    }
                }
                // ResponseEntity.status(404)
                is IntInsnNode -> {
                    if (instruction.operand in 100..599) {
                        statusCodes.add(instruction.operand)
                    }
                }
                // LDC instruction for status codes
                is LdcInsnNode -> {
                    if (instruction.cst is Int && instruction.cst as Int in 100..599) {
                        statusCodes.add(instruction.cst as Int)
                    }
                }
                // ResponseEntity 정적 메소드 호출 패턴
                is MethodInsnNode -> {
                    if (instruction.owner == "org/springframework/http/ResponseEntity") {
                        extractStatusFromResponseEntityMethod(instruction.name)?.let {
                            statusCodes.add(it)
                        }
                    }
                }
            }
        }
        
        return statusCodes.toList().sorted()
    }
    
    /**
     * ResponseEntity 정적 메소드에서 상태 코드를 추출합니다.
     * ResponseEntity.notFound(), ResponseEntity.badRequest() 등의 패턴 지원
     */
    private fun extractStatusFromResponseEntityMethod(methodName: String): Int? {
        return when (methodName) {
            // 2xx Success
            "ok" -> 200
            "created" -> 201
            "accepted" -> 202
            "noContent" -> 204
            
            // 3xx Redirection  
            "notModified" -> 304
            
            // 4xx Client Error
            "badRequest" -> 400
            "unauthorized" -> 401
            "forbidden" -> 403
            "notFound" -> 404
            "methodNotAllowed" -> 405
            "notAcceptable" -> 406
            "conflict" -> 409
            "gone" -> 410
            "preconditionFailed" -> 412
            "unprocessableEntity" -> 422
            "tooManyRequests" -> 429
            
            // 5xx Server Error
            "internalServerError" -> 500
            "notImplemented" -> 501
            "badGateway" -> 502
            "serviceUnavailable" -> 503
            "gatewayTimeout" -> 504
            
            else -> null
        }
    }
}
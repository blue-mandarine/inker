package analyzer

import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import utils.ResponseStatusParser
import utils.ResponseEntityParser
import java.io.File

/**
 * @ControllerAdvice와 @ExceptionHandler 분석기
 * 전역 예외 처리를 분석하여 실제 HTTP 상태 코드와 응답을 추출합니다.
 */
class AdviceAnalyzer {
    
    private val adviceClasses = mutableMapOf<String, ClassNode>()
    private val exceptionHandlers = mutableMapOf<String, ExceptionHandlerInfo>()
    
    /**
     * @ControllerAdvice/@RestControllerAdvice 클래스들을 로드합니다.
     */
    fun loadAdviceClasses(classFiles: List<File>) {
        println("📊 ControllerAdvice 클래스 로딩 시작...")
        
        classFiles.forEach { classFile ->
            try {
                val classNode = BytecodeAnalyzer.analyzeClassFile(classFile)
                
                if (isAdviceClass(classNode)) {
                    val className = classNode.name.replace('/', '.')
                    adviceClasses[className] = classNode
                    extractExceptionHandlers(classNode)
                    println("🔍 ControllerAdvice 발견: $className")
                }
            } catch (e: Exception) {
                println("⚠️  ControllerAdvice 로드 실패: ${classFile.name} - ${e.message}")
            }
        }
        
        println("✅ ControllerAdvice 로딩 완료: ${adviceClasses.size}개 클래스, ${exceptionHandlers.size}개 핸들러")
    }
    
    /**
     * 클래스가 @ControllerAdvice 또는 @RestControllerAdvice인지 확인합니다.
     */
    private fun isAdviceClass(classNode: ClassNode): Boolean {
        classNode.visibleAnnotations?.forEach { annotation ->
            when (annotation.desc) {
                "Lorg/springframework/web/bind/annotation/ControllerAdvice;",
                "Lorg/springframework/web/bind/annotation/RestControllerAdvice;" -> return true
            }
        }
        return false
    }
    
    /**
     * @ExceptionHandler 메소드들을 추출합니다.
     */
    private fun extractExceptionHandlers(classNode: ClassNode) {
        classNode.methods?.forEach { method ->
            val methodNode = method as MethodNode
            
            // @ExceptionHandler 어노테이션 확인
            methodNode.visibleAnnotations?.forEach { annotation ->
                if (annotation.desc == "Lorg/springframework/web/bind/annotation/ExceptionHandler;") {
                    val handledExceptions = extractHandledExceptions(annotation)
                    val statusCode = extractStatusFromHandler(methodNode)
                    val responseType = extractResponseType(methodNode)
                    
                    handledExceptions.forEach { exceptionType ->
                        exceptionHandlers[exceptionType] = ExceptionHandlerInfo(
                            statusCode = statusCode,
                            responseType = responseType,
                            handlerMethod = methodNode.name,
                            adviceClass = classNode.name.replace('/', '.')
                        )
                    }
                }
            }
        }
    }
    
    /**
     * @ExceptionHandler에서 처리하는 예외 타입들을 추출합니다.
     */
    private fun extractHandledExceptions(annotation: AnnotationNode): List<String> {
        val exceptions = mutableListOf<String>()
        
        // @ExceptionHandler(value = {Exception1.class, Exception2.class})
        annotation.values?.let { values ->
            var i = 0
            while (i < values.size) {
                val key = values[i] as String
                if (key == "value" && i + 1 < values.size) {
                    val valueList = values[i + 1]
                    when (valueList) {
                        is List<*> -> {
                            valueList.forEach { typeNode ->
                                if (typeNode is org.objectweb.asm.Type) {
                                    exceptions.add(typeNode.className)
                                }
                            }
                        }
                        is org.objectweb.asm.Type -> {
                            exceptions.add(valueList.className)
                        }
                    }
                    break
                }
                i += 2
            }
        }
        
        return exceptions
    }
    
    /**
     * 핸들러 메소드에서 HTTP 상태 코드를 추출합니다.
     */
    private fun extractStatusFromHandler(methodNode: MethodNode): Int? {
        // 1. @ResponseStatus 어노테이션에서 추출
        methodNode.visibleAnnotations?.forEach { annotation ->
            if (annotation.desc == "Lorg/springframework/web/bind/annotation/ResponseStatus;") {
                return extractStatusFromResponseStatus(annotation)
            }
        }
        
        // 2. ResponseEntity.status() 패턴에서 추출
        return extractStatusFromResponseEntity(methodNode)
    }
    
    /**
     * @ResponseStatus 어노테이션에서 상태 코드를 추출합니다.
     */
    private fun extractStatusFromResponseStatus(annotation: AnnotationNode): Int? {
        return ResponseStatusParser.extractStatusFromAnnotation(annotation)
    }
    
    /**
     * ResponseEntity에서 상태 코드를 추출합니다.
     */
    private fun extractStatusFromResponseEntity(methodNode: MethodNode): Int? {
        val statusCodes = ResponseEntityParser.extractStatusCodesFromMethod(methodNode)
        return statusCodes.firstOrNull() // ExceptionHandler에서는 첫 번째 상태 코드만 사용
    }
    
    /**
     * 핸들러 메소드의 반환 타입을 추출합니다.
     */
    private fun extractResponseType(methodNode: MethodNode): String? {
        // 메소드 descriptor에서 반환 타입 추출
        val returnType = org.objectweb.asm.Type.getReturnType(methodNode.desc)
        return returnType.className
    }
    
    /**
     * 특정 예외가 @ExceptionHandler로 처리되는지 확인합니다.
     */
    fun getHandlerInfo(exceptionType: String): ExceptionHandlerInfo? {
        // 정확한 클래스명 매칭
        exceptionHandlers[exceptionType]?.let { return it }
        
        // 단순 클래스명으로 매칭 (패키지 제외)
        val simpleClassName = exceptionType.substringAfterLast('.')
        return exceptionHandlers.values.find { 
            it.handlerMethod.contains(simpleClassName) || 
            exceptionHandlers.keys.any { key -> key.endsWith(".$simpleClassName") }
        }
    }
}

/**
 * 예외 핸들러 정보를 담는 데이터 클래스
 */
data class ExceptionHandlerInfo(
    val statusCode: Int?,
    val responseType: String?,
    val handlerMethod: String,
    val adviceClass: String
)

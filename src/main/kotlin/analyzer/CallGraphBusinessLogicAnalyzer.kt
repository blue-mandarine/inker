package analyzer

import model.*
import org.objectweb.asm.tree.*
import java.io.File

/**
 * Call Graph 분석기
 * Controller에서 호출되는 모든 비즈니스 로직 메서드를 추적하고, 각 Layer의 예외를 분석합니다.
 */
class CallGraphBusinessLogicAnalyzer(
    private val exceptionAnalyzer: ExceptionAnalyzer
) {
    
    private val businessLogicClasses = mutableMapOf<String, ClassNode>() // 클래스명 -> ClassNode
    private val businessLogicMethodNodes = mutableMapOf<String, MethodNode>() // 메서드시그니처 -> MethodNode
    
    /**
     * 비즈니스 로직 클래스들을 로드합니다.
     */
    fun loadBusinessLogicClasses(classFiles: List<File>) {
        println("📊 비즈니스 로직 클래스 로딩 시작...")
        
        classFiles.forEach { classFile ->
            try {
                val classNode = BytecodeAnalyzer.analyzeClassFile(classFile)
                val className = classNode.name.replace('/', '.')
                
                // 어노테이션 기반으로 비즈니스 로직 클래스들을 식별
                if (isBusinessLogicClass(classNode, className)) {
                    businessLogicClasses[className] = classNode
                    
                    // 메서드들도 인덱싱
                    classNode.methods?.forEach { method ->
                        val methodNode = method as MethodNode
                        val methodSignature = "$className.${methodNode.name}${methodNode.desc}"
                        businessLogicMethodNodes[methodSignature] = methodNode
                    }
                    
                    println("🔍 비즈니스 로직 클래스 로드: $className (메서드 ${classNode.methods?.size ?: 0}개)")
                }
            } catch (e: Exception) {
                println("⚠️  클래스 로드 실패: ${classFile.name} - ${e.message}")
            }
        }
        
        println("✅ 비즈니스 로직 클래스 로딩 완료: ${businessLogicClasses.size}개 클래스, ${businessLogicMethodNodes.size}개 메서드")
    }
    
    /**
     * 비즈니스 로직 관련 클래스인지 확인합니다.
     */
    private fun isBusinessLogicClass(classNode: ClassNode, className: String): Boolean {
        // 어노테이션으로만 확인 - 클래스명 패턴은 제거
        classNode.visibleAnnotations?.forEach { annotation ->
            val annotationType = annotation.desc
            if (annotationType.contains("Service") || 
                annotationType.contains("Repository") || 
                annotationType.contains("Component") ||
                annotationType.contains("Configuration") ||
                annotationType.contains("Bean")) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Controller 메서드에서 호출되는 비즈니스 로직 메서드들을 분석하고, 각 Layer에서 발생하는 예외를 추적합니다.
     */
    fun analyzeBusinessLogicExceptions(controllerMethod: MethodNode, controllerClass: String): List<FailureResponse> {
        val businessLogicExceptions = mutableListOf<FailureResponse>()
        
        // Controller 메서드에서 호출되는 비즈니스 로직 메서드들을 찾기
        val businessLogicCalls = findBusinessLogicCalls(controllerMethod)
        
        businessLogicCalls.forEach { businessLogicCall ->
            try {
                val businessLogicMethod = businessLogicMethodNodes[businessLogicCall]
                if (businessLogicMethod != null) {
                    // 호출 체인 예외 분석
                    val exceptions = analyzeDeepBusinessLogicExceptions(businessLogicMethod)
                    exceptions.forEach { exception ->
                        // 비즈니스 로직 예외를 Controller와 연결
                        val enhancedException = exception.copy(
                            detectedAt = if (exception.detectedAt == "Direct throw") "Business Logic Layer" else exception.detectedAt
                        )
                        businessLogicExceptions.add(enhancedException)
                    }
                    
                    println("🔗 Deep 비즈니스 로직 호출 분석: $businessLogicCall -> ${exceptions.size}개 예외 발견 (다층 체인 포함)")
                } else {
                    println("⚠️  비즈니스 로직 메서드를 찾을 수 없음: $businessLogicCall")
                }
            } catch (e: Exception) {
                println("❌ 비즈니스 로직 예외 분석 오류: $businessLogicCall - ${e.message}")
            }
        }
        
        return businessLogicExceptions
    }
    
    /**
     * Controller 메서드에서 호출되는 비즈니스 로직 메서드들을 찾습니다.
     */
    private fun findBusinessLogicCalls(methodNode: MethodNode): List<String> {
        val businessLogicCalls = mutableListOf<String>()
        
        methodNode.instructions?.forEach { instruction ->
            when (instruction) {
                is MethodInsnNode -> {
                    val className = instruction.owner.replace('/', '.')
                    val methodName = instruction.name
                    val methodDesc = instruction.desc
                    val fullMethodSignature = "$className.$methodName$methodDesc"
                    
                    // 비즈니스 로직 클래스의 메서드인지 확인
                    if (businessLogicClasses.containsKey(className)) {
                        businessLogicCalls.add(fullMethodSignature)
                    }
                }
                is InvokeDynamicInsnNode -> {
                    // Lambda 표현식 등에서의 메서드 호출도 처리
                    try {
                        val handle = instruction.bsm
                        if (handle != null) {
                            val className = handle.owner?.replace('/', '.')
                            if (className != null && businessLogicClasses.containsKey(className)) {
                                val methodSignature = "$className.${handle.name}${handle.desc}"
                                businessLogicCalls.add(methodSignature)
                            }
                        }
                    } catch (e: Exception) {
                        // InvokeDynamic 분석 실패는 무시
                    }
                }
            }
        }
        
        return businessLogicCalls.distinct()
    }
    
    /**
     * 비즈니스 로직 메서드에서 다른 비즈니스 로직을 호출하는 경우도 재귀적으로 분석합니다.
     */
    fun analyzeDeepBusinessLogicExceptions(businessLogicMethod: MethodNode, visited: MutableSet<String> = mutableSetOf()): List<FailureResponse> {
        val methodSignature = "${businessLogicMethod.name}${businessLogicMethod.desc}"
        
        // 무한 재귀 방지
        if (visited.contains(methodSignature)) {
            return emptyList()
        }
        visited.add(methodSignature)
        
        val exceptions = mutableListOf<FailureResponse>()
        
        // 현재 메서드의 직접 예외들
        exceptions.addAll(exceptionAnalyzer.analyzeMethodExceptions(businessLogicMethod))
        
        // 호출하는 다른 비즈니스 로직 메서드들의 예외들
        val nestedBusinessLogicCalls = findBusinessLogicCalls(businessLogicMethod)
        nestedBusinessLogicCalls.forEach { businessLogicCall ->
            val nestedMethod = businessLogicMethodNodes[businessLogicCall]
            if (nestedMethod != null) {
                exceptions.addAll(analyzeDeepBusinessLogicExceptions(nestedMethod, visited))
            }
        }
        
        return exceptions
    }
    
    /**
     * 통계 정보를 반환합니다.
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "businessLogicClasses" to businessLogicClasses.size,
            "businessLogicMethods" to businessLogicMethodNodes.size,
            "businessLogicClassNames" to businessLogicClasses.keys.sorted().take(10) // 처음 10개만
        )
    }
} 

package analyzer

import model.*
import org.objectweb.asm.tree.*
import java.io.File
import java.io.FileInputStream
import java.util.jar.JarFile

/**
 * Call Graph 분석기
 * Controller에서 Service 메서드 호출을 추적하고, Service Layer의 예외를 분석합니다.
 */
class CallGraphAnalyzer(
    private val exceptionAnalyzer: ExceptionAnalyzer
) {
    
    private val serviceClasses = mutableMapOf<String, ClassNode>() // 클래스명 -> ClassNode
    private val serviceMethodNodes = mutableMapOf<String, MethodNode>() // 메서드시그니처 -> MethodNode
    
    /**
     * Service Layer 클래스들을 로드합니다.
     */
    fun loadServiceClasses(classFiles: List<File>) {
        println("📊 Service Layer 클래스 로딩 시작...")
        
        classFiles.forEach { classFile ->
            try {
                val classNode = BytecodeAnalyzer.analyzeClassFile(classFile)
                val className = classNode.name.replace('/', '.')
                
                // Service, Repository, Component 등의 클래스들을 포함
                if (isServiceClass(classNode, className)) {
                    serviceClasses[className] = classNode
                    
                    // 메서드들도 인덱싱
                    classNode.methods?.forEach { method ->
                        val methodNode = method as MethodNode
                        val methodSignature = "$className.${methodNode.name}${methodNode.desc}"
                        serviceMethodNodes[methodSignature] = methodNode
                    }
                    
                    println("🔍 Service 클래스 로드: $className (메서드 ${classNode.methods?.size ?: 0}개)")
                }
            } catch (e: Exception) {
                println("⚠️  클래스 로드 실패: ${classFile.name} - ${e.message}")
            }
        }
        
        println("✅ Service Layer 로딩 완료: ${serviceClasses.size}개 클래스, ${serviceMethodNodes.size}개 메서드")
    }
    
    /**
     * Service 관련 클래스인지 확인합니다.
     */
    private fun isServiceClass(classNode: ClassNode, className: String): Boolean {
        // 클래스명 패턴으로 확인
        if (className.contains("Service") || 
            className.contains("Repository") || 
            className.contains("Component") ||
            className.contains("Helper") ||
            className.contains("Utils")) {
            return true
        }
        
        // 어노테이션으로 확인
        classNode.visibleAnnotations?.forEach { annotation ->
            val annotationType = annotation.desc
            if (annotationType.contains("Service") || 
                annotationType.contains("Repository") || 
                annotationType.contains("Component")) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Controller 메서드에서 Service 메서드 호출을 분석하고, Service에서 발생하는 예외를 추적합니다.
     */
    fun analyzeServiceExceptions(controllerMethod: MethodNode, controllerClass: String): List<FailureResponse> {
        val serviceExceptions = mutableListOf<FailureResponse>()
        
        // Controller 메서드에서 호출되는 Service 메서드들을 찾기
        val serviceCalls = findServiceCalls(controllerMethod)
        
        serviceCalls.forEach { serviceCall ->
            try {
                val serviceMethod = serviceMethodNodes[serviceCall]
                if (serviceMethod != null) {
                    // 호출 체인 예외 분석
                    val exceptions = analyzeDeepServiceExceptions(serviceMethod)
                    exceptions.forEach { exception ->
                        // Service 예외를 Controller와 연결
                        val enhancedException = exception.copy(
                            detectedAt = if (exception.detectedAt == "Direct throw") "Service Layer" else exception.detectedAt
                        )
                        serviceExceptions.add(enhancedException)
                    }
                    
                    println("🔗 Deep Service 호출 분석: $serviceCall -> ${exceptions.size}개 예외 발견 (다층 체인 포함)")
                } else {
                    println("⚠️  Service 메서드를 찾을 수 없음: $serviceCall")
                }
            } catch (e: Exception) {
                println("❌ Service 예외 분석 오류: $serviceCall - ${e.message}")
            }
        }
        
        return serviceExceptions
    }
    
    /**
     * Controller 메서드에서 호출되는 Service 메서드들을 찾습니다.
     */
    private fun findServiceCalls(methodNode: MethodNode): List<String> {
        val serviceCalls = mutableListOf<String>()
        
        methodNode.instructions?.forEach { instruction ->
            when (instruction) {
                is MethodInsnNode -> {
                    val className = instruction.owner.replace('/', '.')
                    val methodName = instruction.name
                    val methodDesc = instruction.desc
                    val fullMethodSignature = "$className.$methodName$methodDesc"
                    
                    // Service 클래스의 메서드인지 확인
                    if (serviceClasses.containsKey(className)) {
                        serviceCalls.add(fullMethodSignature)
                    }
                }
                is InvokeDynamicInsnNode -> {
                    // Lambda 표현식 등에서의 메서드 호출도 처리
                    try {
                        val handle = instruction.bsm
                        if (handle != null) {
                            val className = handle.owner?.replace('/', '.')
                            if (className != null && serviceClasses.containsKey(className)) {
                                val methodSignature = "$className.${handle.name}${handle.desc}"
                                serviceCalls.add(methodSignature)
                            }
                        }
                    } catch (e: Exception) {
                        // InvokeDynamic 분석 실패는 무시
                    }
                }
            }
        }
        
        return serviceCalls.distinct()
    }
    
    /**
     * Service 메서드에서 다른 Service나 Repository를 호출하는 경우도 재귀적으로 분석합니다.
     */
    fun analyzeDeepServiceExceptions(serviceMethod: MethodNode, visited: MutableSet<String> = mutableSetOf()): List<FailureResponse> {
        val methodSignature = "${serviceMethod.name}${serviceMethod.desc}"
        
        // 무한 재귀 방지
        if (visited.contains(methodSignature)) {
            return emptyList()
        }
        visited.add(methodSignature)
        
        val exceptions = mutableListOf<FailureResponse>()
        
        // 현재 메서드의 직접 예외들
        exceptions.addAll(exceptionAnalyzer.analyzeMethodExceptions(serviceMethod))
        
        // 호출하는 다른 Service 메서드들의 예외들
        val nestedServiceCalls = findServiceCalls(serviceMethod)
        nestedServiceCalls.forEach { serviceCall ->
            val nestedMethod = serviceMethodNodes[serviceCall]
            if (nestedMethod != null) {
                exceptions.addAll(analyzeDeepServiceExceptions(nestedMethod, visited))
            }
        }
        
        return exceptions
    }
    
    /**
     * 통계 정보를 반환합니다.
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "serviceClasses" to serviceClasses.size,
            "serviceMethods" to serviceMethodNodes.size,
            "serviceClassNames" to serviceClasses.keys.sorted().take(10) // 처음 10개만
        )
    }
} 

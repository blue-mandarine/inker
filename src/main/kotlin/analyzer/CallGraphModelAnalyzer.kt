package analyzer

import model.*
import org.objectweb.asm.tree.*
import utils.TypeExtractorUtils
import java.io.File
import java.io.FileInputStream

/**
 * Model Call Graph 분석기
 * RequestBody 모델의 중첩된 구조와 Enum을 분석하여 깊이 있는 모델 정보를 추출합니다.
 */
class CallGraphModelAnalyzer {
    
    private val modelClasses = mutableMapOf<String, ClassNode>() // 클래스명 -> ClassNode
    private val enumClasses = mutableMapOf<String, ClassNode>() // Enum 클래스명 -> ClassNode
    private val analyzedModels = mutableMapOf<String, RequestBodyInfo>() // 분석된 모델 정보 캐시
    
    /**
     * 모델 클래스들을 로드합니다.
     */
    fun loadModelClasses(classFiles: List<File>) {
        println("🔍 모델 클래스 로딩 시작...")
        
        classFiles.forEach { classFile ->
            try {
                val classNode = BytecodeAnalyzer.analyzeClassFile(classFile)
                val className = classNode.name.replace('/', '.')
                
                // Spring 컴포넌트가 아닌 클래스들을 모델로 인식
                if (isModelClass(classNode)) {
                    if (isEnumClass(classNode)) {
                        enumClasses[className] = classNode
                        println("🔍 Enum 클래스 로드: $className")
                    } else {
                        modelClasses[className] = classNode
                        println("🔍 모델 클래스 로드: $className")
                    }
                }
            } catch (e: Exception) {
                println("⚠️  모델 클래스 로드 실패: ${classFile.name} - ${e.message}")
            }
        }
        
        println("✅ 모델 클래스 로딩 완료: ${modelClasses.size}개 모델, ${enumClasses.size}개 Enum")
    }
    
    /**
     * Spring 컴포넌트가 아닌 클래스인지 확인합니다.
     */
    private fun isModelClass(classNode: ClassNode): Boolean {
        classNode.visibleAnnotations?.forEach { annotation ->
            when (annotation.desc) {
                "Lorg/springframework/stereotype/Controller;",
                "Lorg/springframework/web/bind/annotation/RestController;",
                "Lorg/springframework/stereotype/Service;",
                "Lorg/springframework/stereotype/Component;",
                "Lorg/springframework/stereotype/Repository;",
                "Lorg/springframework/web/bind/annotation/ControllerAdvice;",
                "Lorg/springframework/web/bind/annotation/RestControllerAdvice;" -> {
                    return false // Spring 컴포넌트를 제외한 모든 클래스 수집
                }
            }
        }
        return true
    }
    
    /**
     * Enum 클래스인지 확인합니다.
     */
    private fun isEnumClass(classNode: ClassNode): Boolean {
        return classNode.superName == "java/lang/Enum"
    }
    
    /**
     * 모델 클래스를 깊이 있게 분석합니다.
     */
    fun analyzeModelDeep(className: String, depth: Int = 0, maxDepth: Int = 5, visited: MutableSet<String> = mutableSetOf()): RequestBodyInfo? {
        // 순환 참조 방지
        if (className in visited || depth > maxDepth) {
            return null
        }
        visited.add(className)
        
        // 캐시된 결과가 있으면 반환
        analyzedModels[className]?.let { return it }
        
        try {
            val classNode = modelClasses[className] ?: enumClasses[className]
            if (classNode == null) {
                return RequestBodyInfo(type = className)
            }
            
            val modelFields = mutableListOf<ModelField>()
            
            if (isEnumClass(classNode)) {
                // Enum 클래스 분석
                val enumValues = extractEnumValues(classNode)
                modelFields.add(
                    ModelField(
                        name = "values",
                        type = "Enum Values",
                        required = false,
                        description = "Available values: ${enumValues.joinToString(", ")}"
                    )
                )
            } else {
                // 일반 모델 클래스 분석
                classNode.fields.forEach { fieldNode ->
                    val fieldName = fieldNode.name
                    val fieldType = TypeExtractorUtils.extractFieldType(fieldNode.desc)
                    val required = !fieldType.endsWith("?") && !fieldType.contains("Optional")
                    
                    // 중첩된 모델인지 확인
                    val nestedModel = if (TypeExtractorUtils.isComplexType(fieldType) && depth < maxDepth) {
                        analyzeModelDeep(fieldType, depth + 1, maxDepth, visited.toMutableSet())
                    } else null
                    
                    modelFields.add(
                        ModelField(
                            name = fieldName,
                            type = fieldType,
                            required = required,
                            description = extractFieldDescription(fieldNode),
                            nestedModel = nestedModel
                        )
                    )
                }
            }
            
            val modelInfo = RequestBodyInfo(
                type = className,
                modelFields = modelFields,
                depth = depth,
                isEnum = isEnumClass(classNode)
            )
            
            // 캐시에 저장
            analyzedModels[className] = modelInfo
            
            return modelInfo
            
        } catch (e: Exception) {
            println("⚠️  모델 깊이 분석 실패 ($className): ${e.message}")
            return RequestBodyInfo(type = className)
        }
    }

    /**
     * 필드 설명을 추출합니다.
     */
    private fun extractFieldDescription(fieldNode: FieldNode): String? {
        // TODO: Javadoc/KDoc 주석 추출 로직 추가 가능
        return null
    }
    
    /**
     * Enum 값들을 추출합니다.
     */
    private fun extractEnumValues(classNode: ClassNode): List<String> {
        val enumValues = mutableListOf<String>()
        
        classNode.fields.forEach { fieldNode ->
            if (fieldNode.access and org.objectweb.asm.Opcodes.ACC_ENUM != 0) {
                enumValues.add(fieldNode.name)
            }
        }
        
        return enumValues
    }
} 

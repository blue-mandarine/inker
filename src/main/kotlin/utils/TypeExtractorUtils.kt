package utils

import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode

/**
 * ASM 바이트코드 디스크립터를 Java 타입으로 변환하는 유틸리티 클래스
 * 중복된 타입 추출 로직을 통합하여 일관성과 유지보수성을 향상시킵니다.
 */
object TypeExtractorUtils {
    
    /**
     * ASM 타입 디스크립터를 Java 클래스명으로 변환합니다.
     * 
     * @param descriptor ASM 타입 디스크립터 (예: "Ljava/lang/String;", "[Ljava/lang/String;", "I" 등)
     * @return Java 클래스명 (예: "java.lang.String", "java.lang.String[]", "int" 등)
     */
    fun extractTypeFromDescriptor(descriptor: String): String {
        return when {
            descriptor.startsWith("L") && descriptor.endsWith(";") -> {
                // 클래스 타입 (예: Ljava/lang/String; -> java.lang.String)
                descriptor.substring(1, descriptor.length - 1).replace('/', '.')
            }
            
            descriptor.startsWith("[") -> {
                // 배열 타입 (예: [Ljava/lang/String; -> java.lang.String[])
                val elementType = descriptor.substring(1)
                if (elementType.startsWith("L") && elementType.endsWith(";")) {
                    elementType.substring(1, elementType.length - 1).replace('/', '.') + "[]"
                } else {
                    // 기본 타입 배열 (예: [I -> int[])
                    "${extractTypeFromDescriptor(elementType)}[]"
                }
            }
            
            else -> {
                // 기본 타입
                extractPrimitiveType(descriptor)
            }
        }
    }
    
    /**
     * 메서드의 특정 파라미터 타입을 추출합니다.
     * 
     * @param methodNode 메서드 노드
     * @param paramIndex 파라미터 인덱스 (0부터 시작)
     * @return Java 클래스명
     */
    fun extractParameterType(methodNode: MethodNode, paramIndex: Int): String {
        return try {
            val paramTypes = Type.getArgumentTypes(methodNode.desc)
            
            if (paramIndex < paramTypes.size) {
                val paramType = paramTypes[paramIndex]
                extractTypeFromDescriptor(paramType.descriptor)
            } else {
                "java.lang.Object"
            }
        } catch (e: Exception) {
            println("⚠️  파라미터 타입 추출 실패: ${e.message}")
            "java.lang.Object"
        }
    }
    
    /**
     * 메서드의 반환 타입을 추출합니다.
     * 
     * @param methodDescriptor 메서드 디스크립터
     * @return Java 클래스명
     */
    fun extractReturnType(methodDescriptor: String): String {
        val returnTypeDesc = methodDescriptor.substringAfterLast(')')
        return extractTypeFromDescriptor(returnTypeDesc)
    }
    
    /**
     * 필드의 타입을 추출합니다.
     * 
     * @param fieldDescriptor 필드 디스크립터
     * @return Java 클래스명
     */
    fun extractFieldType(fieldDescriptor: String): String {
        return extractTypeFromDescriptor(fieldDescriptor)
    }
    
    /**
     * 기본 타입 디스크립터를 Java 타입으로 변환합니다.
     * 
     * @param descriptor 기본 타입 디스크립터
     * @return Java 기본 타입명
     */
    private fun extractPrimitiveType(descriptor: String): String {
        return when (descriptor) {
            "V" -> "void"
            "Z" -> "boolean"
            "B" -> "byte"
            "C" -> "char"
            "S" -> "short"
            "I" -> "int"
            "J" -> "long"
            "F" -> "float"
            "D" -> "double"
            else -> descriptor // 알 수 없는 타입은 그대로 반환
        }
    }
    
    /**
     * 복잡한 타입인지 확인합니다 (중첩 분석 대상).
     * 
     * @param type Java 타입명
     * @return 복잡한 타입 여부
     */
    fun isComplexType(type: String): Boolean {
        return !type.startsWith("java.lang.") && 
               !type.startsWith("java.util.") && 
               !type.startsWith("java.time.") &&
               !type.matches(Regex("^(String|Integer|Int|Long|Double|Float|Boolean|Byte|Short|Char|void|\\[.*])$"))
    }
}

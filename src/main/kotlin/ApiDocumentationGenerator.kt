import analyzer.BytecodeAnalyzer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import model.*
import java.io.File

/**
 * API 문서 생성기
 * Spring 프로젝트의 Controller를 분석하여 API 문서를 생성합니다.
 */
class ApiDocumentationGenerator(
    private val projectPath: String,
    private val projectName: String
) {
    private val analyzer = BytecodeAnalyzer()
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    /**
     * API 문서를 생성합니다.
     */
    fun generateDocumentation(): ApiDocumentation {
        println("🚀 $projectName API 문서 생성을 시작합니다...")
        println("📂 프로젝트 경로: $projectPath")
        
        // 시스템 프로퍼티 설정 (분석기에서 사용)
        System.setProperty("project.name", projectName)
        System.setProperty("project.path", projectPath)

        // Controller 클래스 파일 찾기
        val controllerClassFiles = findControllerClassFiles()
        
        if (controllerClassFiles.isEmpty()) {
            println("⚠️  Controller 클래스 파일을 찾을 수 없습니다.")
            printBuildInstructions()
            return ApiDocumentation(
                title = "$projectName API Documentation",
                version = "1.0.0",
                description = "$projectName 프로젝트의 자동 생성된 API 문서",
                controllers = emptyList()
            )
        }

        println("📁 발견된 클래스 파일 수: ${controllerClassFiles.size}")

        // 모든 클래스 파일을 한 번에 분석 (Service Layer 포함)
        val controllers = analyzer.analyzeControllers(controllerClassFiles)

        return ApiDocumentation(
            title = "$projectName API Documentation",
            version = "1.0.0",
            description = "$projectName 프로젝트의 자동 생성된 API 문서",
            controllers = controllers
        )
    }

    /**
     * Controller 클래스 파일들을 찾습니다.
     * 다양한 빌드 도구와 구조를 지원합니다.
     */
    private fun findControllerClassFiles(): List<File> {
        val classFiles = mutableListOf<File>()
        
        // 다양한 빌드 경로에서 클래스 파일 검색
        val possiblePaths = listOf(
            // Maven 표준 경로
            "$projectPath/target/classes",
            "$projectPath/build/classes/java/main",
            
            // Kotlin Gradle 경로 (추가)
            "$projectPath/build/classes/kotlin/main",
            
            // 멀티모듈 프로젝트 (backend 등)
            "$projectPath/backend/target/classes",
            "$projectPath/backend/build/classes/java/main",
            "$projectPath/backend/build/classes/kotlin/main",
            "$projectPath/server/target/classes", 
            "$projectPath/server/build/classes/java/main",
            "$projectPath/server/build/classes/kotlin/main",
            "$projectPath/api/target/classes",
            "$projectPath/api/build/classes/java/main",
            "$projectPath/api/build/classes/kotlin/main",
            
            // IntelliJ IDEA 빌드 경로
            "$projectPath/out/production/classes",
            "$projectPath/backend/out/production/classes",
            
            // Eclipse 빌드 경로
            "$projectPath/bin/main",
            "$projectPath/backend/bin/main",
            
            // 추가 가능한 경로들
            "$projectPath/target/test-classes", // 테스트 클래스도 포함
            "$projectPath/backend/target/test-classes",
            "$projectPath/build/classes/java/test",
            "$projectPath/backend/build/classes/java/test",
            "$projectPath/build/classes/kotlin/test"
        )

        for (buildPath in possiblePaths) {
            val buildDir = File(buildPath)
            if (buildDir.exists() && buildDir.isDirectory) {
                println("📂 빌드 디렉토리 발견: $buildPath")
                collectClassFiles(buildDir, classFiles)
                break // 첫 번째로 찾은 빌드 디렉토리를 사용
            }
        }

        return classFiles
    }

    /**
     * 재귀적으로 .class 파일들을 수집합니다.
     */
    private fun collectClassFiles(directory: File, classFiles: MutableList<File>) {
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> collectClassFiles(file, classFiles)
                file.name.endsWith(".class") && !file.name.contains("$") -> {
                    classFiles.add(file)
                    println("🔍 분석 중: ${file.name}")
                }
            }
        }
    }

    /**
     * 빌드 관련 안내를 출력합니다.
     */
    private fun printBuildInstructions() {
        println("\n" + "=".repeat(60))
        println("📋 빌드 안내")
        println("=".repeat(60))
        println("프로젝트를 빌드한 후 다시 시도해주세요:")
        println()
        
        // Maven 프로젝트 확인
        if (File("$projectPath/pom.xml").exists()) {
            println("🔨 Maven 프로젝트:")
            println("   cd $projectPath")
            println("   mvn clean compile")
        }
        
        // Gradle 프로젝트 확인
        if (File("$projectPath/build.gradle").exists() || File("$projectPath/build.gradle.kts").exists()) {
            println("🔨 Gradle 프로젝트:")
            println("   cd $projectPath")
            println("   ./gradlew clean compileJava")
        }
        
        // 멀티모듈 프로젝트 확인
        if (File("$projectPath/backend").exists()) {
            println("🔨 멀티모듈 프로젝트 (backend):")
            if (File("$projectPath/backend/pom.xml").exists()) {
                println("   cd $projectPath/backend")
                println("   mvn clean compile")
            } else if (File("$projectPath/backend/build.gradle").exists() || 
                      File("$projectPath/backend/build.gradle.kts").exists()) {
                println("   cd $projectPath/backend")
                println("   ./gradlew clean compileJava")
            }
        }
        
        println("=".repeat(60))
    }

    /**
     * API 문서를 JSON 파일로 저장합니다.
     */
    fun saveToJson(apiDocumentation: ApiDocumentation, fileName: String) {
        try {
            val jsonFile = File(fileName)
            objectMapper.writeValue(jsonFile, apiDocumentation)
            println("💾 API 문서가 저장되었습니다: ${jsonFile.absolutePath}")
        } catch (e: Exception) {
            println("❌ JSON 저장 실패: ${e.message}")
        }
    }

    /**
     * API 문서를 HTML 파일로 저장합니다.
     */
    fun saveToHtml(apiDocumentation: ApiDocumentation, fileName: String) {
        try {
            val htmlContent = generateHtml(apiDocumentation.controllers, projectName, projectPath)
            val htmlFile = File(fileName)
            htmlFile.writeText(htmlContent, Charsets.UTF_8)
            println("💾 HTML API 문서가 저장되었습니다: ${htmlFile.absolutePath}")
        } catch (e: Exception) {
            println("❌ HTML 저장 실패: ${e.message}")
        }
    }

    /**
     * HTML 문서를 생성합니다.
     */
    private fun generateHtml(controllers: List<ControllerInfo>, projectName: String, projectPath: String): String {
        val totalEndpoints = controllers.sumOf { it.endpoints.size }
        val totalExceptions = controllers.sumOf { controller ->
            controller.endpoints.sumOf { endpoint ->
                endpoint.responses?.failures?.size ?: 0
            }
        }
        
        return """
        <!DOCTYPE html>
        <html lang="ko">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$projectName API Documentation</title>
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    line-height: 1.6;
                    color: #333;
                    background: #f8f9fa;
                }
                
                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    padding: 20px;
                }
                
                .header {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    color: white;
                    padding: 30px;
                    border-radius: 10px;
                    margin-bottom: 30px;
                    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
                }
                
                .header h1 {
                    font-size: 2.5em;
                    margin-bottom: 10px;
                    font-weight: 300;
                }
                
                .header .subtitle {
                    font-size: 1.1em;
                    opacity: 0.9;
                    margin-bottom: 20px;
                }
                
                .stats {
                    display: flex;
                    gap: 20px;
                    flex-wrap: wrap;
                }
                
                .stat {
                    background: rgba(255, 255, 255, 0.1);
                    padding: 10px 15px;
                    border-radius: 6px;
                    font-size: 0.9em;
                }
                
                .controller {
                    background: white;
                    border-radius: 8px;
                    margin-bottom: 20px;
                    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
                    overflow: hidden;
                }
                
                .controller-header {
                    background: #f8f9fa;
                    padding: 15px 20px;
                    border-bottom: 1px solid #e9ecef;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    transition: background-color 0.2s;
                }
                
                .controller-header:hover {
                    background: #e9ecef;
                }
                
                .controller-title {
                    font-size: 1.2em;
                    font-weight: 600;
                    color: #495057;
                }
                
                .controller-info {
                    display: flex;
                    align-items: center;
                    gap: 15px;
                    font-size: 0.9em;
                    color: #6c757d;
                }
                
                .endpoint-count {
                    background: #007bff;
                    color: white;
                    padding: 2px 8px;
                    border-radius: 12px;
                    font-size: 0.8em;
                }
                
                .toggle-icon {
                    font-size: 1.2em;
                    color: #6c757d;
                    transition: transform 0.3s;
                }
                
                .controller-content {
                    max-height: 0;
                    overflow: hidden;
                    transition: max-height 0.3s ease-out;
                }
                
                .controller-content.expanded {
                    max-height: 600px;
                    overflow-y: auto;
                    transition: max-height 0.3s ease-in;
                    scrollbar-width: thin;
                    scrollbar-color: #6c757d #f8f9fa;
                }
                
                .controller-content.expanded::-webkit-scrollbar {
                    width: 8px;
                }
                
                .controller-content.expanded::-webkit-scrollbar-track {
                    background: #f8f9fa;
                    border-radius: 4px;
                }
                
                .controller-content.expanded::-webkit-scrollbar-thumb {
                    background: #6c757d;
                    border-radius: 4px;
                }
                
                .controller-content.expanded::-webkit-scrollbar-thumb:hover {
                    background: #495057;
                }
                
                .endpoints {
                    padding: 20px;
                }
                
                .endpoint {
                    border: 1px solid #e9ecef;
                    border-radius: 6px;
                    margin-bottom: 15px;
                    overflow: hidden;
                }
                
                .endpoint-header {
                    background: #f8f9fa;
                    padding: 12px 15px;
                    border-bottom: 1px solid #e9ecef;
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                
                .method {
                    padding: 4px 8px;
                    border-radius: 4px;
                    font-size: 0.8em;
                    font-weight: 600;
                    text-transform: uppercase;
                }
                
                .method.get { background: #d4edda; color: #155724; }
                .method.post { background: #d1ecf1; color: #0c5460; }
                .method.put { background: #fff3cd; color: #856404; }
                .method.delete { background: #f8d7da; color: #721c24; }
                .method.patch { background: #e2e3e5; color: #383d41; }
                
                .path {
                    font-family: 'Monaco', 'Consolas', monospace;
                    font-size: 0.9em;
                    color: #495057;
                    font-weight: 500;
                }
                
                .method-name {
                    color: #6c757d;
                    font-size: 0.8em;
                    font-style: italic;
                }
                
                .endpoint-content {
                    padding: 15px;
                }
                
                .section {
                    margin-bottom: 20px;
                }
                
                .section-title {
                    font-size: 1em;
                    font-weight: 600;
                    color: #495057;
                    margin-bottom: 10px;
                    display: flex;
                    align-items: center;
                    gap: 5px;
                }
                
                .parameters, .responses {
                    background: #f8f9fa;
                    border-radius: 5px;
                    padding: 15px;
                }
                
                .parameter {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                    padding: 8px 0;
                    border-bottom: 1px solid #e9ecef;
                }
                
                .parameter:last-child {
                    border-bottom: none;
                }
                
                .param-name {
                    font-weight: 600;
                    color: #495057;
                    min-width: 120px;
                }
                
                .param-type {
                    background: #e9ecef;
                    padding: 2px 6px;
                    border-radius: 3px;
                    font-size: 0.8em;
                    color: #495057;
                }
                
                .param-source {
                    background: #007bff;
                    color: white;
                    padding: 2px 6px;
                    border-radius: 3px;
                    font-size: 0.7em;
                }
                
                .param-required {
                    background: #dc3545;
                    color: white;
                    padding: 2px 6px;
                    border-radius: 3px;
                    font-size: 0.7em;
                }
                
                .param-optional {
                    background: #28a745;
                    color: white;
                    padding: 2px 6px;
                    border-radius: 3px;
                    font-size: 0.7em;
                }
                
                .response-section {
                    margin-bottom: 15px;
                }
                
                .response-section h4 {
                    margin-bottom: 10px;
                    color: #495057;
                }
                
                .success-response, .failure-response {
                    background: white;
                    border-radius: 5px;
                    padding: 12px;
                    margin-bottom: 10px;
                    border-left: 4px solid #28a745;
                }
                
                .failure-response {
                    border-left-color: #dc3545;
                }
                
                .response-status {
                    font-weight: 600;
                    color: #495057;
                    margin-bottom: 5px;
                }
                
                .response-type {
                    background: #e9ecef;
                    padding: 2px 6px;
                    border-radius: 3px;
                    font-size: 0.8em;
                    color: #495057;
                    display: inline-block;
                    margin-bottom: 5px;
                }
                
                .response-description {
                    color: #6c757d;
                    font-size: 0.9em;
                    margin-bottom: 10px;
                }
                
                .response-layer {
                    font-size: 0.8em;
                    color: #856404;
                    margin-bottom: 10px;
                }
                
                .json-example {
                    margin-top: 10px;
                    background: #f8f9fa;
                    border-radius: 5px;
                    padding: 10px;
                    border-left: 3px solid #007bff;
                }
                
                .json-title {
                    font-size: 0.85em;
                    font-weight: 600;
                    color: #495057;
                    margin-bottom: 5px;
                }
                
                .json-content {
                    background: #2d3748;
                    color: #e2e8f0;
                    padding: 12px;
                    border-radius: 4px;
                    font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
                    font-size: 0.85em;
                    line-height: 1.4;
                    overflow-x: auto;
                    margin: 0;
                    white-space: pre;
                }
                
                .no-data {
                    color: #6c757d;
                    font-style: italic;
                    text-align: center;
                    padding: 20px;
                }
                
                @media (max-width: 768px) {
                    .container {
                        padding: 10px;
                    }
                    
                    .header {
                        padding: 20px;
                    }
                    
                    .header h1 {
                        font-size: 2em;
                    }
                    
                    .stats {
                        flex-direction: column;
                        gap: 10px;
                    }
                    
                    .controller-header {
                        flex-direction: column;
                        align-items: flex-start;
                        gap: 10px;
                    }
                    
                    .controller-info {
                        width: 100%;
                        justify-content: space-between;
                    }
                    
                    .endpoint-header {
                        flex-direction: column;
                        align-items: flex-start;
                        gap: 8px;
                    }
                    
                    .parameter {
                        flex-direction: column;
                        align-items: flex-start;
                        gap: 5px;
                    }
                    
                    .param-name {
                        min-width: auto;
                    }
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>🚀 $projectName</h1>
                    <div class="subtitle">API Documentation</div>
                    <div class="stats">
                        <div class="stat">📁 Controllers: ${controllers.size}</div>
                        <div class="stat">🔗 Endpoints: $totalEndpoints</div>
                        <div class="stat">⚠️ Exceptions: $totalExceptions</div>
                        <div class="stat">📂 Path: $projectPath</div>
                    </div>
                </div>
                
                ${controllers.joinToString("\n") { controller ->
                    generateControllerHtml(controller)
                }}
            </div>
            
            <script>
                // Controller 접기/펼치기 기능
                document.addEventListener('DOMContentLoaded', function() {
                    const controllerHeaders = document.querySelectorAll('.controller-header');
                    
                    controllerHeaders.forEach(header => {
                        header.addEventListener('click', function() {
                            const controller = this.closest('.controller');
                            const content = controller.querySelector('.controller-content');
                            const icon = this.querySelector('.toggle-icon');
                            
                            // 다른 모든 컨트롤러 접기
                            document.querySelectorAll('.controller-content').forEach(otherContent => {
                                if (otherContent !== content) {
                                    otherContent.classList.remove('expanded');
                                    otherContent.closest('.controller').querySelector('.toggle-icon').textContent = '▶';
                                }
                            });
                            
                            // 현재 컨트롤러 토글
                            if (content.classList.contains('expanded')) {
                                content.classList.remove('expanded');
                                icon.textContent = '▶';
                            } else {
                                content.classList.add('expanded');
                                icon.textContent = '▼';
                            }
                        });
                    });
                    
                    // URL 해시로 특정 컨트롤러 자동 펼치기
                    if (window.location.hash) {
                        const targetController = document.querySelector(window.location.hash);
                        if (targetController) {
                            const content = targetController.querySelector('.controller-content');
                            const icon = targetController.querySelector('.toggle-icon');
                            content.classList.add('expanded');
                            icon.textContent = '▼';
                        }
                    }
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Controller HTML을 생성합니다.
     */
    private fun generateControllerHtml(controller: ControllerInfo): String {
        val controllerId = "controller-${controller.className.substringAfterLast('.').lowercase()}"
        return """
        <div class="controller" id="$controllerId">
            <div class="controller-header">
                <div class="controller-title">${controller.className.substringAfterLast('.')}</div>
                <div class="controller-info">
                    <span>Base: ${if (controller.baseMapping.isNotEmpty()) controller.baseMapping else "/"}</span>
                    <span class="endpoint-count">${controller.endpoints.size} endpoints</span>
                    <span class="toggle-icon">▶</span>
                </div>
            </div>
            <div class="controller-content">
                <div class="endpoints">
                    ${controller.endpoints.joinToString("\n") { generateEndpointHtml(it) }}
                </div>
            </div>
        </div>
        """.trimIndent()
    }

    /**
     * Endpoint HTML을 생성합니다.
     */
    private fun generateEndpointHtml(endpoint: ApiEndpoint): String {
        val methodClass = "method-${endpoint.method.lowercase()}"
        
        return """
        <div class="endpoint">
            <div class="endpoint-header">
                <span class="method $methodClass">${endpoint.method}</span>
                <span class="path">${endpoint.path}</span>
                <span class="method-name">${endpoint.methodName}()</span>
            </div>
            
            ${if (endpoint.parameters.isNotEmpty()) """
            <div class="section">
                <div class="section-title">
                    📝 Parameters
                </div>
                <div class="parameters">
                    ${endpoint.parameters.joinToString("\n") { generateParameterHtml(it) }}
                </div>
            </div>
            """ else ""}
            
            ${if (endpoint.responses != null) generateResponsesHtml(endpoint.responses) else ""}
        </div>
        """.trimIndent()
    }

    /**
     * Parameter HTML을 생성합니다.
     */
    private fun generateParameterHtml(parameter: ApiParameter): String {
        return """
        <div class="parameter">
            <span class="param-name">${parameter.name}</span>
            <div class="param-type">${parameter.type.substringAfterLast('.')}</div>
            <span class="param-source">${parameter.source.name}</span>
            <span class="${if (parameter.required) "param-required" else "param-optional"}">
                ${if (parameter.required) "Required" else "Optional"}
            </span>
        </div>
        """.trimIndent()
    }

    /**
     * Responses HTML을 생성합니다.
     */
    private fun generateResponsesHtml(responses: ApiResponses): String {
        return """
        <div class="section">
            <div class="section-title">
                📤 Responses
            </div>
            <div class="responses">
                ${if (responses.success != null) """
                <div class="response-section">
                    <h4>✅ Success Response</h4>
                    <div class="success-response">
                        <div class="response-status">HTTP ${responses.success.statusCode}</div>
                        <div class="response-type">${responses.success.type.substringAfterLast('.')}</div>
                        ${if (responses.success.description != null) """
                        <div class="response-description">${responses.success.description}</div>
                        """ else ""}
                        <div class="json-example">
                            <div class="json-title">Response Example:</div>
                            <pre class="json-content">${generateJsonSuccessExample(responses.success)}</pre>
                        </div>
                    </div>
                </div>
                """ else ""}
                
                ${if (responses.failures.isNotEmpty()) """
                <div class="response-section">
                    <h4>❌ Failure Responses</h4>
                    ${responses.failures.joinToString("\n") { failure ->
                        val jsonExample = generateJsonErrorExample(failure)
                        """
                        <div class="failure-response">
                            <div class="response-status">HTTP ${failure.statusCode}</div>
                            <div class="response-type">${failure.exceptionType}</div>
                            ${if (failure.description != null) """
                            <div class="response-description">${failure.description}</div>
                            """ else ""}
                            <div class="response-layer">📍 ${failure.detectedAt}</div>
                            <div class="json-example">
                                <div class="json-title">Response Example:</div>
                                <pre class="json-content">$jsonExample</pre>
                            </div>
                        </div>
                        """.trimIndent()
                    }}
                </div>
                """ else ""}
                
                ${if (responses.success == null && responses.failures.isEmpty()) """
                <div class="no-data">응답 정보가 없습니다.</div>
                """ else ""}
            </div>
        </div>
        """.trimIndent()
    }
    
    /**
     * 예외에 대한 JSON 응답 예시를 생성합니다.
     */
    private fun generateJsonErrorExample(failure: FailureResponse): String {
        val timestamp = "2023-12-07T10:15:30.123Z"
        val errorName = when (failure.statusCode) {
            400 -> "Bad Request"
            401 -> "Unauthorized" 
            403 -> "Forbidden"
            404 -> "Not Found"
            409 -> "Conflict"
            500 -> "Internal Server Error"
            else -> "Error"
        }
        
        return """
{
  "timestamp": "$timestamp",
  "status": ${failure.statusCode},
  "error": "$errorName",
  "message": "${failure.description}",
  "path": "/api/endpoint"
}""".trimIndent()
    }
    
    /**
     * 성공 응답에 대한 JSON 예시를 생성합니다.
     */
    private fun generateJsonSuccessExample(success: SuccessResponse): String {
        val responseType = success.type.substringAfterLast('.')
        
        return when (responseType) {
            "List" -> """
[
  {
    "id": 1,
    "name": "Sample Item 1",
    "description": "This is a sample item"
  },
  {
    "id": 2,
    "name": "Sample Item 2", 
    "description": "Another sample item"
  }
]""".trimIndent()
            
            "String" -> "\"Success\""
            
            "Long", "Integer" -> "123"
            
            "Boolean" -> "true"
            
            "Void" -> "null"
            
            else -> """
{
  "id": 1,
  "name": "Sample Response",
  "description": "This is a sample response object",
  "createdAt": "2023-12-07T10:15:30.123Z",
  "status": "ACTIVE"
}""".trimIndent()
        }
    }
} 

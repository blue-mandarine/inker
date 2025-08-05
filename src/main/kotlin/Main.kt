import java.io.File

/**
 * Spring API 문서 자동 생성기 메인 클래스
 * KSP와 ASM을 사용하여 Spring Controller를 분석하고 API 문서를 생성합니다.
 */
fun main(args: Array<String>) {
    println("========================================")
    println("🚀 Spring API 문서 자동 생성기")
    println("========================================")
    
    try {
        // 사용 가능한 프로젝트들
        val availableProjects = mapOf(
            "1" to ProjectInfo(
                name = "Spring Petclinic",
                path = "/Users/user/spring-petclinic",
                description = "공식 Spring Boot 샘플 프로젝트"
            ),
            "2" to ProjectInfo(
                name = "Hang-log",
                path = "/Users/user/2023-hang-log",
                description = "우아한테크코스 2023년 프로젝트"
            )
        )
        
        // 명령행 인수로 프로젝트 경로가 지정된 경우
        if (args.isNotEmpty()) {
            val customPath = args[0]
            val customName = if (args.size > 1) args[1] else "Custom Project"
            
            println("📂 사용자 지정 경로: $customPath")
            analyzeProject(customPath, customName)
            return
        }
        
        // 프로젝트 선택 메뉴 출력
        println("📋 분석할 프로젝트를 선택하세요:")
        availableProjects.forEach { (key, project) ->
            val exists = File(project.path).exists()
            val status = if (exists) "✅" else "❌"
            println("   $key. $status ${project.name}")
            println("      경로: ${project.path}")
            println("      설명: ${project.description}")
            println()
        }
        
        println("   3. 🔧 사용자 지정 경로")
        println("   0. ❌ 종료")
        println()
        print("선택 (1-3): ")
        
        val choice = readlnOrNull()
        
        when (choice) {
            "1", "2" -> {
                val selectedProject = availableProjects[choice]!!
                if (!File(selectedProject.path).exists()) {
                    println("❌ 프로젝트가 존재하지 않습니다: ${selectedProject.path}")
                    return
                }
                analyzeProject(selectedProject.path, selectedProject.name)
            }
            
            "3" -> {
                print("프로젝트 경로를 입력하세요: ")
                val customPath = readlnOrNull()
                if (customPath.isNullOrBlank()) {
                    println("❌ 올바른 경로를 입력해주세요.")
                    return
                }
                
                print("프로젝트 이름을 입력하세요 (선택): ")
                val customName = readlnOrNull()?.takeIf { it.isNotBlank() } ?: "Custom Project"
                
                analyzeProject(customPath, customName)
            }
            
            "0" -> {
                println("👋 종료합니다.")
                return
            }
            
            else -> {
                println("❌ 올바른 선택이 아닙니다.")
                return
            }
        }
        
    } catch (e: Exception) {
        println("❌ 오류가 발생했습니다: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * 프로젝트 정보를 담는 데이터 클래스
 */
data class ProjectInfo(
    val name: String,
    val path: String,
    val description: String
)

/**
 * 프로젝트를 분석하고 API 문서를 생성합니다.
 */
private fun analyzeProject(projectPath: String, projectName: String) {
    println("\n========================================")
    println("🔍 $projectName 분석 시작")
    println("========================================")
    
    // 프로젝트 존재 여부 확인
    if (!File(projectPath).exists()) {
        println("❌ 프로젝트가 존재하지 않습니다: $projectPath")
        return
    }
    
    // API 문서 생성기 초기화
    val generator = ApiDocumentationGenerator(projectPath, projectName)
    
    // 프로젝트 분석 및 문서 생성
    val apiDocumentation = generator.generateDocumentation()
    
    // 결과가 비어있는 경우
    if (apiDocumentation.controllers.isEmpty()) {
        println("\n⚠️  Controller를 찾을 수 없거나 컴파일된 클래스가 없습니다.")
        println("빌드를 완료한 후 다시 시도해주세요.")
        return
    }
    
    // 결과를 JSON과 HTML 파일로 저장
    val projectFileName = projectName.lowercase()
        .replace(" ", "-")
        .replace("[^a-z0-9-]".toRegex(), "")
    
    generator.saveToJson(apiDocumentation, "$projectFileName-api-documentation.json")
    generator.saveToHtml(apiDocumentation, "$projectFileName-api-documentation.html")
    
    println("\n========================================")
    println("✅ API 문서 생성이 완료되었습니다!")
    println("📄 JSON 파일: $projectFileName-api-documentation.json")
    println("🌐 HTML 파일: $projectFileName-api-documentation.html")
    println("========================================")
    
    // 브라우저에서 HTML 파일 열기 제안
    print("\n🌐 HTML 파일을 브라우저에서 열까요? (y/N): ")
    val openBrowser = readlnOrNull()
    if (openBrowser?.lowercase() == "y" || openBrowser?.lowercase() == "yes") {
        try {
            val osName = System.getProperty("os.name").lowercase()
            val command = when {
                osName.contains("mac") -> "open"
                osName.contains("win") -> "start"
                else -> "xdg-open"
            }
            ProcessBuilder(command, "$projectFileName-api-documentation.html").start()
            println("🚀 브라우저에서 HTML 파일을 열었습니다!")
        } catch (e: Exception) {
            println("❌ 브라우저 열기에 실패했습니다: ${e.message}")
        }
    }
}
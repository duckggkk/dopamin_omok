plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.dopamin"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}


configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val jjwtVersion = "0.12.6"

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // QueryDSL — 타입 안전 동적 쿼리(방 검색 필터). jakarta 분류자(Boot3/Hibernate6) 필수.
    implementation("com.querydsl:querydsl-jpa::jakarta")
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    // Redis — 이메일 인증코드·Refresh 토큰처럼 "TTL로 자동 만료되는 임시 데이터" 저장소
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // Actuator — /actuator/health 헬스체크(무중단 판단·배포 검증·컨테이너 healthcheck)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Micrometer → Prometheus: /actuator/prometheus 로 메트릭 노출(Grafana 대시보드용, 내부망 전용)
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // MySQL
    runtimeOnly("com.mysql:mysql-connector-j")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // OpenAPI / Swagger UI (Flutter 등 클라이언트 코드 생성 기반)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // DevTools (개발 시 자동 재시작)
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = 1
    jvmArgs("-Xmx512m")
    listOf(
        "omok.bench.mysql",
        "omok.bench.mysql.url",
        "omok.bench.mysql.port",
        "omok.bench.mysql.username",
        "omok.bench.mysql.password",
    ).forEach { name ->
        System.getProperty(name)?.let { systemProperty(name, it) }
    }
}

// test 소스셋 참조 — 아래 벤치마크 task 가 일반 test 와 같은 테스트 클래스를 재사용한다
val testSourceSet = sourceSets.named("test")

// 벤치마크 요약 파일 — 테스트가 측정치를 여기에 쓰고, task 가 실행 마지막에 콘솔로 다시 출력한다
val benchSummaryFile = layout.buildDirectory.file("reports/benchmark/head-to-head-summary.txt")

// 로컬 MySQL 벤치마크 전용 task — 평소 `gradlew test` 와 분리된 opt-in 실행 버튼.
// 실행: .\gradlew.bat mysqlBenchmarkTest --rerun-tasks --console=plain
//   (--rerun-tasks 가 없으면 두 번째 실행부터 UP-TO-DATE 로 건너뜀)
// 사전 준비: docker compose -f docker-compose.dev.yml up -d db  (localhost:3307)
tasks.register<Test>("mysqlBenchmarkTest") {
    group = "verification"
    description = "Runs the opt-in local MySQL benchmark tests."
    // 커스텀 Test task 는 어떤 클래스를 어디서 찾을지 직접 연결해야 한다(내장 test 와 달리 자동 설정 없음)
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    filter {
        // 벤치마크 클래스 하나만 실행 — 나머지 테스트는 이 task 에서 제외
        includeTestsMatching("*HeadToHeadMySqlBenchmarkTest")
    }
    // @EnabledIfSystemProperty 잠금을 여는 열쇠.
    // 일반 `gradlew test` 에는 이 값이 없어서 벤치마크가 자동 skip 된다.
    systemProperty("omok.bench.mysql", "true")
    // 테스트가 측정 요약을 남길 파일 경로를 전달
    systemProperty("omok.bench.summary.file", benchSummaryFile.get().asFile.absolutePath)
    testLogging {
        // 테스트 안의 System.out([HeadToHeadBenchmark] 측정치)을 콘솔에 그대로 표시
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
    // 이전 실행의 요약이 남아 새 결과처럼 보이는 것 방지
    doFirst {
        benchSummaryFile.get().asFile.delete()
    }
    // 로그가 아무리 길어도 요약이 항상 맨 끝(BUILD SUCCESSFUL 직전)에 보이도록 재출력.
    // 콘솔 인코딩이 환경마다 달라(한글 깨짐) 출력 문구는 ASCII 로 유지한다.
    doLast {
        val file = benchSummaryFile.get().asFile
        if (file.exists()) {
            println()
            println("========== [HeadToHeadBenchmark] Summary ==========")
            file.readLines().forEach { println("  $it") }
            println("===================================================")
            println("  summary file: ${file.absolutePath}")
            println("  html report:  ${file.absolutePath.removeSuffix(".txt")}.html")
        }
    }
}

// 로컬 기본 프로파일
tasks.bootRun {
    args("--spring.profiles.active=${project.findProperty("profile") ?: "local"}")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

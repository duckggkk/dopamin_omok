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
}

// 로컬 기본 프로파일
tasks.bootRun {
    args("--spring.profiles.active=${project.findProperty("profile") ?: "local"}")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

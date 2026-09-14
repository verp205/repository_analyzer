package com.ivan_naumenko.repository_analyzer.report.snapshot;

import com.ivan_naumenko.repository_analyzer.git.exception.RepositoryNotFoundException;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositorySnapshotServiceTest {

    private final GitService gitService = mock(GitService.class);
    private final RepositorySnapshotService service = new RepositorySnapshotService(gitService);

    @TempDir
    Path root;

    @Test
    void collectsStackModulesAndSecuritySignalsWithoutSecretValues() throws IOException {
        write("README.md", """
                # SmartStock

                ![build](https://example.com/badge.svg)

                Система учёта товаров на складах и перемещений между ними.
                """);
        write("pom.xml", """
                <parent>
                  <artifactId>spring-boot-starter-parent</artifactId>
                  <version>3.3.1</version>
                </parent>
                <properties><java.version>21</java.version></properties>
                <dependency><artifactId>spring-boot-starter-web</artifactId></dependency>
                <dependency><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
                <dependency><artifactId>postgresql</artifactId></dependency>
                """);
        write("docker-compose.yml", """
                services:
                  db:
                    environment:
                      - POSTGRES_PASSWORD=postgres
                """);
        write("src/main/resources/application.yml", """
                spring:
                  datasource:
                    url: jdbc:postgresql://localhost/stock
                    password: s3cr3t-value
                    username: ${DB_USER}
                jwt:
                  secret: ${JWT_SECRET}
                  token-expiration: 3600
                management:
                  endpoints:
                    web:
                      exposure:
                        include: "*"
                """);
        write("src/main/java/com/acme/stock/controller/InventoryController.java", """
                @RestController
                @CrossOrigin
                class InventoryController {}
                """);
        write("src/main/java/com/acme/stock/controller/TransferController.java", "@RestController class TransferController {}");
        write("src/main/java/com/acme/stock/service/TransferService.java", "class TransferService {}");
        write("src/test/java/com/acme/stock/service/TransferServiceTest.java", "class TransferServiceTest {}");
        write("src/test/resources/application.yml", "spring.datasource.password: test");
        write(".env", "DB_PASSWORD=1");
        write(".env.example", "DB_PASSWORD=");
        write("node_modules/lib/index.js", "ignored");

        RepositorySnapshot snapshot = service.scan(root);

        assertTrue(snapshot.available());
        assertEquals("Система учёта товаров на складах и перемещений между ними.", snapshot.description());
        assertTrue(snapshot.stack().containsAll(List.of(
                "Maven", "Java 21", "Spring Boot 3.3.1", "Spring Web", "Spring Data JPA", "PostgreSQL", "Docker"
        )));
        assertEquals(List.of(
                new RepositorySnapshot.ModuleFact("controller", 2),
                new RepositorySnapshot.ModuleFact("service", 1)
        ), snapshot.modules());
        assertEquals(3, snapshot.mainSourceFiles());
        assertEquals(1, snapshot.testSourceFiles());
        assertEquals(List.of("InventoryController", "TransferController"), snapshot.controllers());

        RepositorySnapshot.SecurityFacts security = snapshot.security();
        assertFalse(security.securityDependency());
        assertFalse(security.securityConfiguration());
        assertTrue(security.corsWildcard());
        assertTrue(security.actuatorExposesAll());
        assertEquals(List.of(
                "docker-compose.yml: services.db.environment.POSTGRES_PASSWORD",
                "src/main/resources/application.yml: spring.datasource.password"
        ), security.secretsInConfig());
        assertEquals(List.of(".env"), security.committedEnvFiles());
        assertFalse(snapshot.toString().contains("s3cr3t-value"));
    }

    @Test
    void detectsConfiguredSpringSecurity() throws IOException {
        write("build.gradle.kts", """
                plugins { id("org.springframework.boot") version "3.2.0" }
                dependencies { implementation("org.springframework.boot:spring-boot-starter-security") }
                """);
        write("src/main/java/app/security/SecurityConfig.java", """
                @EnableWebSecurity
                class SecurityConfig {
                  SecurityFilterChain chain(HttpSecurity http) {
                    return http.csrf(AbstractHttpConfigurer::disable).build();
                  }
                  @PreAuthorize("hasRole('ADMIN')") void admin() {}
                }
                """);

        RepositorySnapshot.SecurityFacts security = service.scan(root).security();

        assertTrue(security.securityDependency());
        assertTrue(security.securityConfiguration());
        assertTrue(security.csrfDisabled());
        assertEquals(1, security.methodSecurityAnnotations());
        assertFalse(security.corsWildcard());
    }

    @Test
    void returnsUnavailableSnapshotWhenRepositoryIsMissing() {
        when(gitService.getRepositoryPath("https://example.com/a.git", "main"))
                .thenThrow(new RepositoryNotFoundException("нет"));

        assertFalse(service.capture("https://example.com/a.git", "main").available());
    }

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

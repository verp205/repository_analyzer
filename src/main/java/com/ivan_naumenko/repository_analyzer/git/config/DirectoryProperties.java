package com.ivan_naumenko.repository_analyzer.git.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "repository-analyzer.directory")
public record DirectoryProperties(
        @NotNull
        Path path
) {
}

package com.ivan_naumenko.repository_analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RepositoryAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepositoryAnalyzerApplication.class, args);
    }

}

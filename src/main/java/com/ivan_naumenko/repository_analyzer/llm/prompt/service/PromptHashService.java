package com.ivan_naumenko.repository_analyzer.llm.prompt.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class PromptHashService {

    public String calculate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "prompt не должен быть пустым"
            );
        }

        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] promptBytes = prompt.getBytes(
                    StandardCharsets.UTF_8
            );

            byte[] hashBytes = messageDigest.digest(promptBytes);

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Алгоритм SHA-256 недоступен",
                    exception
            );
        }
    }
}
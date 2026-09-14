package com.ivan_naumenko.repository_analyzer.evaluation;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

public final class GoldenDataset {

    public static final String RESOURCE = "/evaluation/commit-insight-golden.json";

    private GoldenDataset() {
    }

    public static List<GoldenCommitCase> load() {
        try (InputStream input = GoldenDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Не найден эталонный набор " + RESOURCE);
            }
            return List.of(JsonMapper.builder().build().readValue(input, GoldenCommitCase[].class));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}

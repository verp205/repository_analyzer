package com.ivan_naumenko.repository_analyzer.llm.context.service;

import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@RequiredArgsConstructor
@Service
public class CompactContextSerializer {

    private final ObjectMapper mapper;

    public String serialize(CompactCommitContext context) {
        Objects.requireNonNull(
                context,
                "context не должен быть null"
        );

        return mapper.writeValueAsString(context);
    }
}

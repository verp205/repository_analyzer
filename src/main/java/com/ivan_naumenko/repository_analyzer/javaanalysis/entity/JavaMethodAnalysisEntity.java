package com.ivan_naumenko.repository_analyzer.javaanalysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "java_method_analysis",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_java_method_type_position",
                columnNames = {"java_type_analysis_id", "method_position"}
        )
)
public class JavaMethodAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "java_type_analysis_id", nullable = false)
    private JavaTypeAnalysisEntity javaTypeAnalysis;

    @Column(name = "method_position", nullable = false)
    private int methodPosition;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "return_type", nullable = false)
    private String returnType;

    @Column(name = "parameter_types_text", nullable = false)
    private String parameterTypesText;

    @Column(name = "modifiers_text", nullable = false)
    private String modifiersText;

    @Column(name = "annotations_text", nullable = false)
    private String annotationsText;
}

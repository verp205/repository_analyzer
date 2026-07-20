package com.ivan_naumenko.repository_analyzer.javaanalysis.entity;

import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeKind;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "java_type_analysis",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_java_type_file_position",
                columnNames = {"java_file_analysis_id", "type_position"}
        )
)
public class JavaTypeAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "java_file_analysis_id", nullable = false)
    private JavaFileAnalysisEntity javaFileAnalysis;

    @Column(name = "type_position", nullable = false)
    private int typePosition;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private JavaTypeKind kind;

    @Column(name = "modifiers_text", nullable = false)
    private String modifiersText;

    @Column(name = "annotations_text", nullable = false)
    private String annotationsText;

    @Column(name = "constructor_count", nullable = false)
    private int constructorCount;

    @OrderBy("methodPosition ASC")
    @OneToMany(
            mappedBy = "javaTypeAnalysis",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<JavaMethodAnalysisEntity> methods = new ArrayList<>();

    public void addMethod(JavaMethodAnalysisEntity method) {
        methods.add(method);
        method.setJavaTypeAnalysis(this);
    }
}

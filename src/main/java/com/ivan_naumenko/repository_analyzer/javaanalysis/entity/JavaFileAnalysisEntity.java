package com.ivan_naumenko.repository_analyzer.javaanalysis.entity;

import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "java_file_analysis")
public class JavaFileAnalysisEntity {

    @Id
    @Column(name = "changed_file_id")
    private Integer changedFileId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_file_id")
    private ChangedFileEntity changedFile;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "imports_text", nullable = false)
    private String importsText;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "parsing_successful", nullable = false)
    private boolean parsingSuccessful;

    @Column(name = "problems_text", nullable = false)
    private String problemsText;

    @OrderBy("typePosition ASC")
    @OneToMany(
            mappedBy = "javaFileAnalysis",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<JavaTypeAnalysisEntity> types = new ArrayList<>();

    public void addType(JavaTypeAnalysisEntity type) {
        types.add(type);
        type.setJavaFileAnalysis(this);
    }
}

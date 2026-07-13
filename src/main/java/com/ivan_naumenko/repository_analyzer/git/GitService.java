package com.ivan_naumenko.repository_analyzer.git;

import com.ivan_naumenko.repository_analyzer.config.directory.DirectoryProperties;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RequiredArgsConstructor
@Service
public class GitService {

    private final DirectoryProperties directoryProperties;

    public Path cloneRepository(String repositoryUrl, String branch) throws IOException, GitAPIException {

        Path repositoriesRoot = directoryProperties.path()
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(repositoriesRoot);

        Path repositoryDirectory = repositoriesRoot.resolve(repositoryKey(repositoryUrl, branch));

        if (Files.isDirectory(repositoryDirectory.resolve(".git"))) {
            return repositoryDirectory;
        }

        try (Git git = Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(repositoryDirectory.toFile())
                .setBranch(branch)
                .call()) {
            return repositoryDirectory;
        }
    }

    private String repositoryKey(String repositoryUrl, String branch) {
        String source = repositoryUrl.trim() + "\n" + branch.trim();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}

package com.ivan_naumenko.repository_analyzer.git.model;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.util.Arrays;

/**
 * Временные учётные данные для HTTPS-доступа к Git.
 *
 * <p>Объект не является JPA-сущностью и не должен сохраняться в базе данных.
 * После передачи команды JGit владелец обязан вызвать {@link #close()}.</p>
 */
public final class GitCredentials implements AutoCloseable {

    private static final String DEFAULT_TOKEN_USERNAME = "oauth2";

    private final String username;
    private final char[] accessToken;
    private boolean closed;

    private GitCredentials(String username, String accessToken) {
        this.username = username;
        this.accessToken = accessToken.toCharArray();
    }

    public static GitCredentials from(
            String username,
            String accessToken
    ) {
        boolean usernamePresent = username != null && !username.isBlank();
        boolean tokenPresent = accessToken != null && !accessToken.isBlank();

        if (!tokenPresent) {
            if (usernamePresent) {
                throw new IllegalArgumentException(
                        "Для Git-имени пользователя необходимо указать токен"
                );
            }
            return null;
        }

        String normalizedUsername = usernamePresent
                ? username.trim()
                : DEFAULT_TOKEN_USERNAME;

        return new GitCredentials(normalizedUsername, accessToken);
    }

    public synchronized CredentialsProvider createProvider() {
        if (closed) {
            throw new IllegalStateException(
                    "Учётные данные Git уже были очищены"
            );
        }

        return new UsernamePasswordCredentialsProvider(
                username,
                accessToken
        );
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        Arrays.fill(accessToken, '\0');
        closed = true;
    }

    @Override
    public String toString() {
        return "GitCredentials[username="
                + username
                + ", accessToken=***]";
    }
}

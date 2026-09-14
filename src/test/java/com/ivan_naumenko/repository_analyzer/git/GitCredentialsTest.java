package com.ivan_naumenko.repository_analyzer.git;

import com.ivan_naumenko.repository_analyzer.git.model.GitCredentials;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitCredentialsTest {

    @Test
    void createsJgitProviderWithExplicitUsernameAndToken() throws Exception {
        GitCredentials credentials = GitCredentials.from(
                "ivan",
                "secret-token"
        );
        CredentialsProvider provider = credentials.createProvider();
        CredentialItem.Username username = new CredentialItem.Username();
        CredentialItem.Password password = new CredentialItem.Password();

        assertTrue(provider.get(
                new URIish("https://example.com/private.git"),
                username,
                password
        ));
        assertEquals("ivan", username.getValue());
        assertArrayEquals(
                "secret-token".toCharArray(),
                password.getValue()
        );
        assertTrue(credentials.toString().contains("accessToken=***"));
        assertFalse(credentials.toString().contains("secret-token"));

        credentials.close();
        assertThrows(
                IllegalStateException.class,
                credentials::createProvider
        );
    }

    @Test
    void usesTokenUsernameWhenUsernameIsNotSpecified() throws Exception {
        GitCredentials credentials = GitCredentials.from(
                null,
                "secret-token"
        );
        CredentialItem.Username username = new CredentialItem.Username();

        credentials.createProvider().get(
                new URIish("https://example.com/private.git"),
                username
        );

        assertEquals("oauth2", username.getValue());
        credentials.close();
    }

    @Test
    void returnsNoCredentialsWhenBothFieldsAreEmpty() {
        assertNull(GitCredentials.from(null, null));
        assertNull(GitCredentials.from(" ", " "));
    }

    @Test
    void rejectsUsernameWithoutToken() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GitCredentials.from("ivan", null)
        );
    }
}

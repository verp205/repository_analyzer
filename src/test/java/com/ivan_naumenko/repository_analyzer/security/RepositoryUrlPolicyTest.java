package com.ivan_naumenko.repository_analyzer.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RepositoryUrlPolicyTest {

    @Test
    void acceptsHttpsLinkToPublicHost() {
        RepositoryUrlPolicy policy = policy(false, List.of(), "140.82.121.3");

        assertEquals(
                "https://github.com/owner/repo.git",
                policy.validate("  https://github.com/owner/repo.git ")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://github.com/owner/repo",
            "file:///tmp/repository",
            "ssh://git@github.com/owner/repo.git",
            "git@github.com:owner/repo.git"
    })
    void rejectsNonHttpsLinks(String url) {
        RepositoryUrlPolicy policy = policy(false, List.of(), "140.82.121.3");

        assertThrows(IllegalArgumentException.class, () -> policy.validate(url));
    }

    @Test
    void rejectsCredentialsInsideUrl() {
        RepositoryUrlPolicy policy = policy(false, List.of(), "140.82.121.3");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate("https://user:token@github.com/owner/repo.git")
        );
        assertTrue(exception.getMessage().contains("логин"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",
            "10.1.2.3",
            "172.16.0.10",
            "192.168.1.10",
            "169.254.169.254",
            "100.64.0.1",
            "0.0.0.0",
            "::1",
            "fd00::1",
            "fe80::1"
    })
    void rejectsHostsResolvingToInternalAddresses(String address) {
        RepositoryUrlPolicy policy = policy(false, List.of(), address);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate("https://git.example.com/team/app.git")
        );
        assertTrue(exception.getMessage().contains("внутренний"), exception.getMessage());
    }

    @Test
    void allowsInternalHostsWhenExplicitlyEnabled() {
        RepositoryUrlPolicy policy = new RepositoryUrlPolicy(
                new SecurityProperties(true, List.of(), ""),
                host -> fail("DNS не должен проверяться")
        );

        assertEquals(
                "https://gitlab.corp.local/team/app.git",
                policy.validate("https://gitlab.corp.local/team/app.git")
        );
    }

    @Test
    void enforcesAllowedHostsIncludingSubdomains() {
        RepositoryUrlPolicy policy = policy(false, List.of("GitHub.com"), "140.82.121.3");

        policy.validate("https://github.com/owner/repo");
        policy.validate("https://enterprise.github.com/owner/repo");
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate("https://gitlab.com/owner/repo")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate("https://github.com.evil.io/owner/repo")
        );
    }

    @Test
    void rejectsUnknownHosts() {
        RepositoryUrlPolicy policy = new RepositoryUrlPolicy(
                new SecurityProperties(false, List.of(), ""),
                host -> {
                    throw new UnknownHostException(host);
                }
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate("https://no-such-host.example/repo")
        );
        assertTrue(exception.getMessage().contains("Не удалось определить"));
    }

    private RepositoryUrlPolicy policy(boolean allowPrivate, List<String> allowedHosts, String address) {
        return new RepositoryUrlPolicy(
                new SecurityProperties(allowPrivate, allowedHosts, ""),
                host -> new InetAddress[]{InetAddress.getByName(address)}
        );
    }
}

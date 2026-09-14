package com.ivan_naumenko.repository_analyzer.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Защищает сервер от клонирования произвольных адресов: разрешает только
 * HTTPS-ссылки на публичные хосты (или хосты из белого списка).
 */
@Component
public class RepositoryUrlPolicy {

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final SecurityProperties properties;
    private final HostResolver resolver;

    @Autowired
    public RepositoryUrlPolicy(SecurityProperties properties) {
        this(properties, InetAddress::getAllByName);
    }

    public RepositoryUrlPolicy(SecurityProperties properties, HostResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    /** Проверяет ссылку и возвращает её без лишних пробелов. */
    public String validate(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException("URL репозитория не должен быть пустым");
        }
        String normalized = repositoryUrl.strip();

        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Некорректный URL репозитория");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "Поддерживаются только HTTPS-ссылки на репозиторий"
            );
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Не указывайте логин или токен в URL: для доступа используйте отдельные поля"
            );
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("В URL репозитория не указан хост");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT)
                .replaceAll("^\\[|]$", "");

        if (!properties.allowedHosts().isEmpty()
                && properties.allowedHosts().stream().noneMatch(allowed ->
                normalizedHost.equals(allowed) || normalizedHost.endsWith("." + allowed))) {
            throw new IllegalArgumentException(
                    "Хост " + normalizedHost + " не входит в список разрешённых"
            );
        }
        if (!properties.allowPrivateHosts()) {
            ensurePublicHost(normalizedHost);
        }
        return normalized;
    }

    private void ensurePublicHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Не удалось определить адрес хоста " + host);
        }
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("Не удалось определить адрес хоста " + host);
        }
        if (Arrays.stream(addresses).anyMatch(RepositoryUrlPolicy::isNonPublic)) {
            throw new IllegalArgumentException(
                    "Хост " + host + " указывает на внутренний адрес. Если это корпоративный "
                            + "Git-сервер, включите repository-analyzer.security.allow-private-hosts"
            );
        }
    }

    static boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 198 && (second == 18 || second == 19));
        }
        if (bytes.length == 16) {
            // fc00::/7 — уникальные локальные IPv6-адреса.
            if ((bytes[0] & 0xfe) == 0xfc) {
                return true;
            }
            boolean ipv4Mapped = Arrays.equals(Arrays.copyOfRange(bytes, 0, 10), new byte[10])
                    && (bytes[10] & 0xff) == 0xff
                    && (bytes[11] & 0xff) == 0xff;
            if (ipv4Mapped) {
                try {
                    return isNonPublic(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
                } catch (UnknownHostException exception) {
                    return true;
                }
            }
        }
        return false;
    }
}

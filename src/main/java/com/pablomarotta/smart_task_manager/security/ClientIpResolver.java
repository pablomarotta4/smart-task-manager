package com.pablomarotta.smart_task_manager.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class ClientIpResolver {

    private static final int MAXIMUM_FORWARDED_HEADER_LENGTH = 1_024;
    private static final int MAXIMUM_FORWARDED_HOPS = 20;
    private static final Pattern IPV4_CANDIDATE = Pattern.compile("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}");
    private static final Pattern IPV6_CANDIDATE = Pattern.compile("[0-9A-Fa-f:]+(?:%[0-9A-Za-z_.-]+)?");

    private final List<Cidr> trustedProxies;

    @Autowired
    public ClientIpResolver(@Value("${auth-rate-limit.trusted-proxies:}") String configuredTrustedProxies) {
        this(parseConfiguredTrustedProxies(configuredTrustedProxies));
    }

    ClientIpResolver(List<String> configuredTrustedProxies) {
        this.trustedProxies = configuredTrustedProxies.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .map(Cidr::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        Optional<InetAddress> remote = parseIpLiteral(remoteAddress);
        if (remote.isEmpty() || !isTrusted(remote.get())) {
            return remoteAddress;
        }

        List<InetAddress> forwarded = forwardedAddresses(request);
        for (int index = forwarded.size() - 1; index >= 0; index--) {
            InetAddress candidate = forwarded.get(index);
            if (!isTrusted(candidate)) {
                return candidate.getHostAddress();
            }
        }
        return remoteAddress;
    }

    private List<InetAddress> forwardedAddresses(HttpServletRequest request) {
        String forwarded = request.getHeader("Forwarded");
        if (forwarded != null && !forwarded.isBlank()) {
            return parseForwarded(forwarded);
        }
        return parseXForwardedFor(request.getHeader("X-Forwarded-For"));
    }

    private List<InetAddress> parseForwarded(String header) {
        if (!isBounded(header)) {
            return List.of();
        }
        String[] entries = header.split(",", -1);
        if (entries.length == 0 || entries.length > MAXIMUM_FORWARDED_HOPS) {
            return List.of();
        }
        List<InetAddress> addresses = new ArrayList<>(entries.length);
        for (String entry : entries) {
            String candidate = null;
            for (String parameter : entry.split(";", -1)) {
                int separator = parameter.indexOf('=');
                if (separator <= 0 || parameter.indexOf('=', separator + 1) >= 0) {
                    continue;
                }
                if ("for".equalsIgnoreCase(parameter.substring(0, separator).strip())) {
                    if (candidate != null) {
                        return List.of();
                    }
                    candidate = parameter.substring(separator + 1).strip();
                }
            }
            Optional<InetAddress> parsed = parseForwardedValue(candidate);
            if (parsed.isEmpty()) {
                return List.of();
            }
            addresses.add(parsed.get());
        }
        return addresses;
    }

    private List<InetAddress> parseXForwardedFor(String header) {
        if (!isBounded(header)) {
            return List.of();
        }
        String[] entries = header.split(",", -1);
        if (entries.length == 0 || entries.length > MAXIMUM_FORWARDED_HOPS) {
            return List.of();
        }
        List<InetAddress> addresses = new ArrayList<>(entries.length);
        for (String entry : entries) {
            Optional<InetAddress> parsed = parseIpLiteral(entry.strip());
            if (parsed.isEmpty()) {
                return List.of();
            }
            addresses.add(parsed.get());
        }
        return addresses;
    }

    private Optional<InetAddress> parseForwardedValue(String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        String unquoted = value;
        if (unquoted.startsWith("\"") && unquoted.endsWith("\"") && unquoted.length() > 1) {
            unquoted = unquoted.substring(1, unquoted.length() - 1);
        } else if (unquoted.startsWith("\"") || unquoted.endsWith("\"")) {
            return Optional.empty();
        }
        if (unquoted.startsWith("[") && unquoted.endsWith("]") && unquoted.length() > 2) {
            unquoted = unquoted.substring(1, unquoted.length() - 1);
        }
        return parseIpLiteral(unquoted);
    }

    private boolean isBounded(String value) {
        return value != null && !value.isBlank() && value.length() <= MAXIMUM_FORWARDED_HEADER_LENGTH;
    }

    private boolean isTrusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(cidr -> cidr.matches(address));
    }

    private static List<String> parseConfiguredTrustedProxies(String configuredTrustedProxies) {
        if (configuredTrustedProxies == null || configuredTrustedProxies.isBlank()) {
            return List.of();
        }
        return List.of(configuredTrustedProxies.split(",", -1));
    }

    private static Optional<InetAddress> parseIpLiteral(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String candidate = value.strip();
        if (IPV4_CANDIDATE.matcher(candidate).matches()) {
            String[] octets = candidate.split("\\.", -1);
            for (String octet : octets) {
                if (Integer.parseInt(octet) > 255) {
                    return Optional.empty();
                }
            }
        } else if (!IPV6_CANDIDATE.matcher(candidate).matches() || !candidate.contains(":")) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(candidate));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    private record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(String configuredValue) {
            String[] parts = configuredValue.split("/", -1);
            if (parts.length > 2) {
                throw new IllegalArgumentException("Trusted proxy must be an IP address or CIDR");
            }
            InetAddress address = parseIpLiteral(parts[0])
                    .orElseThrow(() -> new IllegalArgumentException("Trusted proxy must be an IP address or CIDR"));
            int maximumPrefixLength = address.getAddress().length * Byte.SIZE;
            int prefixLength = parts.length == 1 ? maximumPrefixLength : parsePrefixLength(parts[1], maximumPrefixLength);
            return new Cidr(address.getAddress(), prefixLength);
        }

        private static int parsePrefixLength(String value, int maximumPrefixLength) {
            try {
                int prefixLength = Integer.parseInt(value);
                if (prefixLength < 0 || prefixLength > maximumPrefixLength) {
                    throw new IllegalArgumentException("Trusted proxy CIDR prefix is invalid");
                }
                return prefixLength;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Trusted proxy CIDR prefix is invalid", exception);
            }
        }

        boolean matches(InetAddress candidate) {
            byte[] candidateBytes = candidate.getAddress();
            if (candidateBytes.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / Byte.SIZE;
            int remainderBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < fullBytes; index++) {
                if (network[index] != candidateBytes[index]) {
                    return false;
                }
            }
            if (remainderBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainderBits);
            return (network[fullBytes] & mask) == (candidateBytes[fullBytes] & mask);
        }
    }
}

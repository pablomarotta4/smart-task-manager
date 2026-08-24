package com.pablomarotta.smart_task_manager.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersWhenTheDirectPeerIsNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        HttpServletRequest request = request("203.0.113.50", "198.51.100.10", null);

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
    }

    @Test
    void trustedProxyResolvesTheRightmostUntrustedXForwardedForAddress() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        HttpServletRequest request = request("10.0.0.4", "198.51.100.10, 10.0.0.8", null);

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void trustedProxyPrefersAValidForwardedChain() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        HttpServletRequest request = request(
                "10.0.0.4",
                "198.51.100.10",
                "for=198.51.100.11;proto=https, for=10.0.0.8"
        );

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.11");
    }

    @Test
    void rejectsMalformedOrOversizedForwardedChains() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        HttpServletRequest malformed = request("10.0.0.4", "198.51.100.10, spoof.invalid", null);
        HttpServletRequest oversized = request("10.0.0.4", "198.51.100.10".repeat(100), null);

        assertThat(resolver.resolve(malformed)).isEqualTo("10.0.0.4");
        assertThat(resolver.resolve(oversized)).isEqualTo("10.0.0.4");
    }

    private HttpServletRequest request(String remoteAddress, String xForwardedFor, String forwarded) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
        when(request.getHeader("Forwarded")).thenReturn(forwarded);
        return request;
    }
}

package com.pablomarotta.smart_task_manager.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCodecTest {

    private final RefreshTokenCodec codec = new RefreshTokenCodec();

    @Test
    void generatesUrlSafeHighEntropyTokens() {
        String first = codec.generate();
        String second = codec.generate();

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).hasSize(43).isNotEqualTo(first);
    }

    @Test
    void hashesTokensWithADeterministicSha256Digest() {
        assertThat(codec.hash("refresh-token"))
                .isEqualTo("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120")
                .hasSize(64)
                .doesNotContain("refresh-token");
    }
}

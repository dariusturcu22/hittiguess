package org.dariusturcu.backend.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSongCatalogRepositoryTest {

    @Test
    void plainTextIsWrappedToMatchAnywhere() {
        assertThat(AdminSongCatalogRepository.substringPattern("zimi")).isEqualTo("%zimi%");
    }

    @Test
    void likeWildcardsAndTheEscapeCharacterAreEscaped() {
        assertThat(AdminSongCatalogRepository.substringPattern("100%")).isEqualTo("%100\\%%");
        assertThat(AdminSongCatalogRepository.substringPattern("a_b")).isEqualTo("%a\\_b%");
        assertThat(AdminSongCatalogRepository.substringPattern("back\\slash")).isEqualTo("%back\\\\slash%");
    }
}

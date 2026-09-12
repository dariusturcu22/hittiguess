package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.service.SongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SongControllerTest {

    @Mock
    private SongService songService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SongController(songService)).build();
    }

    @Test
    void searchSongsDelegatesTheQueryParameterToTheService() throws Exception {
        when(songService.searchCatalog("bohemian")).thenReturn(List.of());

        mockMvc.perform(get("/api/songs/search").param("query", "bohemian"))
                .andExpect(status().isOk());

        verify(songService).searchCatalog("bohemian");
    }

    @Test
    void searchSongsRejectsARequestMissingTheQueryParameter() throws Exception {
        mockMvc.perform(get("/api/songs/search"))
                .andExpect(status().isBadRequest());
    }
}

package org.dariusturcu.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Standalone MockMvc rather than @WebMvcTest: the app's real security auto-configuration
// pulls in the OAuth2 client and JwtAuthenticationFilter beans, which this environment's
// JDK can't build (same java.net.http.HttpClient limitation SongSearchIntegrationTest
// works around). Standalone setup exercises the controller and its exception handling
// without needing the full application context.
class EnumControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EnumController()).build();

    @Test
    void getCountriesReturnsEveryDeclaredCountry() throws Exception {
        mockMvc.perform(get("/api/enums/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.dariusturcu.backend.model.song.Country.values().length));
    }
}

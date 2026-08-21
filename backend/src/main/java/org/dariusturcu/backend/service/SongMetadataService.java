package org.dariusturcu.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.service.metadata.GeniusService;
import org.dariusturcu.backend.service.metadata.MusicBrainzService;
import org.dariusturcu.backend.service.metadata.WikipediaService;
import org.dariusturcu.backend.service.metadata.YouTubeMetadataService;
import org.dariusturcu.backend.util.MetadataParser;
import org.dariusturcu.backend.util.MetadataPromptBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongMetadataService {
    private final ChatClient.Builder chatClientBuilder;
    private final YouTubeMetadataService youTubeMetadataService;
    private final MusicBrainzService musicBrainzService;
    private final WikipediaService wikipediaService;
    private final GeniusService geniusService;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model:gpt-5.1}")
    private String aiModel;

    public AiResponse fetchMetadata(String youtubeUrl) {
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> allMetadata = gatherAllMetadata(youtubeUrl);
            String prompt = MetadataPromptBuilder.build(allMetadata);

            String raw = chatClientBuilder.build()
                    .prompt()
                    .user(prompt)
                    .options(OpenAiChatOptions.builder()
                            .model(aiModel)
                            .temperature(0.1)
                            .build())
                    .call()
                    .content();

            assert raw != null;
            String clean = raw.replaceAll("```json|```", "").trim();
            log.debug("AI raw response: {}", clean);
            SongMetadataResponse parsed = objectMapper.readValue(clean, SongMetadataResponse.class);

            long duration = System.currentTimeMillis() - startTime;

            return new AiResponse(parsed, aiModel, duration, LocalDateTime.now(), "SUCCESS");

        } catch (Exception e) {
            return new AiResponse(
                    null,
                    aiModel,
                    0,
                    LocalDateTime.now(),
                    "ERROR"
            );
        }
    }

    private Map<String, Object> gatherAllMetadata(String url) {
        Map<String, Object> allData = new HashMap<>();

        Map<String, String> ytData = youTubeMetadataService.fetchMetadata(url);
        allData.put("youtube", ytData);

        String title = MetadataParser.cleanYouTubeText(ytData.get("video_title"));
        String artist = MetadataParser.cleanYouTubeText(ytData.get("channel_title"));

        allData.put("musicbrainz", musicBrainzService.search(title, artist));
        allData.put("wikipedia", wikipediaService.search(title, artist));
        allData.put("genius", geniusService.search(title, artist));

        return allData;
    }
}

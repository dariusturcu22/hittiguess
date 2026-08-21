package org.dariusturcu.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.service.metadata.GeniusService;
import org.dariusturcu.backend.service.metadata.MusicBrainzService;
import org.dariusturcu.backend.service.metadata.WikipediaService;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.service.metadata.YouTubeMetadataService;
import org.dariusturcu.backend.util.MetadataParser;
import org.dariusturcu.backend.util.MetadataPromptBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    // The metadata pipeline chains several rate-limited external calls plus a paid LLM call,
    // so one user queuing many concurrent requests can tie up threads and run up cost. Capping
    // it at one in-flight request per user, rather than a time window, matches the actual risk.
    private final Set<Long> usersWithRequestInFlight = ConcurrentHashMap.newKeySet();

    @Value("${spring.ai.openai.chat.options.model:gpt-5.1}")
    private String aiModel;

    public AiResponse fetchMetadata(String youtubeUrl) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!usersWithRequestInFlight.add(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A metadata request is already in progress");
        }

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

            // A null raw here throws NPE on the next line, caught below same as any other
            // parse failure. Java assertions are disabled by default, so an assert here
            // wouldn't actually run.
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
        } finally {
            usersWithRequestInFlight.remove(userId);
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

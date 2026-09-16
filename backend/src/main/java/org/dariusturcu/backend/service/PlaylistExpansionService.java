package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.exception.PlaylistImportException;
import org.dariusturcu.backend.model.ai.PlaylistVideoIdsRequest;
import org.dariusturcu.backend.model.ai.PlaylistVideoIdsResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Expands a YouTube playlist link or bare playlist id into its video ids by calling the
 * AI microservice, the only place in the system with YouTube Data API access. A failure
 * to expand, whether the link doesn't parse or the upstream call itself fails, surfaces
 * as a PlaylistImportException rather than resolving to an empty list, so a caller never
 * mistakes a rejected playlist link for a playlist that genuinely has no videos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistExpansionService {

    private static final String PLAYLIST_VIDEO_IDS_ENDPOINT = "/metadata/playlist-video-ids";

    private final RestClient aiServiceRestClient;

    public List<String> expandPlaylist(String playlistUrlOrId) {
        try {
            PlaylistVideoIdsResponse response = aiServiceRestClient.post()
                    .uri(PLAYLIST_VIDEO_IDS_ENDPOINT)
                    .body(new PlaylistVideoIdsRequest(playlistUrlOrId))
                    .retrieve()
                    .body(PlaylistVideoIdsResponse.class);

            if (response == null || response.videoIds() == null) {
                throw new PlaylistImportException(
                        "The AI microservice returned no result for the playlist link: " + playlistUrlOrId);
            }
            return response.videoIds();
        } catch (PlaylistImportException playlistImportException) {
            throw playlistImportException;
        } catch (Exception playlistExpansionFailure) {
            log.warn("Playlist expansion call failed: {}", playlistExpansionFailure.getMessage());
            throw new PlaylistImportException(
                    "Failed to expand playlist link " + playlistUrlOrId + ": " + playlistExpansionFailure.getMessage());
        }
    }

    /**
     * Expands the playlist link, when present, and merges its video ids with a caller's
     * own already-parsed ids, deduplicating since a user might paste both a playlist link
     * and a few extra individual links or ids. Returns the already-parsed ids unchanged
     * when no playlist link was submitted.
     */
    public List<String> expandAndMerge(String playlistLink, List<String> alreadyParsedYoutubeIds) {
        if (playlistLink == null || playlistLink.isBlank()) {
            return alreadyParsedYoutubeIds;
        }

        List<String> expandedVideoIds = expandPlaylist(playlistLink);
        Set<String> mergedIds = new LinkedHashSet<>(expandedVideoIds);
        mergedIds.addAll(alreadyParsedYoutubeIds);
        return new ArrayList<>(mergedIds);
    }
}

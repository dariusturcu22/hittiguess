package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SongService {

    private final SongRepository songRepository;
    private final SongMapper songMapper;

    /**
     * Searches the whole song catalog, not any one playlist, so a song already
     * resolved elsewhere isn't resubmitted as a near-duplicate. A query that
     * parses as a YouTube link or bare video ID matches by that ID exactly;
     * anything else is matched as a keyword against title and artist name.
     */
    public List<SongDTO> searchCatalog(String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must not be blank");
        }

        String trimmedQuery = query.trim();
        List<Song> matches = YoutubeLinkParser.parseVideoId(trimmedQuery)
                .map(songRepository::findByYoutubeId)
                .orElseGet(() -> songRepository.searchByTitleOrArtistKeyword(trimmedQuery));

        return matches.stream().map(songMapper::toDTO).toList();
    }
}

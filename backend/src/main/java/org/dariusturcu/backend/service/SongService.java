package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.RecommendedSongsDTO;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    static final int DEFAULT_RECOMMENDATION_SIZE = 20;
    static final int MAXIMUM_RECOMMENDATION_SIZE = 50;

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

    /**
     * Recommends catalog songs for the add flow's default list: newest verified
     * songs first (identity order is insertion order), paged so the client can
     * fetch more. Reads one extra row to report whether another page exists.
     */
    public RecommendedSongsDTO recommendSongs(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recommendation page must not be negative");
        }
        if (size < 1 || size > MAXIMUM_RECOMMENDATION_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Recommendation size must be between 1 and " + MAXIMUM_RECOMMENDATION_SIZE);
        }

        Pageable pageRequest = PageRequest.of(page, size + 1, Sort.by(Sort.Direction.DESC, "id"));
        List<Song> matches = songRepository
                .findByVerificationStatus(VerificationStatus.VERIFIED, pageRequest)
                .getContent();
        boolean hasMore = matches.size() > size;
        List<SongDTO> songs = matches.stream()
                .limit(size)
                .map(songMapper::toDTO)
                .toList();
        return new RecommendedSongsDTO(songs, hasMore);
    }
}

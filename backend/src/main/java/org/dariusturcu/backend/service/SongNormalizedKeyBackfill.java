package org.dariusturcu.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Fills songs.normalized_key for rows saved before V52 or after a migration resets the column
// because the normalization changed. Only rows without a key are read, so a normal startup
// costs one empty query.
@Component
@Slf4j
public class SongNormalizedKeyBackfill implements ApplicationRunner {

    private final SongRepository songRepository;

    public SongNormalizedKeyBackfill(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        List<Song> songsWithoutKey = songRepository.findWithoutNormalizedKey();
        songsWithoutKey.forEach(song -> song.setNormalizedKey(GameSongList.normalizedKey(song)));
        if (!songsWithoutKey.isEmpty()) {
            log.info("Filled the normalized key of {} songs", songsWithoutKey.size());
        }
    }
}

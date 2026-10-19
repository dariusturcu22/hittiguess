package org.dariusturcu.backend.model.song;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps an alternate YouTube video ID to an existing canonical Song, so a different
 * upload of an already-known track (a lyric video, a Topic-channel version, a
 * re-upload) resolves to the same Song rather than creating a duplicate row or
 * re-running the metadata pipeline. Separate from Song's own primary youtubeId:
 * many alternate IDs point at one Song.
 */
@Entity
@Getter
@Setter
@Table(name = "alternate_youtube_ids")
public class AlternateYoutubeId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "youtube_id", nullable = false, unique = true)
    private String youtubeId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;
}

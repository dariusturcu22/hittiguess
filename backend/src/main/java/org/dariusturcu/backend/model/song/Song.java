package org.dariusturcu.backend.model.song;

import org.dariusturcu.backend.model.playlist.Playlist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.dariusturcu.backend.model.user.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "songs")
public class Song {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @OrderBy, not @OrderColumn: Hibernate only wants @OrderColumn managing the index itself
    // for a unidirectional relation; this one is mappedBy (SongArtist owns the FK back to
    // Song), so displayOrder is a real, explicitly-assigned column instead (confirmed live,
    // Hibernate's own HHH160246 warning against @OrderColumn on a mappedBy association).
    @OneToMany(mappedBy = "song", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<SongArtist> artists = new ArrayList<>();

    private String title;

    private int releaseYear;

    private String youtubeId;

    private String gradientColor1;

    private String gradientColor2;

    // Nullable, not settable through CreateSongRequest/UpdateSongRequest: populated by the
    // metadata pipeline once it runs, same as confidence and metadataRaw below.
    private String genre;

    @Enumerated(EnumType.STRING)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    private String confidence;

    // Not @Lob: on Postgres that maps a String to the oid large-object type, a reference into
    // separate large-object storage, not the plain text column the TEXT migration column is.
    @Column(columnDefinition = "TEXT")
    private String metadataRaw;

    @ManyToMany(mappedBy = "songs")
    private Set<Playlist> playlists = new HashSet<>();

    // Nullable: a song is a standalone catalog entity, independent of the account that
    // submitted it. Deleting that account clears this reference rather than blocking the
    // deletion or deleting the song, matching the same independence already established for
    // a song's relationship to its playlists.
    @ManyToOne
    @JoinColumn(name = "added_by")
    private User addedBy;
}

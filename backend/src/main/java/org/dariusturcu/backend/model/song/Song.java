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

    @OneToMany(mappedBy = "song", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "display_order")
    private List<SongArtist> artists = new ArrayList<>();

    private String title;

    private int releaseYear;

    private String youtubeId;

    private String gradientColor1;

    private String gradientColor2;

    @ElementCollection(targetClass = SongTag.class)
    @CollectionTable(name = "song_tags", joinColumns = @JoinColumn(name = "song_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag")
    private Set<SongTag> tags = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    private String confidence;

    @Lob
    private String metadataRaw;

    @ManyToOne
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne
    @JoinColumn(name = "added_by", nullable = false)
    private User addedBy;
}

package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.CatalogSongStatus;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The admin song catalog: every song plus every admin-seeded backlog item that has no song yet,
 * in one result set ordered newest first. Search narrows songs by title or artist substring
 * (case-insensitive, served by the trigram indexes) or exact YouTube ID, and backlog items by
 * exact YouTube ID. The status CASE expressions mirror CatalogSongRules. A stored release year
 * of 0 means no year was resolved and comes back null.
 */
@Repository
public class AdminSongCatalogRepository {

    private static final String ROW_KEY_SEPARATOR = ":";
    private static final String LIKE_ESCAPE_CHARACTER = "\\";
    private static final String LIKE_ANY_CHARACTERS = "%";
    private static final String LIKE_ANY_SINGLE_CHARACTER = "_";

    private static final String SONG_SEARCH_FILTER = """
            WHERE song.id IN (
                SELECT title_match.id FROM songs title_match WHERE title_match.title ILIKE ? ESCAPE '\\'
                UNION
                SELECT artist_match.song_id FROM song_artists artist_match WHERE artist_match.name ILIKE ? ESCAPE '\\'
                UNION
                SELECT youtube_match.id FROM songs youtube_match WHERE youtube_match.youtube_id = ?
            )
            """;
    private static final String QUEUE_SEARCH_FILTER = "AND queue.youtube_id = ?";
    private static final String NO_FILTER = "";

    // Song rows report their backlog facts; backlog-only rows are admin seeds whose YouTube ID
    // has no song, one row per YouTube ID, preferring PROCESSING, then PENDING, then the newest
    // FAILED row. Timestamps in pending_imports hold UTC wall time.
    private static final String CATALOG_ROWS = """
            WITH song_rows AS (
                SELECT
                    'song' AS row_kind,
                    song.id AS row_id,
                    song.id AS song_id,
                    song.youtube_id,
                    song.title,
                    NULLIF(song.release_year, 0) AS release_year,
                    CASE
                        WHEN queue_facts.processing THEN 'PROCESSING'
                        WHEN queue_facts.pending THEN 'IN_QUEUE'
                        WHEN song.verification_status = 'VERIFIED' THEN 'VERIFIED'
                        ELSE 'NEEDS_REVIEW'
                    END AS catalog_status,
                    COALESCE(queue_facts.unfinished_recheck, false) AS unfinished_recheck,
                    COALESCE(queue_facts.finished_recheck, false) AS finished_recheck,
                    latest_queue_row.origin AS latest_origin,
                    COALESCE(queue_facts.admin_seeded, false) AS admin_seeded,
                    song.created_at,
                    added_by_user.username AS added_by_username
                FROM songs song
                LEFT JOIN users added_by_user ON added_by_user.id = song.added_by
                LEFT JOIN LATERAL (
                    SELECT
                        bool_or(queue.status = 'PROCESSING') AS processing,
                        bool_or(queue.status = 'PENDING') AS pending,
                        bool_or(queue.origin IN ('FAST_TIER_RECHECK', 'USER_ADD_RECHECK')
                            AND queue.status <> 'DONE') AS unfinished_recheck,
                        bool_or(queue.origin IN ('FAST_TIER_RECHECK', 'USER_ADD_RECHECK')
                            AND queue.status = 'DONE') AS finished_recheck,
                        bool_or(queue.origin = 'ADMIN_SEED') AS admin_seeded
                    FROM pending_imports queue
                    WHERE queue.youtube_id = song.youtube_id
                ) queue_facts ON true
                LEFT JOIN LATERAL (
                    SELECT queue.origin
                    FROM pending_imports queue
                    WHERE queue.youtube_id = song.youtube_id
                    ORDER BY queue.enqueued_at DESC, queue.id DESC
                    LIMIT 1
                ) latest_queue_row ON true
                %s
            ),
            queue_rows AS (
                SELECT DISTINCT ON (queue.youtube_id)
                    'import' AS row_kind,
                    queue.id AS row_id,
                    NULL::BIGINT AS song_id,
                    queue.youtube_id,
                    NULL::VARCHAR AS title,
                    NULL::INTEGER AS release_year,
                    CASE queue.status
                        WHEN 'PROCESSING' THEN 'PROCESSING'
                        WHEN 'PENDING' THEN 'IN_QUEUE'
                        ELSE 'ERROR'
                    END AS catalog_status,
                    false AS unfinished_recheck,
                    false AS finished_recheck,
                    queue.origin AS latest_origin,
                    true AS admin_seeded,
                    min(queue.enqueued_at) OVER (PARTITION BY queue.youtube_id) AT TIME ZONE 'UTC' AS created_at,
                    NULL::VARCHAR AS added_by_username
                FROM pending_imports queue
                WHERE queue.status IN ('PENDING', 'PROCESSING', 'FAILED')
                  AND queue.origin = 'ADMIN_SEED'
                  AND NOT EXISTS (SELECT 1 FROM songs known_song WHERE known_song.youtube_id = queue.youtube_id)
                  %s
                ORDER BY queue.youtube_id,
                    CASE queue.status WHEN 'PROCESSING' THEN 0 WHEN 'PENDING' THEN 1 ELSE 2 END,
                    queue.enqueued_at DESC,
                    queue.id DESC
            ),
            catalog AS (
                SELECT * FROM song_rows
                UNION ALL
                SELECT * FROM queue_rows
            )
            """;

    private static final String PAGE_QUERY = CATALOG_ROWS + """
            SELECT page.*,
                ARRAY(
                    SELECT artist.name FROM song_artists artist
                    WHERE artist.song_id = page.song_id
                    ORDER BY artist.display_order
                ) AS artist_names
            FROM (
                SELECT * FROM catalog
                %s
                ORDER BY created_at DESC NULLS LAST, row_kind, row_id DESC
                LIMIT ? OFFSET ?
            ) page
            ORDER BY page.created_at DESC NULLS LAST, page.row_kind, page.row_id DESC
            """;
    private static final String STATUS_FILTER = "WHERE catalog_status = ?";

    private static final String COUNT_QUERY = CATALOG_ROWS + """
            SELECT catalog_status, count(*) AS row_count FROM catalog GROUP BY catalog_status
            """;

    private final JdbcTemplate core;

    public AdminSongCatalogRepository(@Qualifier("coreJdbcTemplate") JdbcTemplate core) {
        this.core = core;
    }

    /**
     * Escapes LIKE wildcards and the escape character itself in user text, then wraps it so it
     * matches anywhere in the column.
     */
    public static String substringPattern(String searchText) {
        String escapedText = searchText
                .replace(LIKE_ESCAPE_CHARACTER, LIKE_ESCAPE_CHARACTER + LIKE_ESCAPE_CHARACTER)
                .replace(LIKE_ANY_CHARACTERS, LIKE_ESCAPE_CHARACTER + LIKE_ANY_CHARACTERS)
                .replace(LIKE_ANY_SINGLE_CHARACTER, LIKE_ESCAPE_CHARACTER + LIKE_ANY_SINGLE_CHARACTER);
        return LIKE_ANY_CHARACTERS + escapedText + LIKE_ANY_CHARACTERS;
    }

    public List<AdminCatalogRow> findPage(String searchText, CatalogSongStatus status, int pageSize, long offset) {
        return core.query(pageSql(searchText, status), (resultSet, rowNumber) -> toRow(resultSet),
                pageParameters(searchText, status, pageSize, offset));
    }

    static String pageSql(String searchText, CatalogSongStatus status) {
        return PAGE_QUERY.formatted(songFilter(searchText), queueFilter(searchText),
                status == null ? NO_FILTER : STATUS_FILTER);
    }

    static Object[] pageParameters(String searchText, CatalogSongStatus status, int pageSize, long offset) {
        List<Object> parameters = new ArrayList<>(searchParameters(searchText));
        if (status != null) {
            parameters.add(status.name());
        }
        parameters.add(pageSize);
        parameters.add(offset);
        return parameters.toArray();
    }

    public Map<CatalogSongStatus, Long> countByStatus(String searchText) {
        Map<CatalogSongStatus, Long> counts = new EnumMap<>(CatalogSongStatus.class);
        Arrays.stream(CatalogSongStatus.values()).forEach(status -> counts.put(status, 0L));
        String sql = COUNT_QUERY.formatted(songFilter(searchText), queueFilter(searchText));
        core.query(sql, resultSet -> {
            counts.put(CatalogSongStatus.valueOf(resultSet.getString("catalog_status")),
                    resultSet.getLong("row_count"));
        }, searchParameters(searchText).toArray());
        return counts;
    }

    private static String songFilter(String searchText) {
        return searchText == null ? NO_FILTER : SONG_SEARCH_FILTER;
    }

    private static String queueFilter(String searchText) {
        return searchText == null ? NO_FILTER : QUEUE_SEARCH_FILTER;
    }

    private static List<Object> searchParameters(String searchText) {
        if (searchText == null) {
            return List.of();
        }
        String pattern = substringPattern(searchText);
        return List.of(pattern, pattern, searchText, searchText);
    }

    private static AdminCatalogRow toRow(ResultSet resultSet) throws SQLException {
        String latestOrigin = resultSet.getString("latest_origin");
        OffsetDateTime createdAt = resultSet.getObject("created_at", OffsetDateTime.class);
        return new AdminCatalogRow(
                resultSet.getString("row_kind") + ROW_KEY_SEPARATOR + resultSet.getLong("row_id"),
                resultSet.getObject("song_id", Long.class),
                resultSet.getString("youtube_id"),
                resultSet.getString("title"),
                artistNames(resultSet.getArray("artist_names")),
                resultSet.getObject("release_year", Integer.class),
                CatalogSongStatus.valueOf(resultSet.getString("catalog_status")),
                resultSet.getBoolean("unfinished_recheck"),
                resultSet.getBoolean("finished_recheck"),
                latestOrigin == null ? null : PendingImportOrigin.valueOf(latestOrigin),
                resultSet.getBoolean("admin_seeded"),
                createdAt == null ? null : createdAt.toInstant(),
                resultSet.getString("added_by_username"));
    }

    private static List<String> artistNames(Array artistNames) throws SQLException {
        if (artistNames == null) {
            return List.of();
        }
        return List.of((String[]) artistNames.getArray());
    }
}

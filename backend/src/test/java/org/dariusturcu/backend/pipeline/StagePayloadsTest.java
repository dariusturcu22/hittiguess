package org.dariusturcu.backend.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StagePayloadsTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final StagePayloads PAYLOADS = new StagePayloads(MAPPER);
    private static final int YEAR = 1999;
    private static final int DURATION_SECONDS = 215;
    private static final int SITELINKS = 12;
    private static final String IDENTIFICATION = """
            {"outcome":"IDENTIFIED","title":"Test song","artists":["Main artist"],
             "featuredArtists":["Guest"],"color":"112233","video":{"durationSeconds":215}}
            """;

    private PipelineJob result(PipelineStage stage, String json) {
        return new PipelineJob(0, 0, stage, PipelineLane.BACKFILL, PipelineJobStatus.DONE,
                0, PipelineJobService.DEFAULT_MAX_ATTEMPTS, 0, null, null, null, json,
                null, null, null, null, null, null, null);
    }

    @Test
    void exactAgreementBuildsTheSameLockedMetadataFieldsAsAiResolve() {
        var metadata = PAYLOADS.finalMetadata(List.of(result(PipelineStage.YOUTUBE_METADATA, IDENTIFICATION),
                result(PipelineStage.MUSICBRAINZ, "{\"earliestYear\":1999}"),
                result(PipelineStage.DISCOGS, "{\"earliestYear\":1999}"),
                result(PipelineStage.WIKIDATA, "{\"earliestYear\":1999,\"sitelinksCount\":12}")));
        assertThat(metadata.title()).isEqualTo("Test song");
        assertThat(metadata.mainArtists()).containsExactly("Main artist");
        assertThat(metadata.featuredArtists()).containsExactly("Guest");
        assertThat(metadata.releaseYear()).isEqualTo(YEAR);
        assertThat(metadata.durationSeconds()).isEqualTo(DURATION_SECONDS);
        assertThat(metadata.sitelinksCount()).isEqualTo(SITELINKS);
        assertThat(metadata.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(metadata.source()).isEqualTo("musicbrainz+discogs+wikidata-lock");
        assertThat(metadata.confidence()).isEqualTo("high");
    }

    @Test
    void finalReconciliationUsesTheSnakeCaseWireContract() {
        var metadata = PAYLOADS.finalMetadata(List.of(result(PipelineStage.YOUTUBE_METADATA, IDENTIFICATION),
                result(PipelineStage.LLM_RECONCILIATION, """
                        {"status":"SUCCESS","content":{"title":"Reconciled song","main_artists":["Artist"],
                         "featured_artists":[],"release_year":1999,"verification_status":"NEEDS_REVIEW",
                         "duration_seconds":215,"sitelinks_count":12}}
                        """)));
        assertThat(metadata.title()).isEqualTo("Reconciled song");
        assertThat(metadata.releaseYear()).isEqualTo(YEAR);
        assertThat(metadata.durationSeconds()).isEqualTo(DURATION_SECONDS);
        assertThat(metadata.sitelinksCount()).isEqualTo(SITELINKS);
        assertThat(metadata.verificationStatus()).isEqualTo("NEEDS_REVIEW");
    }
}

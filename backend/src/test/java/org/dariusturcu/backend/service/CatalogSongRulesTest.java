package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.CatalogAddedByKind;
import org.dariusturcu.backend.model.song.CatalogPipeline;
import org.dariusturcu.backend.model.song.CatalogSongStatus;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSongRulesTest {

    private static final Instant EARLIER = Instant.parse("2026-10-14T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-15T09:00:00Z");
    private static final String USERNAME = "mara.k";

    private static PendingImport queueRow(long id, PendingImportOrigin origin, PendingImportStatus status,
                                          Instant enqueuedAt) {
        PendingImport queueRow = new PendingImport();
        queueRow.setId(id);
        queueRow.setOrigin(origin);
        queueRow.setStatus(status);
        queueRow.setEnqueuedAt(enqueuedAt);
        return queueRow;
    }

    @Test
    void processingWinsOverPendingAsTheActiveQueueStatus() {
        assertThat(CatalogSongRules.activeQueueStatus(Set.of(PendingImportStatus.PENDING, PendingImportStatus.PROCESSING)))
                .isEqualTo(PendingImportStatus.PROCESSING);
        assertThat(CatalogSongRules.activeQueueStatus(Set.of(PendingImportStatus.PENDING, PendingImportStatus.DONE)))
                .isEqualTo(PendingImportStatus.PENDING);
        assertThat(CatalogSongRules.activeQueueStatus(Set.of(PendingImportStatus.DONE, PendingImportStatus.FAILED)))
                .isNull();
    }

    @ParameterizedTest
    @EnumSource(VerificationStatus.class)
    void anActiveQueueRowShowsTheQueueStateWhateverTheVerificationStatus(VerificationStatus verificationStatus) {
        assertThat(CatalogSongRules.songStatus(verificationStatus, PendingImportStatus.PROCESSING))
                .isEqualTo(CatalogSongStatus.PROCESSING);
        assertThat(CatalogSongRules.songStatus(verificationStatus, PendingImportStatus.PENDING))
                .isEqualTo(CatalogSongStatus.IN_QUEUE);
    }

    @Test
    void withoutAnActiveQueueRowOnlyVerifiedSongsAreVerified() {
        assertThat(CatalogSongRules.songStatus(VerificationStatus.VERIFIED, null)).isEqualTo(CatalogSongStatus.VERIFIED);
        assertThat(CatalogSongRules.songStatus(VerificationStatus.NEEDS_REVIEW, null)).isEqualTo(CatalogSongStatus.NEEDS_REVIEW);
        assertThat(CatalogSongRules.songStatus(VerificationStatus.UNVERIFIED, null)).isEqualTo(CatalogSongStatus.NEEDS_REVIEW);
        assertThat(CatalogSongRules.songStatus(VerificationStatus.MANUAL_ENTRY, null)).isEqualTo(CatalogSongStatus.NEEDS_REVIEW);
    }

    @Test
    void aBacklogItemWithoutASongIsQueuedProcessingOrInError() {
        assertThat(CatalogSongRules.queueItemStatus(PendingImportStatus.PENDING)).isEqualTo(CatalogSongStatus.IN_QUEUE);
        assertThat(CatalogSongRules.queueItemStatus(PendingImportStatus.PROCESSING)).isEqualTo(CatalogSongStatus.PROCESSING);
        assertThat(CatalogSongRules.queueItemStatus(PendingImportStatus.FAILED)).isEqualTo(CatalogSongStatus.ERROR);
    }

    @Test
    void anUnfinishedRecheckIsTheFastPipeline() {
        assertThat(CatalogSongRules.pipeline(true, false, PendingImportOrigin.FAST_TIER_RECHECK))
                .isEqualTo(CatalogPipeline.FAST);
        assertThat(CatalogSongRules.pipeline(true, true, PendingImportOrigin.ADMIN_SEED))
                .isEqualTo(CatalogPipeline.FAST);
    }

    @Test
    void anAdminSeedOrAFinishedRecheckIsTheFullPipeline() {
        assertThat(CatalogSongRules.pipeline(false, false, PendingImportOrigin.ADMIN_SEED))
                .isEqualTo(CatalogPipeline.FULL);
        assertThat(CatalogSongRules.pipeline(false, true, PendingImportOrigin.USER_ADD_RECHECK))
                .isEqualTo(CatalogPipeline.FULL);
    }

    @Test
    void noBacklogEvidenceIsAnUnknownPipeline() {
        assertThat(CatalogSongRules.pipeline(false, false, null)).isEqualTo(CatalogPipeline.UNKNOWN);
        assertThat(CatalogSongRules.pipeline(List.of())).isEqualTo(CatalogPipeline.UNKNOWN);
    }

    @Test
    void thePipelineFromQueueRowsUsesTheLatestRowAndRecheckStates() {
        List<PendingImport> pendingRecheck = List.of(
                queueRow(1, PendingImportOrigin.FAST_TIER_RECHECK, PendingImportStatus.PENDING, LATER));
        List<PendingImport> failedRecheck = List.of(
                queueRow(1, PendingImportOrigin.USER_ADD_RECHECK, PendingImportStatus.FAILED, LATER));
        List<PendingImport> finishedRecheck = List.of(
                queueRow(1, PendingImportOrigin.FAST_TIER_RECHECK, PendingImportStatus.DONE, LATER));
        List<PendingImport> latestIsAdminSeed = List.of(
                queueRow(2, PendingImportOrigin.ADMIN_SEED, PendingImportStatus.DONE, LATER),
                queueRow(1, PendingImportOrigin.ADMIN_SEED, PendingImportStatus.FAILED, EARLIER));

        assertThat(CatalogSongRules.pipeline(pendingRecheck)).isEqualTo(CatalogPipeline.FAST);
        assertThat(CatalogSongRules.pipeline(failedRecheck)).isEqualTo(CatalogPipeline.FAST);
        assertThat(CatalogSongRules.pipeline(finishedRecheck)).isEqualTo(CatalogPipeline.FULL);
        assertThat(CatalogSongRules.pipeline(latestIsAdminSeed)).isEqualTo(CatalogPipeline.FULL);
    }

    @Test
    void addedByIsTheUserThenAnAdminSeedThenUnknown() {
        assertThat(CatalogSongRules.addedByKind(USERNAME, true)).isEqualTo(CatalogAddedByKind.USER);
        assertThat(CatalogSongRules.addedByKind(null, true)).isEqualTo(CatalogAddedByKind.ADMIN_SEED);
        assertThat(CatalogSongRules.addedByKind(null, false)).isEqualTo(CatalogAddedByKind.UNKNOWN);
    }
}

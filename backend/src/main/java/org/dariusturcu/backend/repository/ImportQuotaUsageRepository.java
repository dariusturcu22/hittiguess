package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.importquota.ImportQuotaUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ImportQuotaUsageRepository extends JpaRepository<ImportQuotaUsage, ImportQuotaUsage.Key> {

    // Adds to the day's count in one statement, and only while the total stays within the
    // limit, so concurrent imports can't together pass it. Returns 1 when the count was
    // added and 0 when it would have passed the limit. A caller must not pass more than the
    // limit on its own, since the first insert of the day is not checked.
    @Modifying
    @Query(value = "INSERT INTO import_quota_usage (user_id, usage_date, resolution_count) "
            + "VALUES (:userId, :usageDate, :additionalCount) "
            + "ON CONFLICT (user_id, usage_date) DO UPDATE "
            + "SET resolution_count = import_quota_usage.resolution_count + EXCLUDED.resolution_count "
            + "WHERE import_quota_usage.resolution_count + EXCLUDED.resolution_count <= :dailyLimit",
            nativeQuery = true)
    int addWithinDailyLimit(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate,
                            @Param("additionalCount") int additionalCount, @Param("dailyLimit") int dailyLimit);

    @Query("select usage.resolutionCount from ImportQuotaUsage usage "
            + "where usage.userId = :userId and usage.usageDate = :usageDate")
    Optional<Integer> findResolutionCount(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);
}

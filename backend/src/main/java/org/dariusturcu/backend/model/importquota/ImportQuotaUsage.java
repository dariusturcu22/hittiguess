package org.dariusturcu.backend.model.importquota;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

// One user's count of new songs resolved through the paid metadata pipeline on one UTC day.
@Entity
@Getter
@Setter
@Table(name = "import_quota_usage")
@IdClass(ImportQuotaUsage.Key.class)
public class ImportQuotaUsage {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "resolution_count", nullable = false)
    private int resolutionCount;

    public record Key(Long userId, LocalDate usageDate) implements Serializable {
    }
}

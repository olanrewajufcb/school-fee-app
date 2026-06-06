package com.fee.app.schoolfeeapp.result.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "published_results", schema = "result")
public class PublishedResult {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    @Column("term_id") private UUID termId;
    @Column("published_by") private UUID publishedBy;
    @Column("published_at") private Instant publishedAt;
}
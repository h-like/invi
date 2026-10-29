package com.invi.api.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.invi.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "template")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Template extends BaseEntity {

    /**
     * Stable preset key shared by every environment (UUIDs differ per DB). Nullable only
     * so schema update can add it to rows seeded before it existed; the seeder claims those.
     */
    @Column(unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_page_data", columnDefinition = "jsonb")
    private JsonNode defaultPageData;

    private String thumbnailUrl;

    /** Aligns this row with its code-defined preset. Returns whether anything changed. */
    public boolean syncPreset(String code, String name, String category, JsonNode defaultPageData, String thumbnailUrl) {
        boolean changed =
                !Objects.equals(this.code, code)
                        || !Objects.equals(this.name, name)
                        || !Objects.equals(this.category, category)
                        || !Objects.equals(this.defaultPageData, defaultPageData)
                        || !Objects.equals(this.thumbnailUrl, thumbnailUrl);
        this.code = code;
        this.name = name;
        this.category = category;
        this.defaultPageData = defaultPageData;
        this.thumbnailUrl = thumbnailUrl;
        return changed;
    }
}

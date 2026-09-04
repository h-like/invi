package com.invi.api.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.invi.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_page_data", columnDefinition = "jsonb")
    private JsonNode defaultPageData;

    private String thumbnailUrl;
}

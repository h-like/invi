package com.invi.api.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import com.invi.api.account.Member;
import com.invi.api.common.BaseEntity;
import com.invi.api.template.Template;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "invitation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Invitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, unique = true)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(nullable = false)
    private LocalDate weddingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Plan plan = Plan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode pageData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode langVariants;

    private Instant expiresAt;

    /** Bumped by Hibernate on every update; the editor sends it back to detect stale saves. */
    @Version
    @Column(nullable = false)
    private Long version;

    public void updatePageData(JsonNode pageData) {
        this.pageData = pageData;
    }

    public void updateLangVariants(JsonNode langVariants) {
        this.langVariants = langVariants;
    }

    public void updateWeddingDate(LocalDate weddingDate) {
        this.weddingDate = weddingDate;
    }

    /**
     * Key to the private review view ({@code /r/{token}}). Written only through
     * {@link InvitationRepository#updateReviewToken}, never via this entity, so making or revoking
     * a link never bumps {@link #version} and the editor's next page save is not refused as stale.
     * Not updatable, so flushing this entity can never write back a token read before a change.
     */
    @Column(name = "review_token", unique = true, length = 64, updatable = false)
    private String reviewToken;

    public void publish() {
        this.status = InvitationStatus.PUBLISHED;
    }

    /**
     * Back to a draft: the public link stops working, RSVPs and guestbook entries are kept.
     * Only a published invitation changes; a draft or archived one is left as it is.
     */
    public void unpublish() {
        if (this.status == InvitationStatus.PUBLISHED) {
            this.status = InvitationStatus.DRAFT;
        }
    }
}

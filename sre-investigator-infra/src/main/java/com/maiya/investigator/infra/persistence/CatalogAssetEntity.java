package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "investigator_catalog_asset")
public class CatalogAssetEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 80)
    private String route;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 20)
    private String origin;

    @Column(name = "catalog_id", length = 80)
    private String catalogId;

    @Column(name = "package_name", length = 200)
    private String packageName;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "git_ref", length = 120)
    private String gitRef;

    @Column(name = "command_line", length = 200)
    private String commandLine;

    @Column(length = 512)
    private String args;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(length = 280)
    private String summary;

    @Column(length = 400)
    private String tags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CatalogAssetEntity() {
    }

    public CatalogAssetEntity(
            UUID id,
            UUID projectId,
            String kind,
            String source,
            String slug,
            String name,
            String route,
            boolean enabled,
            String origin,
            String catalogId,
            String packageName,
            String sourceUrl,
            String gitRef,
            String commandLine,
            String args,
            String content,
            String summary,
            String tags,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.kind = kind;
        this.source = source;
        this.slug = slug;
        this.name = name;
        this.route = route;
        this.enabled = enabled;
        this.origin = origin;
        this.catalogId = catalogId;
        this.packageName = packageName;
        this.sourceUrl = sourceUrl;
        this.gitRef = gitRef;
        this.commandLine = commandLine;
        this.args = args;
        this.content = content;
        this.summary = summary;
        this.tags = tags;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getKind() {
        return kind;
    }

    public String getSource() {
        return source;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getRoute() {
        return route;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getOrigin() {
        return origin;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getGitRef() {
        return gitRef;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public String getArgs() {
        return args;
    }

    public String getContent() {
        return content;
    }

    public String getSummary() {
        return summary;
    }

    public String getTags() {
        return tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "investigator_catalog_asset_file")
public class CatalogAssetFileEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "asset_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID assetId;

    @Column(nullable = false, length = 200)
    private String path;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CatalogAssetFileEntity() {
    }

    public CatalogAssetFileEntity(UUID id, UUID assetId, String path, String content, int sortOrder) {
        this.id = id;
        this.assetId = assetId;
        this.path = path;
        this.content = content;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

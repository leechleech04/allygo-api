package com.allygo.allygo_api.apprelease.infrastructure.persistence;

import com.allygo.allygo_api.apprelease.domain.AppPlatform;
import com.allygo.allygo_api.apprelease.domain.AppReleasePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "app_release_policies")
public class AppReleasePolicyJpaEntity {

    @Id
    @Column(name = "release_policy_id", nullable = false)
    private Long releasePolicyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, unique = true)
    private AppPlatform platform;

    @Column(name = "minimum_supported_version", nullable = false, length = 30)
    private String minimumSupportedVersion;

    @Column(name = "latest_version", nullable = false, length = 30)
    private String latestVersion;

    @Column(name = "maintenance_enabled", nullable = false)
    private boolean maintenanceEnabled;

    @Column(name = "maintenance_message")
    private String maintenanceMessage;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AppReleasePolicyJpaEntity() {
    }

    public AppReleasePolicyJpaEntity(
            Long releasePolicyId,
            AppPlatform platform,
            String minimumSupportedVersion,
            String latestVersion,
            boolean maintenanceEnabled,
            String maintenanceMessage,
            OffsetDateTime updatedAt
    ) {
        this.releasePolicyId = releasePolicyId;
        this.platform = platform;
        this.minimumSupportedVersion = minimumSupportedVersion;
        this.latestVersion = latestVersion;
        this.maintenanceEnabled = maintenanceEnabled;
        this.maintenanceMessage = maintenanceMessage;
        this.updatedAt = updatedAt;
    }

    AppReleasePolicy toDomain() {
        return new AppReleasePolicy(
                releasePolicyId,
                platform,
                minimumSupportedVersion,
                latestVersion,
                maintenanceEnabled,
                maintenanceMessage,
                updatedAt
        );
    }
}

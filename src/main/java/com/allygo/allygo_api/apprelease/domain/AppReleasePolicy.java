package com.allygo.allygo_api.apprelease.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record AppReleasePolicy(
    Long releasePolicyId,
    AppPlatform platform,
    String minimumSupportedVersion,
    String latestVersion,
    boolean maintenanceEnabled,
    String maintenanceMessage,
    OffsetDateTime updatedAt
) {

    private static final Pattern SEMANTIC_VERSION_PATTERN = Pattern.compile(
        "^(0|[1-9]\\d*)\\." +
            "(0|[1-9]\\d*)\\." +
            "(0|[1-9]\\d*)" +
            "(?:-((?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)" +
            "(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*))?" +
            "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$"
    );

    public AppReleasePolicy {
        Objects.requireNonNull(
            releasePolicyId,
            "releasePolicyId must not be null"
        );
        Objects.requireNonNull(
            platform,
            "platform must not be null"
        );
        Objects.requireNonNull(
            updatedAt,
            "updatedAt must not be null"
        );

        SemanticVersion minimumVersion =
            parseVersion(minimumSupportedVersion, "minimumSupportedVersion");

        SemanticVersion latest =
            parseVersion(latestVersion, "latestVersion");

        if (minimumVersion.compareTo(latest) > 0) {
            throw new IllegalArgumentException(
                "minimumSupportedVersion must not be greater than latestVersion"
            );
        }

        if (maintenanceEnabled
            && (maintenanceMessage == null || maintenanceMessage.isBlank())) {
            throw new IllegalArgumentException(
                "maintenanceMessage must be provided when maintenance is enabled"
            );
        }
    }

    public String effectiveMaintenanceMessage() {
        return maintenanceEnabled ? maintenanceMessage : null;
    }

    private static SemanticVersion parseVersion(
        String version,
        String fieldName
    ) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        if (version.length() > 30) {
            throw new IllegalArgumentException(
                fieldName + " must not exceed 30 characters"
            );
        }

        Matcher matcher = SEMANTIC_VERSION_PATTERN.matcher(version);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                fieldName + " must follow Semantic Versioning, for example 1.2.3"
            );
        }

        try {
            return new SemanticVersion(
                Long.parseLong(matcher.group(1)),
                Long.parseLong(matcher.group(2)),
                Long.parseLong(matcher.group(3)),
                matcher.group(4)
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                fieldName + " contains a version number that is too large",
                exception
            );
        }
    }

    private record SemanticVersion(
        long major,
        long minor,
        long patch,
        String preRelease
    ) implements Comparable<SemanticVersion> {

        @Override
        public int compareTo(SemanticVersion other) {
            int comparison = Long.compare(major, other.major);

            if (comparison != 0) {
                return comparison;
            }

            comparison = Long.compare(minor, other.minor);

            if (comparison != 0) {
                return comparison;
            }

            comparison = Long.compare(patch, other.patch);

            if (comparison != 0) {
                return comparison;
            }

            return comparePreRelease(preRelease, other.preRelease);
        }

        private static int comparePreRelease(
            String left,
            String right
        ) {
            // 정식 버전은 prerelease 버전보다 우선순위가 높다.
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }

            String[] leftIdentifiers = left.split("\\.");
            String[] rightIdentifiers = right.split("\\.");

            int length = Math.min(
                leftIdentifiers.length,
                rightIdentifiers.length
            );

            for (int i = 0; i < length; i++) {
                int comparison = compareIdentifier(
                    leftIdentifiers[i],
                    rightIdentifiers[i]
                );

                if (comparison != 0) {
                    return comparison;
                }
            }

            return Integer.compare(
                leftIdentifiers.length,
                rightIdentifiers.length
            );
        }

        private static int compareIdentifier(
            String left,
            String right
        ) {
            boolean leftNumeric = isNumeric(left);
            boolean rightNumeric = isNumeric(right);

            if (leftNumeric && rightNumeric) {
                return Long.compare(
                    Long.parseLong(left),
                    Long.parseLong(right)
                );
            }

            // SemVer에서는 숫자 식별자가 문자 식별자보다 우선순위가 낮다.
            if (leftNumeric) {
                return -1;
            }
            if (rightNumeric) {
                return 1;
            }

            return left.compareTo(right);
        }

        private static boolean isNumeric(String value) {
            return value.chars().allMatch(Character::isDigit);
        }
    }
}

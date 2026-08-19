package com.example.statementservice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EndpointLabelTest {

    @Test
    void GivenUriWithoutDownloadSegment_WhenLabeled_ThenReturnsUriUnchanged() {
        // Given
        var uri = "/api/v1/statements/search";

        // When
        var label = EndpointLabel.of(uri);

        // Then
        assertThat(label).isEqualTo("/api/v1/statements/search");
    }

    @Test
    void GivenUriCarryingAFileNameAfterDownload_WhenLabeled_ThenFileNameIsTruncatedAway() {
        // Given
        var uri = "/api/v1/statements/download/statement-2026-07.pdf";

        // When
        var label = EndpointLabel.of(uri);

        // Then
        assertThat(label).isEqualTo("/api/v1/statements/download");
    }

    @Test
    void GivenUriEndingExactlyAtDownload_WhenLabeled_ThenReturnsUriUnchanged() {
        // Given: no trailing slash after "download", so nothing follows it to truncate
        var uri = "/api/v1/statements/download";

        // When
        var label = EndpointLabel.of(uri);

        // Then
        assertThat(label).isEqualTo("/api/v1/statements/download");
    }

    @Test
    void GivenAuditLogsUriWithTwoStaticSegmentsAfterBase_WhenLabeled_ThenNoSegmentIsDropped() {
        // Given: /audit/logs has no path variable, unlike /download/{fileName}
        var uri = "/api/v1/statements/audit/logs";

        // When
        var label = EndpointLabel.of(uri);

        // Then
        assertThat(label).isEqualTo("/api/v1/statements/audit/logs");
    }
}

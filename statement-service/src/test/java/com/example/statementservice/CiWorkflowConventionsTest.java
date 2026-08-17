package com.example.statementservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

class CiWorkflowConventionsTest {

    private static final Pattern FULL_COMMIT_SHA_PIN = Pattern.compile(".+@[0-9a-f]{40}$");

    private static JsonNode workflow;
    private static JsonNode dependabot;

    @BeforeAll
    static void loadCiConfigFiles() {
        Path repoRoot = findRepoRoot();
        YAMLMapper yaml = new YAMLMapper();
        workflow = yaml.readTree(repoRoot.resolve(".github/workflows/ci.yml").toFile());
        dependabot = yaml.readTree(repoRoot.resolve(".github/dependabot.yml").toFile());
    }

    private static Path findRepoRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (current.resolve(".github").toFile().isDirectory()) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "No ancestor of " + System.getProperty("user.dir") + " contains a .github directory");
    }

    @Test
    void GivenCiWorkflow_WhenInspectingActionSteps_ThenAllActionsArePinnedToFullCommitShas() {
        // Given
        JsonNode jobs = workflow.get("jobs");

        // When
        List<String> unpinned = new ArrayList<>();
        for (Map.Entry<String, JsonNode> job : jobs.properties()) {
            JsonNode steps = job.getValue().get("steps");
            if (steps == null) {
                continue;
            }
            for (JsonNode step : steps) {
                JsonNode uses = step.get("uses");
                if (uses != null
                        && !FULL_COMMIT_SHA_PIN.matcher(uses.asString()).matches()) {
                    unpinned.add(job.getKey() + ": " + uses.asString());
                }
            }
        }

        // Then
        assertThat(unpinned)
                .as("every 'uses:' step must be pinned to a full 40-character commit SHA (owner/repo@<sha>)")
                .isEmpty();
    }

    @Test
    void GivenCiWorkflow_WhenReadingWorkflowPermissions_ThenOnlyContentsReadIsGranted() {
        // Given
        JsonNode workflowRoot = workflow;

        // When
        JsonNode permissions = workflowRoot.get("permissions");

        // Then
        assertThat(permissions)
                .as("workflow must declare a top-level 'permissions' block")
                .isNotNull();
        assertThat(permissions.properties())
                .as("permissions must grant nothing beyond contents: read")
                .hasSize(1);
        assertThat(permissions.get("contents")).isNotNull();
        assertThat(permissions.get("contents").asString()).isEqualTo("read");
    }

    @Test
    void GivenCiWorkflow_WhenInspectingJobs_ThenEveryJobDeclaresATimeout() {
        // Given
        JsonNode jobs = workflow.get("jobs");

        // When
        List<String> missingTimeout = new ArrayList<>();
        for (Map.Entry<String, JsonNode> job : jobs.properties()) {
            JsonNode timeout = job.getValue().get("timeout-minutes");
            if (timeout == null || timeout.asInt(0) <= 0) {
                missingTimeout.add(job.getKey());
            }
        }

        // Then
        assertThat(missingTimeout)
                .as("every job must declare a positive timeout-minutes")
                .isEmpty();
    }

    @Test
    void GivenCiWorkflow_WhenReadingConcurrencyConfig_ThenStaleRunsAreCancelledExceptOnMain() {
        // Given
        JsonNode workflowRoot = workflow;

        // When
        JsonNode concurrency = workflowRoot.get("concurrency");

        // Then
        assertThat(concurrency)
                .as("workflow must declare a top-level 'concurrency' block")
                .isNotNull();
        assertThat(concurrency.get("group")).isNotNull();
        assertThat(concurrency.get("cancel-in-progress")).isNotNull();
        assertThat(concurrency.get("cancel-in-progress").asString())
                .as("in-flight main builds must never be cancelled")
                .contains("github.ref")
                .contains("refs/heads/main");
    }

    @Test
    void GivenCiWorkflow_WhenReadingTriggers_ThenPullRequestTargetIsNeverUsed() {
        // Given
        JsonNode workflowRoot = workflow;

        // When
        JsonNode triggers = workflowRoot.get("on");
        boolean pullRequestTargetPresent = containsPropertyNamed(workflowRoot, "pull_request_target");

        // Then
        assertThat(triggers).as("workflow must declare an 'on' trigger block").isNotNull();
        assertThat(triggers.get("pull_request")).isNotNull();
        assertThat(triggers.get("pull_request").get("branches").toString()).contains("main");
        assertThat(pullRequestTargetPresent)
                .as("pull_request_target is privilege-escalating and must never appear in the workflow")
                .isFalse();
    }

    private static boolean containsPropertyNamed(JsonNode node, String propertyName) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (entry.getKey().equals(propertyName) || containsPropertyNamed(entry.getValue(), propertyName)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                if (containsPropertyNamed(element, propertyName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void GivenDependabotConfig_WhenReadingEcosystems_ThenActionsAndMavenAreCovered() {
        // Given
        JsonNode updates = dependabot.get("updates");

        // When
        List<String> ecosystems = new ArrayList<>();
        for (JsonNode update : updates) {
            ecosystems.add(update.get("package-ecosystem").asString());
        }

        // Then
        assertThat(ecosystems).contains("github-actions", "maven");
    }
}

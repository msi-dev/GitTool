package com.msi.gittool.util

import com.msi.gittool.data.local.db.ActivityLogEntity

data class AiAnalysisResult(
    val summary: String,
    val rootCause: String,
    val solutionSteps: List<String>,
    val suggestions: List<String>
)

object ActivityAiAnalyzer {

    fun analyzeActivity(log: ActivityLogEntity): AiAnalysisResult {
        val isFailed = log.status.equals("FAILED", ignoreCase = true) || log.status.equals("ERROR", ignoreCase = true)
        val isWarning = log.status.equals("WARNING", ignoreCase = true)
        val type = log.type.uppercase()
        val details = log.details ?: ""
        val msg = log.message

        return if (isFailed) {
            when {
                type == "UPLOAD" || type == "PUSH" || type == "COMMIT" -> {
                    AiAnalysisResult(
                        summary = "Code push or file commit failed to synchronize with GitHub remote.",
                        rootCause = if (details.contains("401") || details.contains("403") || details.contains("Auth")) {
                            "Authentication credentials expired or Personal Access Token (PAT) lacks write 'repo' scope."
                        } else if (details.contains("conflict") || details.contains("rejected")) {
                            "Remote repository branch contains commits that you do not have locally (non-fast-forward push rejection)."
                        } else {
                            "Network interruption or remote repository permission restriction."
                        },
                        solutionSteps = listOf(
                            "Verify your GitHub PAT token in Settings -> Credentials Manager.",
                            "Ensure the token has 'repo' (full control of private repositories) permissions checked.",
                            "Pull the latest remote commits first before attempting to commit or push again.",
                            "Check your internet connection or active VPN proxy configuration."
                        ),
                        suggestions = listOf(
                            "Re-authenticate via OAuth in Settings for automatic scope management.",
                            "Check file diff before pushing to prevent merge conflict overrides."
                        )
                    )
                }
                type == "FORK" || type == "IMPORT" -> {
                    AiAnalysisResult(
                        summary = "Repository fork or archive import operation could not be completed.",
                        rootCause = "Remote repository name conflict or network timeout during zip archive extraction.",
                        solutionSteps = listOf(
                            "Check if a repository with the same name already exists in your GitHub account.",
                            "Verify that the source repository is public or you have read permission.",
                            "If importing a local ZIP, ensure the zip format is valid and under 100 MB."
                        ),
                        suggestions = listOf(
                            "Rename the target repository in the import modal.",
                            "Clear local app cache in Settings before re-trying."
                        )
                    )
                }
                else -> {
                    AiAnalysisResult(
                        summary = "Operation failed during $type activity execution.",
                        rootCause = "Execution error: $msg ${if (details.isNotBlank()) "($details)" else ""}",
                        solutionSteps = listOf(
                            "Review technical log details attached to this notification.",
                            "Check network connectivity and GitHub API status.",
                            "Retry the operation or re-login if credentials expired."
                        ),
                        suggestions = listOf(
                            "Check Settings -> Credentials Manager to verify connection status.",
                            "Export technical log for debugging if issue persists."
                        )
                    )
                }
            }
        } else if (isWarning) {
            AiAnalysisResult(
                summary = "Activity completed with warning warnings.",
                rootCause = "Non-blocking discrepancy detected during execution.",
                solutionSteps = listOf(
                    "Verify file integrity or repository settings.",
                    "Check if rate limits are nearing threshold."
                ),
                suggestions = listOf(
                    "Refresh repository data from the top bar.",
                    "Review recent repository activity logs."
                )
            )
        } else {
            // SUCCESS
            when (type) {
                "UPLOAD", "COMMIT", "PUSH" -> AiAnalysisResult(
                    summary = "Successful code commit and synchronization.",
                    rootCause = "All files parsed, validated, and pushed cleanly via JGit engine.",
                    solutionSteps = listOf("No action required. Your remote repository is up to date!"),
                    suggestions = listOf(
                        "Create a Release tag for this commit version.",
                        "Share repository link or bookmark for quick access."
                    )
                )
                "IMPORT", "FORK" -> AiAnalysisResult(
                    summary = "Repository successfully imported into active workspace.",
                    rootCause = "Source tree extracted and local/remote repository created with success.",
                    solutionSteps = listOf("No action needed."),
                    suggestions = listOf(
                        "Open File Browser tab to inspect imported source code.",
                        "Check Insights graphics for language distribution stats."
                    )
                )
                else -> AiAnalysisResult(
                    summary = "Activity '$type' completed successfully.",
                    rootCause = "Execution completed without errors.",
                    solutionSteps = listOf("No action required."),
                    suggestions = listOf("Continue enjoying GitTool companion capabilities!")
                )
            }
        }
    }
}

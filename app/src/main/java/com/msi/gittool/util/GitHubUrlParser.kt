package com.msi.gittool.util

object GitHubUrlParser {
    data class ParsedRepoInfo(
        val owner: String,
        val repoName: String
    ) {
        val fullName: String get() = "$owner/$repoName"
        val httpsUrl: String get() = "https://github.com/$owner/$repoName"
        val cloneUrl: String get() = "https://github.com/$owner/$repoName.git"
    }

    /**
     * Parses a GitHub repository URL or repository specifier into owner and repo name.
     * Supports formats like:
     * - https://github.com/msi-dev/Music.git
     * - https://github.com/msi-dev/Music
     * - http://github.com/msi-dev/Music/
     * - github.com/msi-dev/Music.git
     * - git@github.com:msi-dev/Music.git
     * - msi-dev/Music
     */
    fun parse(input: String): ParsedRepoInfo? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Strip common prefixes
        var cleaned = trimmed
            .removePrefix("git@github.com:")
            .removePrefix("ssh://git@github.com/")
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("github.com/")
            .trim('/')

        // Remove .git suffix if present
        if (cleaned.endsWith(".git", ignoreCase = true)) {
            cleaned = cleaned.substring(0, cleaned.length - 4)
        }

        // Remove trailing tree/branch path if user copied a sub-page URL e.g. /tree/main
        if (cleaned.contains("/tree/")) {
            cleaned = cleaned.substringBefore("/tree/")
        } else if (cleaned.contains("/blob/")) {
            cleaned = cleaned.substringBefore("/blob/")
        }

        val parts = cleaned.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val owner = parts[0]
            val repoName = parts[1]
            if (owner.isNotEmpty() && repoName.isNotEmpty()) {
                return ParsedRepoInfo(owner = owner, repoName = repoName)
            }
        }
        return null
    }
}

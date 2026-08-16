package com.example.roboticgit.data

import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.AccountType

/**
 * Where an account's REST API lives.
 *
 * The repository listing was built for GitHub and then pointed at whatever base
 * URL an account carried, which meant a self-hosted Gitea was asked for
 * `<instance>/user/repos`. Gitea serves that under `/api/v1/`, so the request
 * 404'd and the Import tab silently showed nothing.
 *
 * Gitea's response needs no other adaptation: `id`, `name`, `full_name`,
 * `clone_url`, `private` and `description` are all named as GitHub names them,
 * and it accepts the same `Authorization: Bearer` header.
 */
fun Account.repoListingBaseUrl(): String? = when (type) {
    AccountType.GITHUB -> baseUrl?.let(::asBaseUrl) ?: "https://api.github.com/"

    AccountType.GITEA -> baseUrl?.let { "${it.trimEnd('/')}/api/v1/" }

    // GitLab's API is shaped differently -- /api/v4/projects, with
    // path_with_namespace and http_url_to_repo rather than full_name and
    // clone_url -- so listing is not wired up for it. A custom instance could be
    // anything at all. Both can still be cloned by pasting a URL into the Clone
    // tab; only the browse-and-pick list is unavailable.
    AccountType.GITLAB, AccountType.CUSTOM -> null
}

/** Retrofit requires a base URL that ends in a slash. */
private fun asBaseUrl(url: String): String = if (url.endsWith("/")) url else "$url/"

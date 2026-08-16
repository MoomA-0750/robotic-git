# Git hooks

Tracked in the repository rather than left in `.git/hooks`, which is not
version-controlled and does not survive a fresh clone.

## Install (once per clone)

```sh
git config core.hooksPath .githooks
```

## pre-push

Refuses a push that would publish content meant to stay on this machine.

The policy: **what leaves this machine must be safe to be public**, whether or
not the remote repository is private. A private repository can be made public
later, is readable by everyone added to it, and gets mirrored by tooling that
does not consult the setting.

It scans only the commits actually being pushed, and only *added* lines — so the
commit that removes a leaked secret is not itself blocked.

Blocks on:

- Provider-issued tokens matched by their documented prefix and length
  (GitHub classic and fine-grained, GitLab, Slack, AWS, Google API keys)
- Any PEM private key block
- Paths that hold machine-local or signing material: `local.properties`,
  `keystore.properties`, `signing.properties`, `.env*`, `*.jks`, `*.keystore`,
  `*.p12`, `*.pfx`, `*.pem`, `id_rsa`, `id_ed25519`

Only high-confidence patterns are included on purpose. A guard that fires on
ordinary code gets bypassed reflexively, and then it is not a guard at all.

## Overriding

```sh
SKIP_PUSH_GUARD=1 git push
```

For a finding you have looked at and judged to be a false positive. If a real
secret was committed, rewrite the history that introduced it instead — the
remote keeps what it receives.

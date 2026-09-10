---
name: release
description: Cut a Token-Sheriff release — bump .github/project.yml version, open and merge the release PR, wait for the automated Release workflow, then reformat the generated GitHub release notes
user-invocable: true
allowed-tools: Bash, Read, Edit, AskUserQuestion
---

# Release Skill

Cuts a new Token-Sheriff release end-to-end: determine the version, open the version-bump PR
that triggers the release, merge it, wait for the automated Release workflow, verify the
release landed, and reformat the auto-generated GitHub release notes.

The GitHub repository is **`cuioss/TokenSheriff`** (the local working-directory name is
`OAuthSheriff`, pre-rebrand — always pass `--repo cuioss/TokenSheriff` to `gh`).

## How the release is wired (read first)

The release is **fully automated by GitHub Actions**. `.github/workflows/release.yml`
triggers on a **merged pull request that changes `.github/project.yml`**:

```yaml
on:
  pull_request:
    types: [closed]
    paths:
      - '.github/project.yml'
```

So this skill never runs Maven release goals by hand. Its job is to produce and merge the
correct `project.yml` change; the reusable `cuioss-organization` release workflow
(`reusable-maven-release.yml`) does the tagging, Maven Central deploy, and GitHub release
creation.

Observed timings (use these as the basis for the waits below):
- PR gating checks: **Maven Build** is the primary gate; **Integration Tests** (multi-IDP:
  Keycloak + Dex + Zitadel) and **End-to-End Tests** (Playwright Dev-UI, `timeout-minutes: 30`)
  also run. End-to-end is the long pole, so a full green PR takes **~15–20 min**.
- Release workflow itself: **~6 min**, but Maven Central propagation, the GitHub Pages deploy
  (`pages.deploy-at-release: true`), and the GitHub release publish can lag → allow **up to
  ~30 min** before treating it as stuck.

## Workflow

### Step 1 — Determine the version number

`.github/project.yml` is the single source of truth for **every** version this skill
touches. Derive them; never read the project version from `pom.xml`, a git tag, a previous
GitHub release, or your own memory of the last cycle, and never hand-substitute a literal.

- `release.current-version` — the **last released** version.
- `release.next-version` — what `pom.xml` carries between releases.

**Default rule:** the release version is `next-version` with `-SNAPSHOT` stripped. The new
`next-version` is the next patch bump plus `-SNAPSHOT`.

Export the three values once and use these variables verbatim in every later step — branch
name, commit subject, PR title and body, tag check, notes filenames, and the changelog
compare range:

```bash
eval "$(python3 - <<'PY'
import pathlib, re
text = pathlib.Path('.github/project.yml').read_text()
def field(key):
    m = re.search(rf'^\s*{key}:\s*(\S+)\s*$', text, re.M)
    if not m:
        raise SystemExit(f'{key} not found in .github/project.yml')
    return m.group(1)
prev = field('current-version')
release = field('next-version').removesuffix('-SNAPSHOT')
major, minor, patch = release.split('.')
print(f'PREV_VERSION={prev}')
print(f'RELEASE_VERSION={release}')
print(f'NEXT_VERSION={major}.{minor}.{int(patch) + 1}-SNAPSHOT')
PY
)"
echo "$PREV_VERSION -> $RELEASE_VERSION (then $NEXT_VERSION)"
```

`PREV_VERSION` is captured **before** the edit in Step 5 — once `current-version` is
bumped, the previous release is no longer recoverable from the file, and both the
`**Full Changelog**` compare range and the "previously …" Quarkus line need it.

Then assert `pom.xml` agrees with `next-version`. project.yml stays the source of truth;
a mismatch means the tree is in an unexpected state, so stop and investigate rather than
releasing:

```bash
POM_VERSION=$(./mvnw -B -q help:evaluate -Dexpression=project.version -DforceStdout -N)
[ "$POM_VERSION" = "${RELEASE_VERSION}-SNAPSHOT" ] \
  && echo "OK: pom.xml agrees with project.yml" \
  || { echo "STOP - pom.xml=$POM_VERSION, expected ${RELEASE_VERSION}-SNAPSHOT"; false; }
```

**Ask the user** (AskUserQuestion) only if in doubt — e.g. the numbers don't follow the
expected pattern, a patch/major release is plausible, or `current-version` and `next-version`
are inconsistent. Otherwise state the determined version and proceed.

### Step 2 — Determine current status (clean to release?)

```bash
gh pr list --repo cuioss/TokenSheriff --state open --json number,title,isDraft
```
- **No open PRs** → good, proceed.
- **Open PRs exist** → these would normally be merged before a release. Surface the list and
  **ask the user** whether to proceed anyway or wait. Do not silently ignore them.

Also confirm the working tree is clean (`git status --porcelain`) before branching.

### Step 2b — Gate on smallrye/Quarkus alignment (do NOT skip)

```bash
python3 .claude/skills/release/check-quarkus-alignment.py --repo . --check-resolved
```

| exit | meaning |
|------|---------|
| 0 | aligned — proceed |
| 1 | **misaligned** — stop, fix, restart |
| 2 | **could not determine** — also a stop. An unresolvable check is never a pass. |

`version.quarkus` reaches this project in one of **two** shapes, and the script resolves
both — first by scanning reactor POMs, then, if none declares it, from the effective POM:

- **declared here** — the historical shape, and still what any consumer pinning its own
  Quarkus does.
- **inherited from the parent chain** — TokenSheriff since it adopted `cui-quarkus-parent`
  (PR #717). An *imported* BOM can never supply the value, because Maven does not propagate
  properties from imported BOMs and `quarkus-maven-plugin` needs it as a build extension —
  but a real `<parent>` does propagate it.

That fallback exists because the text scan alone reported the inherited case as "not declared
anywhere" and exited 2 — blocking every release at this step. A gate that *cannot run* is the
one that gets waved through, which is exactly the outage it exists to prevent. A genuine
*conflict* between reactor declarations still fails loudly: that is a real split, not a
missing value.

Quarkus' deployment classes are compiled against one specific smallrye-config release, so a
newer version — even an internally coherent one — fails augmentation with
`failed to access io.smallrye.config.ConfigMappingLoader$ConfigMappingImplementation`. That
shipped twice through `cuioss-parent-pom` and cost five weeks of red builds the first time.
The `requireSameVersions` enforcer guard cannot catch it: nothing is split, so it stays
correctly silent.

`--check-resolved` asserts every `io.smallrye.config` artifact actually resolves to what
**this** project's Quarkus was built against, catching a split family as well as a wrong one.

Keep the script in sync with `cuioss-parent-pom`'s copy; there is no shared parent to inherit
it from.

### Step 3 — Pull current main

```bash
git checkout main && git pull --ff-only origin main
```

### Step 4 — Create the release branch

Branch name uses the `chore/` prefix (required — the Maven CI workflow only triggers on
`main`, `feature/*`, `fix/*`, `chore/*`, `release/*`, `dependabot/**`; other prefixes skip the
`build` check and block auto-merge):

```bash
git checkout -b "chore/release_${RELEASE_VERSION}"
```

### Step 5 — Update `.github/project.yml`

Edit the `release` block:
- `current-version:` → `$RELEASE_VERSION`
- `next-version:` → `$NEXT_VERSION`

Leave everything else untouched — the diff must be exactly those two lines. Confirm with
`git diff .github/project.yml` before committing.

### Step 6 — Badges in README.md (normally no action)

The README badges are **dynamic shields.io endpoints** (Maven Central, Sonar, last-commit,
benchmark) that update automatically — there is **no hand-maintained version badge** to bump,
so normally leave `README.md` alone. Only touch it if a release note explicitly calls for a
README change.

### Step 7 — Commit, push, open PR

```bash
git add .github/project.yml
git commit -m "chore(release): prepare release ${RELEASE_VERSION}"
git push -u origin "chore/release_${RELEASE_VERSION}"
gh label create skip-bot-review --repo cuioss/TokenSheriff --description "Skip automated bot review" --color ededed 2>/dev/null || true
gh pr create --repo cuioss/TokenSheriff --base main \
  --title "chore(release): prepare release ${RELEASE_VERSION}" \
  --label "skip-bot-review" \
  --body "Bump current-version to ${RELEASE_VERSION}, next-version to ${NEXT_VERSION}. Triggers the automated Release workflow on merge."
```

The mechanical release PR carries the `skip-bot-review` label so automated bot review is
skipped, matching the other cuioss release skills (the preceding `gh label create` ensures the
label exists first).


### Step 8 — Wait for PR checks (~15–20 min)

Watch the checks rather than blindly sleeping; the end-to-end suite is the long pole:

```bash
gh pr checks <pr#> --repo cuioss/TokenSheriff --watch
```
If using a scheduled/loop wait, poll roughly every few minutes up to ~20 min.

### Step 9 — Handle PR comments / failures (if any)

- If a check fails, read the failing run's log (`gh run view <id> --log-failed`), fix the cause
  on the branch, push, and re-wait. **Never** merge a red PR.
- If reviewers (Gemini/CI) leave comments
  (`gh api repos/cuioss/TokenSheriff/pulls/<pr#>/comments`), address each: fix valid ones and
  reply, or reply explaining why not — every comment gets a reply and is resolved.
- Re-run Step 8 after any push.

### Step 10 — Merge → release starts automatically

Once checks are green and comments resolved:

```bash
gh pr merge <pr#> --repo cuioss/TokenSheriff --squash
```
`main` uses the org-managed merge queue (`main-merge-queue`), so the merge **enqueues** (it is
not immediate) and `--delete-branch` is rejected (the queue auto-deletes the branch on merge).
After `gh pr merge ... --squash`, poll
`gh pr view <pr#> --repo cuioss/TokenSheriff --json state --jq .state` until it reports `MERGED`
before expecting the Release workflow. The release workflow is unaffected because
`cuioss-release-bot` is a bypass actor on the queue.

Merging this PR (it touches `.github/project.yml`) fires `release.yml` automatically — do
**not** dispatch the release manually unless the auto-trigger demonstrably did not fire.

### Step 11 — Wait for the Release workflow (~30 min)

```bash
# Pick the newest in-progress Release run explicitly (a bare --limit 3 can surface
# older Release runs alongside the active one):
RUN_ID=$(gh run list --repo cuioss/TokenSheriff --workflow "Release" --limit 10 \
  --json databaseId,status --jq 'map(select(.status=="in_progress")) | first | .databaseId')
# Fallback if the run hasn't registered yet, or to target this release by the merged
# commit: gh run list ... --json databaseId,headSha --jq 'map(select(.headSha=="<sha>")) | first | .databaseId'
gh run watch "$RUN_ID" --repo cuioss/TokenSheriff
```
The workflow itself runs ~6 min; allow up to ~30 min for tag + Maven Central propagation +
Pages deploy + GitHub release publish before treating it as stuck.

### Step 12 — Verify the release landed

```bash
gh release view "$RELEASE_VERSION" --repo cuioss/TokenSheriff \
  --json tagName,name,createdAt,body
git fetch --tags && git tag --list "$RELEASE_VERSION"
```
Confirm the tag exists and a GitHub release for `$RELEASE_VERSION` was created. If it did not appear,
inspect the Release workflow run log before proceeding.

### Step 12b — Verify consumer propagation actually changed something

The Release workflow's `propagate-to-consumers` job opens a bump PR in each consumer repo.
It reports **success even when it changed nothing**, so a green job is not evidence that
consumers moved. Read the per-consumer `RESULT:` lines:

```bash
JOB=$(gh run view <run-id> --repo cuioss/TokenSheriff --json jobs \
  --jq '.jobs[] | select(.name|test("propagate")) | .databaseId')
gh run view --job "$JOB" --repo cuioss/TokenSheriff --log \
  | grep -E 'Propagating to|Skipping|RESULT:'
```

`"status": "no_changes"` is the one to look at. It means either "already correct" **or**
the updater declined, and the two are indistinguishable in the job summary. The decline
that matters:

```
Skipping SNAPSHOT property value: <version>-SNAPSHOT
```

A consumer developing against the unreleased snapshot pins `<version>-SNAPSHOT`, and the
updater refuses to overwrite a SNAPSHOT pin — a sound guard that nevertheless blinds it at
the exact moment the pin should move, because `X-SNAPSHOT → X` is the one bump it will
never make. That consumer keeps building against mutable snapshot bytes that Central will
eventually purge, and any temporary snapshot-repository block stays in its resolution path.

Such a consumer needs a **manual** PR: move the pin to the release version, drop any
snapshot-repository declaration added for it, and re-check version-convergence comments
that name the old Quarkus/platform numbers. Report it in Step 14 rather than treating the
green job as done.

### Step 13 — Reformat the generated release notes

The Release workflow creates the GitHub release with **auto-generated** notes (a flat
`## What's Changed` list). Rewrite them in place using the **house format below**, then push
the update:

```bash
mkdir -p .plan/temp
gh release view "$RELEASE_VERSION" --repo cuioss/TokenSheriff --json body --jq .body \
  > ".plan/temp/release-${RELEASE_VERSION}-orig.md"
# ...build the reformatted body in .plan/temp/release-${RELEASE_VERSION}.md...
gh release edit "$RELEASE_VERSION" --repo cuioss/TokenSheriff \
  --notes-file ".plan/temp/release-${RELEASE_VERSION}.md"
```

After building the reformatted file, **cross-check coverage** before editing the release:
extract the `pull/<n>` numbers from both files and confirm every original PR is either kept,
collapsed into a chain, or intentionally dropped (only the mechanical version-bump PR and
OpenRewrite bumps should be dropped outright), and that no PR appears in the new file that was
not in the original.

#### House format rules (apply exactly)

1. **Three top-level groups, in this order:** `## Quarkus`,
   `## Features & Enhancements`, and `## Dependency Updates`.
2. **Quarkus is the headline** — the Quarkus platform version is the single most
   important fact in a release, so it gets its **own top-level section at the very top**,
   never a bullet buried under dependency updates. Open it with a one-line statement of the
   target version, then the PR line(s):

   ```
   ## Quarkus

   This release targets **Quarkus <new>** (previously <old>).

   * <PR line(s)>
   ```

   If Quarkus did **not** change in this cycle, state the unchanged version in the same
   one-line form and omit the PR line. Never also list Quarkus under `### Java`.
3. **Features & Enhancements** — group functional PRs by theme with `###` subheadings, e.g.:
   - `### API & Code Quality` — also the home for refactor/standards/cleanup recipes
     (e.g. `refactor-to-profile-standards`, requirement-ID renames), **not** under build/tooling
   - `### Security`
   - `### Testing & Standards`
   - `### Documentation`
   - `### Build & CI` — manually-authored CI/build improvements (e.g. adopting a new
     cuioss-organization workflow behaviour); **not** mechanical dependency bumps
   Add release-specific themes when the cycle has a dominant thread (e.g. a
   `### Rebrand: OAuth-Sheriff → Token-Sheriff` group). Adapt headings to the actual PRs;
   omit empty sections.
4. **Dependency Updates** — group by type with `###` subheadings:
   - `### Java` — Java libraries (e.g. gson, commons-io, microprofile-jwt-auth-api).
     **Not** Quarkus — that has its own top section (rule 2).
   - `### JavaScript` — npm deps under
     `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment` and
     `token-sheriff-quarkus-parent/e-2-e-playwright` (eslint, prettier, stylelint, jest, babel,
     lit, …). Note older PR titles may still carry the pre-rebrand `oauth-sheriff-quarkus-*`
     path — keep the path verbatim from each PR title.
   - `### Infra` — platform/build/CI: build plugins (e.g. frontend-maven-plugin), GitHub Action
     bumps (claude-code-action, harden-runner, actions/*), cui-java-parent, and
     cuioss-organization workflow bumps
5. **Collapse by library identity — one line per library, spanning the full range.**
   The unit of collapsing is the *library*, not the PR title. Merge into a single line
   whenever the PRs concern the same library, in all three shapes that occur:
   - **Version chains** — several bumps of one artifact (`A → B → C`) collapse to one line
     spanning `A → C`, carrying the latest PR's author.
   - **The same library in several places** — one library bumped in more than one module or
     directory is **one** line naming them all, not one line each. Those titles differ only
     by that suffix, so do not wait for identical titles before merging.
   - **One upstream release landing as several coordinates** — when a single upstream bump
     arrives as separate PRs against different coordinates (e.g. a version property *and*
     a BOM or parent), that is **one** bump: one line naming the coordinates in parentheses.

   Carry every merged PR's URL onto the surviving line, comma-separated.
6. **Recover versions the title omits.** Dependabot truncates a title to
   `bump <lib> in /<dir>`, with no versions, when several dependencies must move together.
   Never publish a dependency line without a version range: read the PR body, which states
   ``Updates `<lib>` from X to Y``, and use those versions when computing the range:

   ```bash
   gh pr view <n> --repo cuioss/TokenSheriff --json body --jq .body | head -6
   ```
7. **Remove all OpenRewrite bumps and friends** — drop every `rewrite-maven-plugin`,
   `rewrite-migrate-java`, `rewrite-testing-frameworks`, and related OpenRewrite dependency PR.
8. **Remove internal tooling churn** — drop PRs that only touch dev/build orchestration with no
   user-facing effect: `marshal.json`/plan-marshall config migrations, plan-marshall build
   wiring, internal dev-skill changes, and **the mechanical version-bump PR itself**
   (`chore(release): prepare release <version>` / `release: cut … <version>`).
9. **Preserve each kept PR line** in its original
   `* <title> by @author in <url>` shape. Rules 5 and 6 **override** verbatimness where
   they conflict: rewrite the title's version range to span the collapsed chain, and name
   the several modules or coordinates on the surviving line.
10. Keep the trailing `**Full Changelog**: ...compare/$PREV_VERSION...$RELEASE_VERSION`
    line, built from the Step 1 values rather than copied from the generated notes.

#### Verify before publishing (mandatory)

These rules are easy to under-apply: a duplicate survives whenever two PRs touch the same
library under differing titles. After building the notes file and **before**
`gh release edit`, assert that every library appears exactly once:

```bash
grep -oE '(bump|update) [^ ]+ (from|in)' ".plan/temp/release-${RELEASE_VERSION}.md" \
  | sort | uniq -c | sort -rn | head
```

Every count must be `1`. Any count `>1` is an unmerged duplicate — collapse it per rule
5 and re-run. Also confirm that no dependency line is missing a version range
(rule 6).


### Step 14 — Done

Report: released version, release URL, the PR number, a short summary of how many
dependency PRs were collapsed/removed during note reformatting, and the outcome of Step 12b
— naming any consumer that reported `no_changes` because it pins a SNAPSHOT and therefore
still needs a manual bump.

## Critical rules

- The release is triggered by **merging a `.github/project.yml` change** — never hand-run
  Maven release goals.
- **Every project version is derived from `.github/project.yml`** — via the Step 1 export,
  never from `pom.xml`, a git tag, a prior release, or a literal typed into a command.
  `pom.xml` is checked *against* it, never read *as* it.
- Always pass `--repo cuioss/TokenSheriff` to `gh` (the local checkout is named `OAuthSheriff`).
- Branch prefix **must** be `chore/` (or another CI-accepted prefix) or the build check skips
  and auto-merge is blocked.
- Never merge a red PR; fix and re-wait.
- Temporary files go under `.plan/temp/`.

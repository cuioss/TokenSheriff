# Claude Code Configuration

All AI development guidelines for this project live in **`AGENTS.md`**.
Refer to that file for dev-environment tips, build commands, testing instructions, code style,
and framework-specific standards when working on Token-Sheriff.

## Temporary Files

- Use `.plan/temp/` for ALL temporary files (covered by the `Write(.plan/**)` permission — avoids permission prompts).

## Git Workflow

Branch protection on `main` forbids direct pushes. Always:

1. Create a feature branch: `git checkout -b <branch-name>`
2. Commit: `git add <files> && git commit -m "<message>"`
3. Push: `git push -u origin <branch-name>`
4. Open a PR against `main` via `gh pr create --repo cuioss/TokenSheriff`
5. Wait for CI + AI reviewer(s): `gh pr checks --watch`
6. Address every AI-reviewer comment — reply with the reason for fixing or not fixing, and resolve it. If uncertain, ask the user first.
7. Merge the PR yourself, through the merge queue, once CI is green and every AI-reviewer comment is addressed and
   resolved; no separate approval is needed. Never bypass branch protection or a required check, and never merge with
   a failing check or an unresolved reviewer comment. If a merge is blocked, report why instead of working around it.
8. After merge, return to `main`: `git checkout main && git pull`

## Tool Usage

- Use proper tools (Edit, Read, Write) instead of shell commands (echo, cat)
- Never use Bash for file operations (find, grep, cat, ls) — use Glob, Read, Grep tools instead

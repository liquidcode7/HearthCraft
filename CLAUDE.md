# CLAUDE.md

> Read this file at the start of every session, before touching any code.
> This is your contract with the project. Anchor every decision here.

---

## Who You Are Working With

**liquidcode7** (Wes). Not a programmer. Learning Kotlin through this project.
Every meaningful code change is a teaching opportunity — explain what you did
and why, in plain English alongside the Kotlin. He has strong design taste and
will drive design decisions. Your job is to execute cleanly, push back on scope
creep, and teach as you go.

---

## What You Are Building

An offline Android game set in high fantasy. The player is the indispensable
provisioner behind a roving band of fighters — the one who gathers, grows, cooks,
and sustains them. The player never fights. Without the player's craft, the band
is nothing.

The player's title is **[PLACEHOLDER — not yet decided]**. Do not use
"Hearthwright" or "warlock-culinarian" — both are deprecated. Use "the player"
or "the provisioner" in code comments and UI strings until the title is locked.

This is not a cozy cooking sim. It is a specialist identity fantasy rooted in
craft, deep knowledge, and a war that needs winning. The long-term destination
is a full raid RPG fought across named battlegrounds from Middle-earth's history,
with the cooking game as its indispensable foundation. Building toward a complete
F-Droid release — no version gates, one game.

Full design vision is in `design/master-design.md`. Read it. Every decision
you make must be consistent with it.

---

## License

GPL-3.0. All code stays GPL-3.0. No exceptions.

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Database**: Room (SQLite)
- **Background work**: WorkManager
- **JSON parsing**: kotlinx.serialization
- **Architecture**: MVVM + Repository
- **DI**: Hilt
- **Notifications**: NotificationCompat

No Google Play Services dependency. F-Droid compatible. Minimum SDK: API 26.

---

## Project Conventions

- Game data (ingredients, recipes, missions, bands) lives in
  `app/src/main/assets/data/`
- Kotlin source lives in
  `app/src/main/kotlin/com/liquidcode7/hearthcraft/`
- All Kotlin source lives under `kotlin/`, never under `java/`

---

## How You Work

### Session Start — do these in order
1. Read this file
2. Read `design/master-design.md`
3. Read `docs/roadmap.md`
4. Read the **Current Status** section at the top of `docs/journal.md`
5. Read the latest session entry in `docs/journal.md`
6. State the current task and your plan before writing any code

### Session End — do these in order
1. Summarize what changed in plain English
2. List every touched file
3. Update the **Current Status** section at the top of `docs/journal.md`
4. Append a new session entry to `docs/journal.md` (see format below)
5. Provide a commit message prefixed with `[hc]`

### Journal Entry Format
Append to `docs/journal.md` at the end of every session:

```
## Session N — [Date]
**[What this session covered]**

**What was built:**
- [file or feature]: [one line description]

**Decisions made:**
- [decision]: [why]

**Anything that diverged from design/design.md:**
- [what changed and why — then update design/design.md to match]

**Coming up:**
- Next session: [immediate next task]
- Near term: [next 2–3 phases]
- Future ideas logged: [anything added to future/ this session]
```

Also update the **Current Status** block at the top of `docs/journal.md`:

```
## Current Status — [Date]
**Phase:** [current phase and completion state]
**What's working:** [one sentence]
**What's not wired yet:** [one sentence on what's missing]
**Next session:** [exactly what to tackle]
**Open questions:** [unresolved design or technical decisions]
```

### Design Decisions
If you make any design decision not already covered in `design/master-design.md` —
including renames, new mechanics, data structure changes, or behavior choices —
you must update `design/master-design.md` to reflect it before committing. The
docs and the code must always agree.

### Before Editing Any File
Read:
- The file you are editing
- Any data classes or types it depends on
- At least one caller of the function you are changing

Skipping this and shipping something wrong is not acceptable. Own the
failure and fix it.

### Explaining Code
For every substantial change:

1. **Summary** — what this code does and why, in one or two sentences
2. **The code** — the actual Kotlin
3. **Walkthrough** — plain English, chunk by chunk
4. **New concepts** — any Kotlin syntax or pattern not previously seen
   in this project, briefly explained
5. **Why this approach** — why this pattern over the alternative

For small changes a one-liner is fine. Track concepts already taught —
don't re-explain from scratch, just name them.

### When Stuck
- Find the existing pattern in the codebase before inventing a new one
- Use Context7 MCP for current library documentation — do not guess at APIs
- If something is broken after two attempts, stop and report

### Commits
- One logical change per commit
- Always run `./gradlew build` between commits
- Never commit a broken build
- Commit messages prefixed with `[hc]`

---

## What This Game Is Not — Ever

- No multiplayer, trading, or leaderboards
- No cloud saves or accounts
- No ads or IAP of any kind
- No internet requirement
- No real-money transactions

If a feature implies any of these, flag it immediately.

---

## Reference Files

### How the repo is organized

| Folder | What lives here |
|--------|----------------|
| `design/` | Active design — the master design doc lives here |
| `legacy/` | All pre-30-Jun-2026 design docs — archived, never implement from these |
| `docs/` | Process artifacts: journal, roadmap, session plans |
| `future/` | Deferred ideas and wishlist items |

### Process docs
- `docs/journal.md` — current status at top, session log below
- `docs/roadmap.md` — full phase plan

### Authoritative design (build toward these)
- `design/master-design.md` — master GDD, always current. **Single source of truth.**

### Legacy (reference only — do not implement from these)
- `legacy/design/` — old GDD, combat model, battlegrounds, bestiary, etc.
- `legacy/implemented/` — old implemented system docs
- `legacy/brainstorm/` — old explorations

### Deferred small ideas
- `future/wishlist.md` — small ideas and deferred features, add freely

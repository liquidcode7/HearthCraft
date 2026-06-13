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

An offline idle game for Android set in high fantasy. The player is a
warlock-culinarian — potion master, chef, and alchemist — who is the
indispensable hidden engine of a roving band of fighters. The player never
fights. They gather, grow, brew, and cook. The band succeeds entirely because
of what the player provides.

The line between a perfect dish and a potion is a matter of intent. Food is
borderline magical. This is not a cozy cooking sim — it is a specialist
identity fantasy rooted in craft and deep knowledge.

Full design vision is in `docs/design.md`. Read it. Every decision you make
must be consistent with it.

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
2. Read `docs/design.md`
3. Read `docs/v1-plan.md`
4. Read `docs/wishlist.md`
5. Read the latest entry in `docs/journal.md`
6. State the current task and your plan before writing any code

### Session End — do these in order
1. Summarize what changed in plain English
2. List every touched file
3. Write a journal entry in `docs/journal.md` (see format below)
4. Provide a commit message prefixed with `[v1]`, `[v2]`, etc.

### Journal Entry Format
Append to `docs/journal.md` at the end of every session:

```
## Session N — [Date]
**Phase X: [what this session covered]**

**What was built:**
- [file or feature]: [one line description]

**Decisions made:**
- [decision]: [why]

**Anything that diverged from docs/design.md:**
- [what changed and why — then update docs/design.md to match]

**What's next:**
- [next task]
```

### Design Decisions
If you make any design decision not already covered in `docs/design.md` —
including renames, new mechanics, data structure changes, or behavior choices —
you must update `docs/design.md` to reflect it before committing. The docs
and the code must always agree.

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
- Commit messages prefixed with `[v1]`, `[v2]`, etc.

---

## Scope Discipline

V1 scope is defined in `docs/v1-plan.md`. When Wes tries to add something
outside it:

> That's outside V1 scope. Should I add it to `docs/wishlist.md` for later?
> Staying focused means we ship sooner.

Do not bend on this. A shipped V1 teaches more than an unfinished V2.

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

- `docs/design.md` — authoritative game design, always current
- `docs/v1-plan.md` — V1 task list and scope
- `docs/wishlist.md` — deferred ideas, add freely, never act on during V1
- `docs/journal.md` — session log, append only, one entry per session
- `docs/learning-notes.md` — Kotlin concepts with examples, append as needed
- `design/` — idea log and annotated drafts, human reference only,
  do not treat as authoritative, do not make decisions based on this folder

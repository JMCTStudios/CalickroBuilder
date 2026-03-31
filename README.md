# CalickroBuilder

An intelligent NPC building system for Minecraft that is being developed phase-by-phase, starting with safe structure placement and growing toward fully conversational AI-driven village building.

---

# 🚧 CURRENT PHASE
## Phase 4.3.1.3 — Speed System + Builder Persistence

### ✅ Working
- Builder NPC binding
- Builder stays a builder after restart
- Walk-before-build behavior
- Adaptive plot search
- WorldGuard-aware placement baseline
- Scan diagnostics for valid plot search
- Admin speed command
- Admin speed mode command
- Completion time chat message after builds finish

### ⚠️ Still Needs Improvement
- Better road alignment and village-style layout
- Stronger spacing between repeated houses
- Role-aware building selection for NPC jobs
- Smarter self-correction / block-fix logic
- Better NPC final positioning in completed buildings

---

# 🧭 PHASE ROADMAP

## Phase 1 — Foundation
- Plugin setup
- Command framework
- Builder registry
- Provider structure

## Phase 2 — First Build System
- First successful block placement
- Basic build execution

## Phase 2.5 — Movement Introduction
- NPC walks before building

## Phase 2.6 — Movement Stability
- Improved walk/build timing

## Phase 2.7 — Safer Placement
- Avoid obviously invalid build spots

## Phase 2.8 — WorldGuard Awareness
- Protected-area awareness baseline
- Better spawn-safe placement

## Phase 2.9 — Smart Orientation (WIP checkpoint)
- Orientation experiments
- Not stable enough to mark final

## Phase 3 — Adaptive Search
- Retry nearby plots instead of failing immediately
- Better success rate in open areas

## Phase 4.3.1 — Smart Placement Baseline
- Smarter lot selection
- Scan diagnostics baseline

## Phase 4.3.1.1 — Placement Tuning + Scan
- Placement tuning
- More useful scan feedback

## Phase 4.3.1.2 — Placement Fix + Deep Debug
- Grounded scan origin
- Better candidate reporting

## Phase 4.3.1.3 — Speed System + Persistence (CURRENT)
- Speed modes
- Builder persistence
- Completion time feedback

---

# 🔮 UPCOMING PHASES

## Phase 5 — Village Layout Intelligence
- Stronger road alignment
- Better spacing between houses
- Better lot scoring
- Cleaner spawn-village behavior

## Phase 6 — NPC Integration
- Move NPCs out of the way during construction
- Put NPCs back into correct buildings
- Use CalickroNPCBridge-linked roles/labels

## Phase 7 — Role-Based Structures
- Guide house
- Auction house
- Shop
- Warp station
- Crates / service buildings

## Phase 8 — Safety & Recovery
- Snapshot surrounding blocks
- Restore accidental damage
- Better overlap prevention
- Undo / rebuild improvements

## Phase 9 — Builder Visuals
- Builder tools / equipment
- Builder skins
- Better stance / animation behavior

## Phase 10 — Conversational AI
- Trigger phrases such as `~Hey Moses`
- Natural requests like “build a tower here”
- Contextual follow-up dialogue

---

# 🧪 COMMANDS

```bash
/cali builder bind
/cali builder status
/cali builder scan
/cali builder testhouse
/cali builder speed <ticks>
/cali builder speedmode <smart|fixed|cinematic|fast|custom>
/cali builder reload
```

---

# ⚙️ CONFIG HIGHLIGHTS

The plugin supports configurable build-speed modes:
- `smart` — recommended default
- `fixed` — one consistent speed
- `cinematic` — slower and more immersive
- `fast` — useful for testing, may lag large builds
- `custom` — server-owner tuned values

Builders are persisted in:
- `plugins/CalickroBuilder/builders.yml`

---

# 🔥 PROJECT STATUS

Current state:
- Functional builder NPC
- Adaptive plot search
- Dynamic speed modes
- Persistent builder identities

Next milestone:
- Village-style lot planning and stronger road-based layout logic


## Phase 4.3.1
- Fixed builder NPC spawn resolution after persistence
- Reduced scan/build lag from over-aggressive candidate checks
- Kept speed modes and persistence, but made them safer

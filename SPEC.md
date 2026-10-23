# Create: Factory Routing — Technical Specification

## 1. Purpose

Create 6.0.10 gives a recipe-mode Factory Panel one `recipeAddress` shared by every ingredient.
Create: Factory Routing adds an optional address override to each ingredient connection.

For each ingredient link, the effective address is resolved in this order:

1. Use the ingredient override when it exists and is not blank.
2. Otherwise use the recipe panel's address.
3. If neither value is usable, abort the complete request cycle.

Removing this addon must leave ordinary Factory Panel data usable. Routing information is therefore
additive and never replaces Create's own address field.

## 2. Supported environment

| Component | Supported version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.227 or newer |
| Create | 6.0.10 — required and tested |
| Create: Factory Controller | Optional, 1.2.1 to 1.2.3 — built against 1.2.3 |
| Java | 21 |

The compile target is `com.simibubi.create:create-1.21.1:6.0.10-281` and
`curse.maven:create-factory-controller-1573767:9224923` (1.2.3). The published metadata keeps both
dependencies on narrow ranges rather than accepting newer ones: five Mixin classes with required
injection points when their targets are present, plus the adapted dispatch logic, depend on
specific method signatures, so a newer Create or Factory Controller has to be checked before a
range is widened.

Factory Controller is declared as `[1.2.1-neoforge-1.21.1,1.2.3-neoforge-1.21.1]` — the version
strings it actually reports, which the loader orders above the plain numbers. The range is closed
over exactly the three builds whose published jars were compared member by member: 1.2.1, 1.2.2 and
1.2.3 declare every field and method this addon shadows, injects into or calls with identical
signatures, and `tickRequests` contains exactly one `LogisticsManager.findPackagersForRequest` call
in each. 1.2.0 and older are excluded because they still use the pre-`component.gauge` package
layout and the removed `targetedBy()` map.

Excluding them is a hard stop rather than a silent skip, which is why the bounds are worth getting
right. A development server started against Factory Controller 1.1.1 and again against 1.2.0
refused to load both times:

```
net.neoforged.fml.ModLoadingException: Loading errors encountered:
  - Mod create_factory_routing only supports createfactorycontroller between
    1.2.1-neoforge-1.21.1 and 1.2.3-neoforge-1.21.1 (inclusive)
    Currently, createfactorycontroller is 1.1.1-neoforge-1.21.1
```

An `optional` dependency means the mod may be absent, not that any version of it will do. Players
who need Factory Controller 1.1.1 stay on Create: Factory Routing 0.1.0, which declares
`[1.1.1-neoforge-1.21.1]`.

## 3. Scope

Version 0.1 provides:

- one optional address override per ingredient connection;
- persistence and server-to-client synchronization;
- multi-address dispatch that is planned as a whole and abandoned entirely if any part of it cannot
  be covered;
- an in-game overview and address editor;
- a command-based diagnostic fallback;
- optional Create: Factory Controller integration.

Tags, NBT matching, priorities, round-robin selection, fallback chains, custom batch sizes, stock
conditions, and delivery-time synchronization are outside the current scope.

## 4. Create data model

**Terminology.** This document uses *Factory Panel* for Create's internal `FactoryPanel*` classes,
because that is what they are called in code. The block players see in game is the **Factory Gauge**;
player-facing texts such as the README and the mod description use that name throughout.

The implementation relies on Create's factory-board types:

```java
public record FactoryPanelPosition(BlockPos pos, PanelSlot slot) { }

public class FactoryPanelConnection {
    public FactoryPanelPosition from;
    public int amount;
    public List<Direction> path;
    public int arrowBendMode;
    public boolean success;
    public WeakReference<Object> cachedSource;
}
```

`FactoryPanelConnection` does not store the requested item. The item is read from the source panel
identified by `connection.from`. `FactoryPanelPosition` is therefore also the stable key for an
ingredient route override.

## 5. Persistence

### 5.1 Regular Factory Panels

`BoardRouting` stores `PanelRouting` data by panel slot. Each `PanelRouting` maps source
`FactoryPanelPosition` values to `IngredientRoute` records. The data is attached to the
`FactoryPanelBlockEntity` with a NeoForge data attachment.

### 5.2 Factory Controller virtual gauges

Virtual gauge routing is stored separately through `VirtualPanelRouting`. Optional compatibility
code only loads when Create: Factory Controller is present.

### 5.3 Cleanup

Stale entries that no longer correspond to an active ingredient link are removed during normal
panel processing. Empty routes do not need to be stored.

## 6. Dispatch contract

Create already performs a two-phase request cycle: validate availability, plan requests, verify
that packagers are not busy, and only then execute all plans. The addon preserves that behavior.

The routed cycle is:

1. Aggregate required items by logistics network and item, matching Create's availability model.
2. Abort the complete cycle if any aggregated requirement is unavailable.
3. Resolve the effective address of every ingredient link.
4. Partition requests into `(network, address)` buckets.
5. Plan every bucket while accounting for stock already assigned to earlier buckets.
6. Abort all buckets if any selected packager is too busy.
7. Execute every plan.
8. Add exactly one output promise, matching Create's original recipe behavior.

Availability must remain aggregated by `(network, item)`, not by address. Checking address buckets
independently could allow two buckets to approve the same physical stock.

### 6.1 Single-pass planning

`LogisticsManager.findPackagersForRequest` plans exactly one address per call and reserves nothing:
it reads each packager's cached item summary and returns a plan. Calling it once per address would
let two addresses select the same physical stock — both pick the first packager reporting enough,
one address is under-delivered, and stock sitting on a second packager is never touched. Since the
panel records its single output promise in the same cycle, and promises do not expire under the
default panel settings, that panel then stops requesting altogether.

The addon therefore plans an entire network in one walk over its links, in two phases:

1. **Allocation.** Every link is visited once. Each packager's available items are read once and
   handed out to the outstanding demands of all addresses, subtracting from a local tally as it
   goes. No packager can be counted twice, and Create's cached summaries are never modified.
2. **Request construction.** Create's own loop shape is replayed per address — items outer, links
   inner — using the amounts decided in phase one. `orderId`, the order context carried by the first
   package, `linkIndex` and the shared `finalLink` flag are produced in exactly the sequence Create
   produces them, so package grouping, defragmentation and order completion behave unchanged.

Each address is an independent order with its own `orderId`, context, `finalLink` tracker and its
own request multimap. `PackagerBlockEntity.attemptToSend` packs consecutive requests only while
address and `orderId` match, and ends the current package on the first mismatch. Two orders sharing
a packager key in an unordered multimap could therefore interleave, restarting the package counter
in the middle of an order and moving its context into a later package. Create never places more
than one order into a multimap.

The regular panel path keeps the address plans separate for the same reason and executes them
individually, after one shared busy check across all of them. The Factory Controller redirect must
return a single multimap, so it is the deliberate exception: the finished address plans are
concatenated block by block into an `ArrayListMultimap`, whose per-key insertion order keeps every
order contiguous. Ordering inside one address plan does not matter, since it holds a single address
and `orderId`.

One deviation remains in that merged form: `performPackageRequests` retries a packager at most 100
times per call, and that budget is then shared across all addresses of the same packager. It is
reachable with very large or very many multi-address orders on one packager.

If any requested amount cannot be covered, planning reports an incomplete result and the cycle is
abandoned: nothing is sent and no output promise is created. A partial delivery is never acceptable,
because it strands the machine while marking the craft as already in flight.

Planning stays side-effect free. Nothing is withdrawn until `performPackageRequests` runs. The
inventory type behind a packager is irrelevant to correctness.

### 6.2 Passthrough behavior

If a regular panel has no overrides, the Mixin does not cancel Create's original method. This keeps
the common case on Create's native code path. The custom dispatch path is used only when routing
data requires it.

## 7. Networking and validation

Routing changes are server-authoritative. Configuration payloads are rejected unless all of the
following hold:

- the target block entity and panel slot exist;
- the player is not a spectator and not in adventure mode;
- the target position is loaded and within interaction distance;
- the player may interact with the logistics network of the target panel and of the source panel;
- the referenced ingredient connection exists on that panel slot.

The address itself is normalized rather than rejected. A blank address is a valid payload and means
*remove the override*, so the ingredient falls back to the gauge's own address. An address longer
than Create's 25-character limit is truncated to that length when the payload is decoded, because
the normalization happens in the `IngredientRoute` constructor.

The `/cfr` command path is stricter: it refuses a blank address, pointing at `route clear` instead,
and refuses an over-long one rather than silently shortening it.

Accepted changes are persisted and synchronized back to clients. The client may update its local
view optimistically, but the server remains authoritative.

## 8. User interface

The routing icon is available from recipe-mode Factory Panel settings. When the screen has enough
space beside the Recipe Settings panel, it toggles an embedded ingredient list on the left and the
selected ingredient's address editor on the right. Both panels shrink independently with the
available side space and GUI scale. Small per-ingredient shortcuts select the matching entry
directly. The client remembers whether the embedded panels were visible when the recipe screen is
reopened during the same game session. Resolutions that cannot fit even the narrow side layout retain the compact paged overview
and editor as a fallback.

Visual states:

- gold: the ingredient inherits the panel address;
- teal: a custom ingredient address is active;
- green routing icon: at least one override is configured.

The editor provides **Save**, **Cancel**, and **Use default** actions. It uses a normal text field to
avoid Create's large suggestion overlay obscuring the surrounding screen.

Create: Factory Controller virtual gauges expose the same embedded interface and compact fallback
through optional client Mixins. Per-ingredient routing details remain in the embedded panels; the
small routing shortcuts do not draw an additional tooltip over Create's native ingredient tooltip.

## 9. Compatibility strategy

Create is a required dependency. Create: Factory Controller is an optional dependency.

Factory Controller compatibility uses pseudo-Mixins and string-based targets so its classes are not
resolved when the mod is absent. The same built JAR must start and function in both configurations.

The Mixin injection point for Create 6.0.10 was checked against the published bytecode. The copied
dispatch logic must be reviewed whenever support moves to a newer Create implementation that changes
Factory Panel request processing.

The same applies to Factory Controller. Its 1.2 line reworked the gauge API the compatibility layer
builds on:

- `content.component.VirtualGaugeBehaviour` and `content.component.RecipeSlot` moved to
  `content.component.gauge`;
- `targetedBy()` and `targeting()` were removed in favour of `incomingConnections()` and
  `incomingConnection(position, type)`;
- `remapRecipeSlots(Function)` became `onComponentsRelocated(UnaryOperator)`;
- `ConnectionKey` gained a `type` component, so one source position can now reach the same gauge
  over several wires at once. Anything that used to treat "targets this gauge" as a single
  connection per position — route pruning in particular — has to filter by
  `LogisticsConnection.TYPE` instead;
- `LogisticsConnection.excludeFromRequestMultiplier` holds a link's amount out of the request
  multiplier, so an ordered count is `fixed + scalable × scaler` and no longer proportional to the
  raw link amounts. `VirtualRoutedDispatch.requestScaler` recovers the multiplier so the per-address
  split follows what was ordered; see *Known gaps* in §11 for the one case it cannot solve.

`VirtualGaugeBehaviour.buildOrderedSlots` still emits one entry per `recipeSlots` index with
trailing gaps trimmed and the arrangement marker appended last, so the positional assumption
`VirtualRoutedDispatch.splitCustom` makes continues to hold.

## 10. Diagnostic commands

`/cfr route list` prints the active ingredient connections and their source positions. `/cfr route
set` and `/cfr route clear` provide controlled configuration without using the client UI.

Commands require permission level 2, and that is the only player-side gate. They check the same
structural facts as the network path — the position must be loaded, the block entity must be a
factory panel, the slot must be active and the referenced ingredient connection must exist — but
they deliberately skip interaction distance, the spectator and adventure tests, and the logistics
network permission check. They are also stricter about the address itself: a blank or over-long
address is refused instead of normalized.

## 11. Validation checklist

### Build and loading

- Build on Java 21.
- Start with Create 6.0.10 and without Factory Controller.
- Start with Create 6.0.10 and Factory Controller 1.2.3.
- Confirm all required and optional Mixins apply in an audited development run.
- Confirm an out-of-range Factory Controller aborts loading with the range error rather than
  starting without the integration — see §2. Checked against 1.1.1 and 1.2.0.

### Regular panels

- A panel without overrides behaves exactly like native Create.
- One overridden ingredient is delivered to its custom address.
- Several ingredients can be sent to different addresses in one cycle.
- Removing an override restores inheritance from the panel address.
- Routing survives world reload and chunk unload.

### All-or-nothing planning and stock accounting

- If one ingredient is unavailable, no bucket is dispatched.
- 64 of one item to address A and 64 to address B, served from two ordinary chests holding 64 each:
  both addresses receive 64. The inventory type must not affect the outcome.
- The same split served from a single inventory holding 128.
- The same split across a mixed pair of an ordinary chest and an Item Vault.
- Insufficient total stock: nothing is sent and no output promise is created.
- A busy packager aborts the complete cycle.
- One recipe cycle creates exactly one output promise.
- Two items of the same address drawn from one packager, plus a second address on that packager,
  with amounts large enough to need several packages: package indices stay continuous per order,
  the order context rides on the first package of its own order, and the final-link flag closes
  each order exactly once.
- Every case above on a regular Factory Panel and through a Factory Controller virtual gauge.

### Known gaps

Two checklist items above have not been reproduced in game. Both paths are implemented and were
reviewed line by line against Create's own code, but neither has been observed at runtime, so treat
them as untested rather than working.

**Same item routed to two addresses.** The allocation phase exists precisely for this case. It
requires two source panels carrying an identical filter, wired to one consumer panel and routed to
different addresses. Every setup tested so far used distinct items per address, so the buckets were
disjoint per item and never competed for the same stock. The single pass reads each packager's
summary once, which is what should prevent double assignment, but the contention path itself has
never run.

**One order spanning more than one package.** A package holds `PackageItem.SLOTS` = 9 slots, and
`FactoryPanelScreen` clamps each ingredient amount to 1–64 across at most 9 ingredient links, so an
order of ordinary 64-stacking items stays within a single package. A second package needs items with
a smaller maximum stack size — ten lava buckets occupy ten slots — or a Factory Controller recipe,
whose amounts are not bound by that clamp. Large controller recipes are therefore not an exotic
case. Until this is run, `packageCounter` continuity across packages of one order, the placement of
the order context, and the final-link flag remain unobserved.

**Factory Controller 1.2.** The compatibility layer was migrated to the 1.2 gauge API by comparing
the published 1.1.1, 1.2.1, 1.2.2 and 1.2.3 jars and reading Factory Controller's own 1.2.3 source
for `tickRequests`. A first run with 1.2.3 installed works, but the checklist above has not been
repeated on the 1.2 line, and only 1.2.3 itself was run — 1.2.1 and 1.2.2 rest on the signature
comparison in §2.

**Excluded links served by ignore-data sources.** `LogisticsConnection.excludeFromRequestMultiplier`
keeps a link's amount out of the request multiplier, so `materializeDemand` ships
`fixed + scalable × scaler` per item rather than a uniform multiple of the link amounts.
`VirtualRoutedDispatch.requestScaler` recovers that multiplier from the ordered count —
`scaler = (count - Σfixed) / Σscalable` — and `splitMerged` weights each link by what it actually
contributed, so the mixed case now splits correctly.

The reconstruction is refused, and the raw link amounts are used instead, when a contributing source
has `ignoreData` set: such a source spreads one link's demand across several stock variants, so the
ordered stacks no longer map one-to-one onto links and the equation has no unique solution. An
ignore-data source *and* an excluded link feeding the same item to two addresses therefore still
splits by raw amounts, which over-serves the excluded link's address. The total is conserved in
every case. CUSTOM mode is unaffected throughout, because it keeps one order entry per recipe slot.

The cases that have been exercised are unaffected on the regular Factory Panel path: overrides and
inheritance, two addresses in one cycle producing one correctly addressed package each, both
trigger paths, and sustained operation.

### Factory Controller

- Virtual gauge overrides can be created, edited, cleared, and persisted.
- Virtual routed dispatch uses the configured addresses.
- Installing or removing Factory Controller does not require a different addon JAR.

### User interface

- The overview is readable at supported GUI scales.
- Mouse wheel and page controls reach every ingredient.
- Address input accepts keyboard editing without an obstructive suggestion panel.
- All buttons work in both regular and Factory Controller screens.
- The main routing icon toggles adaptively sized embedded panels when side space is available.
- Compact screens remain reachable when the side panels do not fit.
- A regular gauge ingredient keeps only Create's native foreground tooltip.

## 12. Current status

The project builds against Create `6.0.10-281` and Factory Controller `1.2.3`. Both dependency
configurations build successfully, and the mod starts cleanly with and without Create: Factory
Controller — without it, the three `@Pseudo` compatibility mixins are skipped with a warning and
nothing else changes.

Exercised in game: per-ingredient overrides and inheritance, two addresses within one craft cycle
producing exactly one correctly addressed package each, both trigger paths (manual crafting request
and stock threshold), sustained operation across many consecutive cycles, persistence, the routing
UI, and both the regular Factory Panel and a Factory Controller virtual gauge. Those runs were on
Factory Controller 1.1.1. The 1.2 compatibility layer has since been started and used once on
1.2.3 without problems, but the checklist above has not been worked through again on that line.

The persistence path on 1.2.3 was verified separately on a dedicated server holding a copy of a
world with a configured controller. With the gauge's chunk force-loaded, `/data get block` returned

```
CreateFactoryRouting: {Routes: [{Address: "F2", X: -5, Y: -1}, {Address: "F1", X: -5, Y: 1}]}
```

for the gauge at board position (-3, 1), whose own `RecipeAddress` is `A`. That NBT is produced in
memory by the `toNBT` injection, which calls `createFactoryRouting$getRouting()` and therefore the
type-filtered pruning described in §9 — had `incomingConnections(LogisticsConnection.TYPE)` come
back empty, `prunedTo` would have dropped both overrides and the tag would not appear at all. Load
and save of routing data under 1.2.3 is thus confirmed; dispatch behaviour is not.

The routing interface uses embedded side panels where space permits and compact screens at smaller
resolutions without changing the routing data or dispatch contract described above.

Two cases remain unverified at runtime; see *Known gaps* in §11.

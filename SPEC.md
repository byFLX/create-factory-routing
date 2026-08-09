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
| NeoForge | 21.1.219 or newer |
| Create | 6.0.10 or newer |
| Create: Factory Controller | Optional, 1.1.1 or newer |
| Java | 21 |

The oldest supported Create artifact and compile target is
`com.simibubi.create:create-1.21.1:6.0.10-281`.

## 3. Scope

Version 0.1 provides:

- one optional address override per ingredient connection;
- persistence and server-to-client synchronization;
- atomic multi-address dispatch;
- an in-game overview and address editor;
- a command-based diagnostic fallback;
- optional Create: Factory Controller integration.

Tags, NBT matching, priorities, round-robin selection, fallback chains, custom batch sizes, stock
conditions, and delivery-time synchronization are outside the current scope.

## 4. Create data model

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

### 6.1 Planning ledger

Create normally plans one request per logistics network. This addon may plan several address buckets
against the same network. After each plan, the selected quantities are deducted from the temporary
available-item summaries before planning the next bucket. This prevents multiple buckets from
selecting the same stock.

Temporary summary changes are invalidated after planning or aborting so later request cycles rebuild
their view from the actual inventories.

### 6.2 Passthrough behavior

If a regular panel has no overrides, the Mixin does not cancel Create's original method. This keeps
the common case on Create's native code path. The custom dispatch path is used only when routing
data requires it.

## 7. Networking and validation

Routing changes are server-authoritative. Configuration payloads validate:

- the target block entity and panel slot;
- player interaction distance;
- access to the Create logistics network;
- the existence of the referenced ingredient connection;
- non-blank addresses;
- Create's 25-character address limit.

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

## 10. Diagnostic commands

`/cfr route list` prints the active ingredient connections and their source positions. `/cfr route
set` and `/cfr route clear` provide controlled configuration without using the client UI. Commands
require permission level 2 and apply the same structural validation as network requests.

## 11. Validation checklist

### Build and loading

- Build on Java 21.
- Start with Create 6.0.10 and without Factory Controller.
- Start with Create 6.0.10 and Factory Controller 1.1.1.
- Confirm all required and optional Mixins apply in an audited development run.

### Regular panels

- A panel without overrides behaves exactly like native Create.
- One overridden ingredient is delivered to its custom address.
- Several ingredients can be sent to different addresses in one cycle.
- Removing an override restores inheritance from the panel address.
- Routing survives world reload and chunk unload.

### Atomicity and stock accounting

- If one ingredient is unavailable, no bucket is dispatched.
- Two address buckets requesting the same item do not allocate the same stock twice.
- The previous case works when stock is split across two packagers.
- A busy packager aborts the complete cycle and does not leave stale summary data.
- One recipe cycle creates exactly one output promise.

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

The project builds against Create `6.0.10-281`. Regular Factory Panel routing, optional Factory
Controller routing, persistence, dispatch, synchronization, and the routing UI have been exercised
in-game. Both dependency configurations build successfully. The routing interface uses embedded
side panels where space permits and compact screens at smaller resolutions without changing the
routing data or dispatch contract described above.

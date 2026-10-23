# Create: Factory Routing

Create: Factory Routing adds optional delivery-address overrides to the ingredient connections of
Create Factory Gauge recipes.

Normally, every requested ingredient uses the recipe gauge's default address. With this addon, each
ingredient connection can keep that default or use its own custom address. This allows one recipe
gauge to distribute ingredient deliveries across multiple destinations without splitting the setup
across several gauges.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.227 or newer
- Create 6.0.10 — required and tested
- Create: Factory Controller 1.2.1 to 1.2.3 — optional; built against 1.2.3
- Java 21

## Features

- A separate custom delivery address for each ingredient connection
- Automatic inheritance from the recipe gauge's default address
- Embedded ingredient list on the left and address editor on the right
- Compact paged fallback when there is not enough side space
- Support for regular Create Factory Gauges
- Optional support for Create: Factory Controller virtual gauges
- Joint planning of all target addresses on each logistics network within one request cycle
- Persistent, server-authoritative routing data
- English and German localization
- No new blocks or items
- `/cfr` diagnostic commands for controlled testing

## How to use

1. Open a Factory Gauge in recipe mode.
2. Select the routing icon to show the routing side panels.
3. Select an ingredient from the list on the left. Where available, the small **R** shortcut in the
   recipe grid selects it directly.
4. Enter the target address in the editor on the right and select **Save**.
5. Select **Use default** to remove the override and use the recipe gauge's default address again.

Gold entries use the gauge's default address. Teal entries use a custom address. The routing icon
toggles both side panels, and their visible state is remembered for the current game session. Their
widths adapt to the available space and GUI scale; only screens too small for the narrow side layout
fall back to the compact paged view.

The **R** shortcuts are not available in every mode. A regular gauge hides them while a crafting
arrangement is active, and a Factory Controller gauge draws them in regular mode only. The
ingredient list remains available in all modes.

## Installation

Install the built JAR in the `mods` directory of a Minecraft 1.21.1 NeoForge instance containing
Create 6.0.10. Create: Factory Controller is optional.

## Building

Gradle must run on JDK 17 through 24. The project compiles with a Java 21 toolchain.

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The output JAR is written to `build/libs/`.

To launch the development client without the optional Factory Controller compatibility mod:

```powershell
.\gradlew.bat runClient -PwithoutFactoryController
```

Without that property, the development client includes Create: Factory Controller.

## Command fallback

The `/cfr` command is intended for diagnostics and controlled runtime tests. It requires permission
level 2 and operates in the command source's current dimension.

List the ingredient connections of a recipe gauge. The argument names below are the literal command
parameters, which still use Create's internal `panel` wording:

```text
/cfr route list <panel x y z> <panel slot>
```

Set or clear an ingredient override:

```text
/cfr route set <panel x y z> <panel slot> <source x y z> <source slot> <address>
/cfr route clear <panel x y z> <panel slot> <source x y z> <source slot>
```

Valid slot names are `top_left`, `top_right`, `bottom_left`, and `bottom_right`. The longer
`/create_factory_routing` root is an alias for `/cfr`.

## Technical overview

The addon stores routing overrides as NeoForge data attachments instead of adding fields directly
to Create classes. A small Mixin into Create's `FactoryPanelBehaviour` — the class behind a Factory
Gauge — delegates gauges with active overrides to the routed dispatch implementation; gauges without
overrides continue through Create's original code path.

Create plans one target address per call and reserves nothing while it plans, so calling it once per
address would let two addresses select the same physical stock — one of them silently under-delivered
while the gauge already counts the craft as in flight. Routed dispatch therefore plans a whole
logistics network in a single walk over its links: amounts are allocated first, then the requests
are built per address following Create's own loop order, so that package grouping and order
completion keep the sequence Create produces. Each address remains an independent order.

Optional Factory Controller integration uses guarded pseudo-Mixins, so the same addon JAR works
with or without that mod installed. See [SPEC.md](SPEC.md) for the implementation contract and
validation checklist.

## Status

Working and in use. Per-ingredient overrides and inheritance, two addresses within one craft cycle,
both trigger paths, sustained operation, persistence, the routing UI, and both regular Factory
Gauges and Factory Controller virtual gauges have been exercised in game.

Two dispatch cases have not been reproduced in game yet: the same item routed to two different
addresses, and a single order large enough to span more than one package. Both are implemented and
reviewed against Create's own code, but neither has been observed at runtime. Large Factory
Controller recipes in particular can reach the second case. See *Known gaps* in [SPEC.md](SPEC.md).

Factory Controller 1.2 moved the gauge classes into a new package and replaced the connection
lookup the compatibility layer used. The addon follows those changes and builds against 1.2.3. A
first run with 1.2.3 installed starts and works; the full checklist in [SPEC.md](SPEC.md) has not
been repeated on the 1.2 line. The regular Factory Gauge path is unaffected.

## Credits and licensing

Create: Factory Routing is an independent addon for
[Create](https://www.curseforge.com/minecraft/mc-mods/create) and is not affiliated with or endorsed
by the Create Team.

Portions of the routed request-planning implementation are adapted from the MIT-licensed Create
codebase:

- Create — Copyright (c) The Create Team / The Creators of Create
- Source: <https://github.com/Creators-of-Create/Create>
- License: <https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/LICENSE.md>

Optional compatibility is provided for
[Create: Factory Controller](https://www.curseforge.com/minecraft/mc-mods/create-factory-controller):

- Create: Factory Controller — Copyright (c) 2026 nbcss
- Source: <https://github.com/nbcss/create-factory-controller>
- License: <https://github.com/nbcss/create-factory-controller/blob/main/LICENSE>

No graphical, audio, or other binary assets from Create or Create: Factory Controller are bundled
with this addon. Both projects remain separate dependencies and are distributed by their respective
authors.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the complete license notices.

## License

This project is licensed under the [MIT License](LICENSE).

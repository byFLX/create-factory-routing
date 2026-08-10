# Create: Factory Routing

Per-ingredient target addresses for Create's Factory Panels.

Create normally assigns one recipe address to every ingredient of a Factory Panel recipe. This
addon lets each ingredient use its own optional target address while keeping the panel address as
the default. A single recipe panel can therefore route different ingredients to different machines
without splitting the recipe across several panels.

| Requirement | Version |
|---|---|
| Minecraft | 1.21.1 |
| Mod loader | NeoForge 21.1.219 or newer |
| Create | 6.0.10 or newer |
| Create: Factory Controller | Optional, 1.1.1 or newer |
| Java | 21 |

## Features

- Optional target-address override for every ingredient link
- The original Factory Panel address remains the default
- Atomic availability checks across all ingredients before dispatch
- Several addresses on one logistics network are planned in a single pass, so two of them can never
  be assigned the same stock, whatever inventory sits behind the packager
- Embedded ingredient list and per-ingredient address editor
- Compact routing fallback for resolutions without enough side space
- Native compatibility with regular Create Factory Panels
- Optional compatibility with Create: Factory Controller virtual gauges
- Persistent, server-authoritative routing data
- `/cfr` diagnostic commands for controlled testing

## Using the routing UI

1. Open a Factory Panel in recipe mode.
2. Select the routing icon next to the normal panel controls to show the routing side panels.
3. Choose an ingredient in the list on the left, or use its small **R** shortcut in the recipe grid.
4. Edit the target address on the right and select **Save**.
5. Select **Use default** to remove the override and inherit the panel address again.

Gold entries use the panel's default address. Teal entries have a custom address override. The
main routing icon toggles both side panels. Their widths adapt to the available space and Minecraft
GUI scale, and their visible/hidden state is remembered when the recipe screen is reopened during
the same game session. Only screens that cannot fit even the narrow side layout use the compact paged view. This workflow is also
available for Create: Factory Controller virtual recipe gauges when that mod is installed.

## Installation

Install the built JAR in the `mods` directory of a Minecraft 1.21.1 NeoForge instance containing
Create 6.0.10 or newer. Create: Factory Controller is optional.

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

List the ingredient links of a recipe panel:

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
to Create classes. A small Mixin delegates panels with active overrides to the routed dispatch
implementation; panels without overrides continue through Create's original code path.

Create plans one target address per call and reserves nothing while it plans, so calling it once per
address would let two addresses select the same physical stock — one of them silently under-delivered
while the panel already counts the craft as in flight. Routed dispatch therefore plans a whole
logistics network in a single walk over its links: amounts are allocated first, then the requests
are built per address in Create's own order, so package grouping and order completion stay
unchanged. Each address remains an independent order.

Optional Factory Controller integration uses guarded pseudo-Mixins, so the same addon JAR works
with or without that mod installed. See [SPEC.md](SPEC.md) for the implementation contract and
validation checklist.

## Status

Working and in use. Per-ingredient overrides and inheritance, two addresses within one craft cycle,
both trigger paths, sustained operation, persistence, the routing UI, and both regular Factory
Panels and Factory Controller virtual gauges have been exercised in game.

Two dispatch cases are correct by construction but have not been reproduced in game yet: the same
item routed to two different addresses, and a single order large enough to span more than one
package. Both require a deliberately unusual board, which is also why they are unlikely to occur by
accident. See *Known gaps* in [SPEC.md](SPEC.md).

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

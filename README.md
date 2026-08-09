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
- Correct stock accounting when several address groups use the same logistics network
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

Optional Factory Controller integration uses guarded pseudo-Mixins, so the same addon JAR works
with or without that mod installed. See [SPEC.md](SPEC.md) for the implementation contract and
validation checklist.

## License

This project is licensed under the [MIT License](LICENSE).

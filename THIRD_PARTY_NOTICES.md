# Third-Party Notices

Create: Factory Routing is licensed under the MIT License; see [LICENSE](LICENSE).

This document lists the third-party projects this addon builds on, together with their complete
license notices. Both are separate mods, distributed by their own authors, and are **not** bundled
with this addon — they must be installed alongside it.

No graphical, audio, or other binary assets from either project are included in this addon's JAR.
Textures and sounds that appear in the routing interface are loaded at runtime from the installed
Create mod.

---

## Create

Parts of the routed request planning in this addon are adapted from Create's own implementation, in
particular from `FactoryPanelBehaviour.tickRequests()` and
`LogisticsManager.findPackagersForRequest()`. The affected files are
`dev.failzockt.factoryrouting.dispatch.RoutedDispatch` and
`dev.failzockt.factoryrouting.dispatch.RoutedPlanner`, both of which carry a provenance note in
their source.

- Project: Create
- Source: <https://github.com/Creators-of-Create/Create>
- License: <https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/LICENSE.md>

Create splits its licensing: everything under `src/main/resources/assets/` is All Rights Reserved,
while the code is MIT. Only the MIT-licensed code is relevant here — no Create assets are copied
into this addon.

### Assets License (All Rights Reserved)

```
All Rights Reserved

Copyright (c) The Create Team / The Creators of Create

This license applies to any files under the following directories:

- ./src/main/resources/assets/
```

### Code License (MIT)

```
MIT License

Copyright (c) The Create Team / The Creators of Create

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Create: Factory Controller

Optional compatibility only. This addon accesses Factory Controller classes through guarded
`@Pseudo` Mixins that are skipped when the mod is absent. No Factory Controller code is copied into
this addon's JAR.

- Project: Create: Factory Controller
- Author: nbcss
- Source: <https://github.com/nbcss/create-factory-controller>
- License: <https://github.com/nbcss/create-factory-controller/blob/main/LICENSE>

```
MIT License

Copyright (c) 2026 nbcss

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

Create: Factory Routing is an independent addon and is not affiliated with, endorsed by, or
supported by the Create Team or the author of Create: Factory Controller.

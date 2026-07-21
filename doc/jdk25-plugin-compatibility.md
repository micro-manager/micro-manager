# JDK 25 plugin compatibility assessment

This note records an audit of the Micro-Manager plugins and their dependencies
for compatibility with modern JDKs (17 through 25), as a companion to the
JDK 25 build/launch support (see [how-to-build.md](how-to-build.md) and the
`--add-opens` / `--enable-native-access` flags the launchers pass).

## Bottom line

No hard blockers were found for the plugins. The one JDK-17+ breakage category
that matters in practice - reflective access into JDK internals blocked by
strong module encapsulation - appears in only two places, and both are either
dead code or degrade gracefully. Everything genuinely risky in the bundled
dependency set turns out to be unreachable (present as an artifact but
referenced by no code on any live path).

**No code changes are required for JVM 25 plugin compatibility.** The only
open item is a recommended manual smoke test of the two GPU plugins on a real
JDK 25 machine with a GPU (see Caveats).

## Method

- All plugin source (`plugins/*/`) was scanned for removed/encapsulated-API
  patterns.
- The risky third-party jars in `dependencies/artifacts/compile/` were traced
  for *reachability* - i.e. whether any code actually references them - rather
  than merely noting their presence.
- The native-memory / OpenGL / OpenCL classes were decompiled with `javap` to
  see exactly what they reflect into and whether those paths are reached.

## What is clean (no action)

- **No plugin source** imports `sun.*` / `com.sun.*`, uses `sun.misc.Unsafe`,
  `SecurityManager` / `AccessController` / `doPrivileged`, `Thread.stop` /
  `suspend` / `resume`, the Applet API, Nashorn / `javax.script`, JavaFX, JAXB
  (`javax.xml.bind`), or any other removed Java EE module.
- The `.stop()` calls in autolase, ASIdiSPIM, and others are on **custom
  `Runnable` classes that define their own `stop()`** (e.g.
  `autolase.LaseThread`, `DensityThread`, `DensityMap` all `implements
  Runnable`), not on `java.lang.Thread`. They are **not** the removed
  `Thread.stop()`. (Initially flagged, then cleared.)
- **Kryo 2.24.0, Clojure 1.3.0, objenesis, ASM, cglib, Javassist, reflectasm,
  and JNA** are present as artifacts in the dependency directory but are
  **referenced by nothing** on any reachable code path (verified by scanning
  every bundled jar's bytecode for references to those packages). Their known
  JDK-17+ issues (Unsafe-based instantiation, old bundled ASM, `URLClassLoader`
  assumptions) therefore cannot fire.

## The two real findings (both non-fatal)

### 1. Magellan `internal/misc/JavaUtils.addURL()` - JDK-9+ incompatible, but dead code

`plugins/Magellan/src/main/java/org/micromanager/magellan/internal/misc/JavaUtils.java`
casts the application class loader to `URLClassLoader` (which fails on JDK 9+,
where the app loader is `jdk.internal.loader.ClassLoaders$AppClassLoader`) and
`setAccessible`s `URLClassLoader.addURL` (which would additionally throw
`InaccessibleObjectException` on JDK 17+).

However, `addURL()` is only called by `findAndLoadClasses()`, which has **no
callers anywhere** in Magellan; the sibling reflection helpers
(`invokeRestrictedMethod`, `getRestrictedFieldValue`, `setRestrictedFieldValue`)
are also unused. The failure is caught and logged in any case.

**Impact:** none at runtime. Candidate for deletion as cleanup, not a
compatibility fix.

### 2. ClearVolume / ClearCL native stack - degrades gracefully

Affects **MMClearVolumePlugin** (ClearVolume + JOGL) and **MultiChannelShading**
(ClearCL / OpenCL). Both go through `coremem-0.4.5`.

- **JOGL / gluegen 2.3.2** (`com.jogamp.common.os.NativeLibrary`) reflects into
  a non-public `java.lang.ClassLoader` method and calls `setAccessible(true)`.
  On JDK 17+ that throws `InaccessibleObjectException` - **but the call is
  wrapped in `catch (Exception)`**, so JOGL simply drops one native-library
  lookup strategy and continues via its fallbacks. Consistent with the existing
  observation that ClearVolume initializes and renders (aside from an unrelated
  HiDPI band bug).
- **coremem `OffHeapMemoryAccess`** uses `sun.misc.Unsafe.allocateMemory` (and
  friends) for off-heap GPU transfer buffers. `sun.misc.Unsafe` lives in the
  `jdk.unsupported` module, which is exported/open, so this **works on JDK 25**
  without any `--add-opens`. The memory methods are deprecated for removal
  (JEP 471) but remain present and functional through JDK 25.
- **coremem `NIOBuffersInterop`** reflects `java.nio.Buffer.address`
  (`getDeclaredField("address")` + `setAccessible` + `getLong`). This is the one
  genuine hard-failure path - it would need `--add-opens
  java.base/java.nio=ALL-UNNAMED`. **But no class in
  clearvolume/clearcl/coremem/cleargl references `NIOBuffersInterop`**, so it is
  never reached on the plugin code paths.

**Impact:** the plugins run. Do **not** add `java.base/java.nio` or
`java.base/java.lang` opens to "fix" this - nothing reaches the paths that would
need them, and adding opens needlessly re-weakens encapsulation.

## Caveats / follow-ups

- The "JDK 11-25 verified to work" statement in `how-to-build.md` covered core
  MMStudio, **not** the GPU plugins. MMClearVolumePlugin and MultiChannelShading
  should get an actual smoke test on JDK 25 on a machine with real OpenGL /
  OpenCL. They are expected to run (per the analysis above); watch the log for
  benign JOGL reflection warnings, and note the pre-existing ClearVolume HiDPI
  band bug is a library issue unrelated to JDK version.
- `sun.misc.Unsafe`'s memory methods are on a removal track. This is not a
  JDK 25 problem, but because coremem / ClearVolume / ClearGL are effectively
  unmaintained, a future JDK will eventually break the 3D viewer. Worth noting
  for planning, not for fixing now.
- BeanShell (`bsh-2.0b6`) is used by the mmstudio Script Panel (not by plugins)
  and carries its own bundled bytecode-manipulation classes; it is out of scope
  here and already known to run.

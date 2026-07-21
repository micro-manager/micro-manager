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

## Clojure acquisition engine (`acqEngine/`)

The legacy acquisition engine is written in Clojure (`acqEngine/src/main/clj/`),
**AOT-compiled** at build time via `clojure.lang.Compile` (see
`buildscripts/clojurebuild.xml` / `buildprops.xml`), and loaded at runtime by
`MMStudio.getAcquisitionEngine2010()` reflecting the public constructor of the
generated class `org.micromanager.internal.AcquisitionEngine2010` (from the
`:gen-class` in `acq_engine.clj`) - a normal classpath class in the unnamed
module.

- **Clojure version: 1.3.0 (2011).** Old, but its class files are bytecode
  major version 49 (Java 5), which JDK 25 loads without issue; the AOT-compiled
  engine classes are the same (v49). A full javap disassembly of all 2867
  classes in `clojure-1.3.0.jar` found **no references to `sun.misc.Unsafe`,
  `jdk.internal.*`, `sun.nio.ch.*`, or any other encapsulated JDK package**.
- **Runtime class generation is subclass-based, not a reflective hack.**
  `clojure.lang.DynamicClassLoader extends java.net.URLClassLoader` and calls
  the inherited `ClassLoader.defineClass` directly. It does **not** use the
  reflective `setAccessible(ClassLoader.defineClass)` trick that later became an
  encapsulation problem, so `proxy` / `reify` / `:gen-class` need **no
  `--add-opens`**.
- **Engine source is clean.** No `setAccessible`, `sun.*`, `Unsafe`, or
  JDK-internal reflection in any `.clj` file. The `proxy`/`reify` sites target
  ordinary public superclasses (`LinkedBlockingQueue`, MMCore SWIG vector types,
  Swing adapters). Aux libs `core.cache`, `core.memoize`, `data.json` are
  likewise clean.
- **Residual risk - reflective Java interop.** The engine carries only ~10 type
  hints, so Clojure resolves most MMCore/Studio calls reflectively via
  `clojure.lang.Reflector` at runtime. Clojure 1.3.0 predates the JDK-9
  "public method on an inaccessible class" reflection fixes (fully addressed in
  Clojure 1.10). In practice the engine's reflective targets are public methods
  of public, exported classes (`mmcorej.CMMCore`, the mmstudio API,
  `TaggedImage`, JSON), so this is not expected to bite - but because it is
  data-/path-dependent, the Clojure engine is the item that most warrants an
  actual run on JDK 25 (see Caveats).

## BeanShell scripting (`bsh-2.0b6`, mmstudio Script Panel)

Used by `mmstudio/.../internal/script/` (`BeanshellEngine`, `ScriptPanel`) as
the interactive scripting language. BeanShell interprets Java and does heavy
runtime reflection, so it was examined closely.

- **Class generation is subclass-based.** `bsh.classpath.BshClassLoader extends
  java.net.URLClassLoader`; scripted `class`/`interface` definitions use the
  inherited `defineClass`. No reflective ClassLoader hack -> **no `--add-opens`
  needed** for BeanShell class generation.
- **`setAccessibility` is member-bypass on the script target, not JDK
  internals.** `bsh.ReflectManager` (loaded optionally via `Class.forName`, and
  marked "unavailable" if it fails) calls `AccessibleObject.setAccessible` only
  when the script enables the "accessibility" capability. The Script Panel does
  call `setAccessibility(true)` (`ScriptPanel.clearOutput()`), but specifically
  to reach BeanShell's own `bsh.console.text` (a public Swing `JTextComponent`),
  not an encapsulated JDK package. A javap scan of all 164 classes in
  `bsh-2.0b6.jar` found **no references to any encapsulated JDK internal
  package**.
- **Verified empirically.** On JDK 11 (which already enforces the module
  system), a standalone test exercised interpreter init, `setAccessibility(true)`,
  runtime class generation (`class Foo implements Runnable {...}`), and
  reflective interop against public JDK classes - all succeeded.
- **Residual risk - user scripts.** If a *user script* reflectively pokes a
  private member of an encapsulated JDK class with accessibility on, it will get
  `InaccessibleObjectException` on JDK 17+. That is inherent to running old
  scripts on a new JDK, not a defect in Micro-Manager, and the fix (if ever
  needed for a specific script) is a targeted `--add-opens`, not a blanket one.

## Are more `--add-opens` flags needed? No.

A reliable (javap-based) disassembly scan of the reachable code plus a
source-level scan of the whole tree (`mmstudio`, `plugins`, `libraries`,
`acqEngine`) found **exactly two** places that load an encapsulated JDK class,
and only one needs a flag:

1. `GUIUtils.preventDisplayAdapterChangeExceptions()` loads
   `sun.awt.Win32GraphicsEnvironment` and reflectively invokes its public
   `displayChanged()` (a Windows-only display-adapter workaround). This is
   exactly what the existing **`--add-opens java.desktop/sun.awt=ALL-UNNAMED`**
   supports. Already provided by the launchers.
2. `PhysicalMemoryInfoSection` reflects public methods of
   `com.sun.management.OperatingSystemMXBean`, which lives in the **exported**
   `jdk.management` module - no flag needed, and it is fully try/catch-guarded.

Everything else that *could* have needed a flag is either subclass-based
(Clojure/BeanShell `defineClass`), in an exported/open module
(`sun.misc.Unsafe` -> `jdk.unsupported`), caught-and-non-fatal (JOGL's
`java.lang.ClassLoader` reflection), or unreachable (Kryo's Unsafe/DirectBuffer
- confirmed no consumer references it in any source or reachable jar).

**Do not add `--add-opens java.base/java.nio`, `java.base/java.lang`, or others
speculatively.** Nothing on a reachable path needs them, and each one needlessly
re-weakens module encapsulation.

## Caveats / follow-ups

- The "JDK 11-25 verified to work" statement in `how-to-build.md` covered core
  MMStudio, **not** the GPU plugins or the two scripting/engine runtimes below.
- **GPU plugins:** MMClearVolumePlugin and MultiChannelShading should get an
  actual smoke test on JDK 25 on a machine with real OpenGL / OpenCL. They are
  expected to run (per the analysis above); watch the log for benign JOGL
  reflection warnings, and note the pre-existing ClearVolume HiDPI band bug is a
  library issue unrelated to JDK version.
- **Clojure engine:** run an actual acquisition on JDK 25 (single-channel plus a
  multi-dimensional acquisition). This is the highest-value runtime test because
  Clojure 1.3.0 does reflective Java interop and predates the JDK-9 reflection
  fixes; the analysis says it is fine, but only a real run exercises the
  reflective call sites with real data.
- **BeanShell:** run a few representative Script Panel scripts on JDK 25. Core
  interpreter/class-gen is verified safe; the only exposure is a user script
  that itself reflects into encapsulated JDK internals.
- `sun.misc.Unsafe`'s memory methods are on a removal track. This is not a
  JDK 25 problem, but because coremem / ClearVolume / ClearGL are effectively
  unmaintained, a future JDK will eventually break the 3D viewer. Worth noting
  for planning, not for fixing now.

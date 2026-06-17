AcqEngineTests: integration tests for the two acquisition engines
==================================================================

These tests drive Micro-Manager's two high-level acquisition engines -- the
modern AcqEngJ (AcqEngJAdapter) and the legacy Clojure engine
(AcquisitionWrapperEngine) -- through the public API
(studio.acquisitions().runAcquisitionWithSettings(...)) and verify the result.

Unlike the sibling SequenceTests module, which exercises the raw
AcquisitionEngine2010 directly, these tests boot a real (headless-ish) MMStudio
and toggle settings().setShouldUseAcqEngJ(...) so each scenario is run through
BOTH engines, exactly as a user would hit them from the MDA dialog.

Hardware comes from the SequenceTester device adapter (see
DeviceAdapters/SequenceTester): a virtual camera whose images encode the full
state and set-history of all SequenceTester devices. We decode that embedded
"InfoPacket" from each image in the resulting Datastore to assert not just the
number/coordinates of images, but also that the right hardware moved (e.g. Z set
per slice, channel switcher moved, autofocus ran on the expected frames).


What is covered
---------------
Each scenario is parameterized over both engines (acqEngJ = {false, true}):

  SmokeTest         boot + a time-lapse on each engine
  ChannelsTest      multi-channel: count/coords, switcher position per channel
  ZStackTest        z-stack count + Z moves; channel-only Z handling
  TimeLapseTest     fixed and custom intervals, frame ordering
  MultiPositionTest position list: per-position images, XY moves, P/T ordering
  SequencedTest     hardware-triggered channel bursts vs. software (non-seq.)
  AutofocusTest     autofocus per frame and skipAutofocusCount
  CombinedTest      full channels x time x z (x positions) cross-axis MDAs

Some scenarios assert engine-aware behavior where the two engines legitimately
differ (documented inline): the channel-only Z-stack handling and the
interpretation of skipAutofocusCount.


How to run
----------
Prerequisite: the native libraries must be built and current (matching device
interface version). In a normal build, mmCoreAndDevices is a subdirectory of the
micro-manager source tree, and the DLLs end up in
mmCoreAndDevices/build/Release/x64 -- which is ../../mmCoreAndDevices/build/Release/x64
relative to this module. You need there:
  - MMCoreJ_wrap.dll              (the Core JNI library)
  - mmgr_dal_SequenceTester.dll   (the test device adapter)
If you change C++ (or the interface version bumps), rebuild MMCoreJ_wrap and the
SequenceTester adapter in Visual Studio first.

Set two environment variables to the directory holding those DLLs, then run ant
from THIS directory. The paths below are relative to this module directory; both
variables point to the same folder. (For a Debug build, swap Release -> Debug.)

  Git Bash:
    cd systemtest/AcqEngineTests
    export MMCOREJ_LIBRARY_PATH="../../mmCoreAndDevices/build/Release/x64"
    export MMTEST_ADAPTER_PATH="../../mmCoreAndDevices/build/Release/x64"
    ant test-only
    ant test-only -Dtest.class=org.micromanager.acqenginetests.ChannelsTest

  PowerShell:
    cd systemtest\AcqEngineTests
    $env:MMCOREJ_LIBRARY_PATH = "..\..\mmCoreAndDevices\build\Release\x64"
    $env:MMTEST_ADAPTER_PATH  = "..\..\mmCoreAndDevices\build\Release\x64"
    ant test-only
    ant test-only -Dtest.class=org.micromanager.acqenginetests.ChannelsTest

Use the "test-only" target (not "test"): there is no src/main/java to jar, and
test-only compiles and runs the tests directly. Reports are written to
build/JavaTestReports/AcqEngineTester/; a failure fails the ant build.

These tests reuse the image decoder (TestImageDecoder/TaggedImageDecoder) from
the SequenceTests module, so its test classes are added to the classpath in
build.xml; SequenceTests is therefore compiled as part of this run.


Notes and gotchas
-----------------
- NOT headless. MMStudio builds Swing components, so build.xml overrides the
  shared test target to run with java.awt.headless=false. A window will flash up
  briefly during the run -- that is expected; do not close it. On a headless CI
  host this needs a virtual display (e.g. Xvfb on Linux).

- First run of each test class is slower (~20 s). The legacy Clojure engine
  cold-starts (JIT + namespace loading) on first use; the fixture runs one
  throwaway "warm-up" acquisition so that cost is paid before the asserted
  tests. After warm-up each test is a few seconds. A full module run takes a
  few minutes because each test class forks its own JVM and warms up once.

- Do not wrap the command in an external "timeout": killing the JVM mid-run
  produces confusing "Forked Java VM exited abnormally" results. Just let it run.

- Rare startup hang: MMStudio construction can occasionally wedge the EDT at
  startup (a demo-config/Background init race, before the test code runs). It is
  intermittent and isolated to one forked JVM; kill the stuck java.exe and
  re-run -- it passes on retry.

- There is intentionally no engine-equivalence test (running both engines
  back-to-back in one method). The legacy Clojure engine is too unstable under
  rapid repeated programmatic acquisition to compare deterministically; instead,
  every scenario above runs and asserts on both engines individually.

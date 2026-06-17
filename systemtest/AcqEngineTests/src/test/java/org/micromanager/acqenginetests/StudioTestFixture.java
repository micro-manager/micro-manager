package org.micromanager.acqenginetests;

import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Boots a headless {@link MMStudio} backed by the SequenceTester device adapter
 * so the high-level acquisition engines (AcqEngJAdapter and the legacy
 * AcquisitionWrapperEngine) can be driven from JUnit.
 *
 * <p>MMStudio enforces a single instance per JVM and a lot of internal state is
 * static, so this fixture is intended to be created once and shared (see
 * {@link #getInstance()}). The JUnit task forks a JVM per test class, which
 * keeps separate test classes isolated.
 *
 * <p>The two environment variables used by the existing SequenceTests harness
 * must be set so the native libraries can be found:
 * <ul>
 *   <li>{@code MMCOREJ_LIBRARY_PATH} - directory containing MMCoreJ_wrap</li>
 *   <li>{@code MMTEST_ADAPTER_PATH} - directories (path-separated) containing
 *       the device adapter shared libraries, including SequenceTester</li>
 * </ul>
 *
 * <p>This class is deliberately not a JUnit {@code @Rule}: starting MMStudio is
 * expensive and can only happen once, so tests use the shared instance and
 * {@link #reset()} between tests.
 */
public final class StudioTestFixture {
   private static StudioTestFixture instance_;

   private final MMStudio studio_;
   private final CMMCore core_;

   private StudioTestFixture() {
      // Make the MMCoreJ_wrap native library discoverable. MMStudio's
      // constructor creates its own CMMCore, so the JNI library path must be a
      // system property before construction; the adapter search path is set on
      // that Core afterwards (below).
      String jniLibPath = System.getenv("MMCOREJ_LIBRARY_PATH");
      System.setProperty("mmcorej.library.loading.stderr.log", "yes");
      if (jniLibPath != null) {
         System.setProperty("mmcorej.library.path", jniLibPath);
      }

      // new MMStudio(false) is the same call the Gaussian plugin's
      // TestParticlePairLister uses headlessly. In a fresh test environment the
      // intro dialog is skipped; if a CI environment ever blocks here, set the
      // StartupSettings skip flags on the default profile before this call.
      studio_ = new MMStudio(false);
      core_ = studio_.core();

      // CRITICAL for unattended runs: suppress modal error dialogs. The
      // acquisition engines report failures via studio.logs().showError(),
      // which routes through ReportingUtils and pops a MODAL JOptionPane. In a
      // non-headless test JVM with no one to click "OK", that dialog blocks the
      // EDT forever and hangs the whole test run. Errors are still logged.
      ReportingUtils.showErrorOn(false);

      String adapterPaths = System.getenv("MMTEST_ADAPTER_PATH");
      StrVector paths = new StrVector();
      if (adapterPaths != null) {
         for (String path :
               adapterPaths.split(java.util.regex.Pattern.quote(
                     java.io.File.pathSeparator))) {
            paths.add(path);
         }
      }
      core_.setDeviceAdapterSearchPaths(paths);

      core_.enableStderrLog(true);
      core_.enableDebugLog(true);
   }

   /**
    * Returns the shared fixture, creating (and booting MMStudio) on first call.
    *
    * @return the shared fixture
    */
   public static synchronized StudioTestFixture getInstance() {
      if (instance_ == null) {
         instance_ = new StudioTestFixture();
         try {
            instance_.warmUpEngines();
         } catch (Exception e) {
            throw new RuntimeException("Failed to warm up acquisition engines", e);
         }
      }
      return instance_;
   }

   /**
    * Runs one trivial acquisition on each engine before any test, to pay
    * one-time initialization costs up front.
    *
    * <p>The legacy (Clojure) engine in particular JIT-compiles and loads its
    * namespaces on first use, which can take ~20 s -- long enough to exceed the
    * default per-image camera timeout and make the FIRST real acquisition fail
    * with "Timed out waiting for image to arrive from camera". Warming up here
    * keeps that cost off every test's measured path.
    */
   public void warmUpEngines() throws Exception {
      // Only the legacy (Clojure) engine has a costly cold start; AcqEngJ does
      // not, so we warm up the legacy engine only.
      loadStandardDevices();
      useAcqEngJ(false);
      SequenceSettings s = studio_.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(1)
            .intervalMs(0.0)
            // Generous timeout so the cold Clojure start cannot self-abort.
            .cameraTimeout(120000)
            .save(false)
            .shouldDisplayImages(false)
            .build();
      studio_.acquisitions().runAcquisitionWithSettings(s, true);
   }

   public MMStudio getStudio() {
      return studio_;
   }

   public CMMCore getCore() {
      return core_;
   }

   /** Selects which acquisition engine the next acquisition will use. */
   public void useAcqEngJ(boolean useAcqEngJ) {
      studio_.settings().setShouldUseAcqEngJ(useAcqEngJ);
   }

   /**
    * Unloads all devices and reloads a clean set of SequenceTester devices.
    * Call from {@code @Before} so each test starts from a known state.
    *
    * <p>Loads a hub plus a machine-readable camera, a Z stage, an XY stage, a
    * state switcher (for channels), and an autofocus device. Image size is set
    * large enough (128x128) to hold the embedded MessagePack InfoPacket.
    *
    * @throws Exception if any Core call fails
    */
   public void loadStandardDevices() throws Exception {
      core_.unloadAllDevices();

      core_.loadDevice("THub", "SequenceTester", "THub");
      core_.initializeDevice("THub");

      loadChildDevice("TCamera", "TCamera");
      loadChildDevice("TZStage", "TZStage");
      loadChildDevice("TXYStage", "TXYStage");
      loadChildDevice("TSwitcher", "TSwitcher");
      loadChildDevice("TAutofocus", "TAutofocus");

      core_.setProperty("TCamera", "ImageMode", "MachineReadable");
      core_.setProperty("TCamera", "ImageWidth", 128);
      core_.setProperty("TCamera", "ImageHeight", 128);
      core_.initializeDevice("TCamera");
      core_.initializeDevice("TZStage");
      core_.initializeDevice("TXYStage");
      core_.initializeDevice("TSwitcher");
      core_.initializeDevice("TAutofocus");

      core_.setCameraDevice("TCamera");
      core_.setFocusDevice("TZStage");
      core_.setXYStageDevice("TXYStage");

      // Unloading/reloading devices makes the Core post notifications that
      // MMStudio's managers (SnapLive, Shutter, config-group table) handle on
      // the EDT. Let those settle before the test runs an acquisition, so a
      // queued EDT event can't race with -- and occasionally deadlock against
      // -- the acquisition. Without this drain the suite hangs intermittently.
      drainEdt();
   }

   /**
    * Blocks until the AWT event queue has processed everything currently
    * pending (including events posted by the Core notifications above).
    */
   public static void drainEdt() {
      if (java.awt.GraphicsEnvironment.isHeadless()) {
         return;
      }
      try {
         // Run twice: the first drain may post follow-up events.
         SwingUtilities.invokeAndWait(() -> { });
         SwingUtilities.invokeAndWait(() -> { });
      } catch (Exception e) {
         throw new RuntimeException("Interrupted while draining the EDT", e);
      }
   }

   private void loadChildDevice(String label, String name) throws Exception {
      core_.loadDevice(label, "SequenceTester", name);
      core_.setParentLabel(label, "THub");
   }

   /**
    * Defines a channel group on the TSwitcher device with the given presets,
    * one preset per state, and makes it the Core's channel group.
    *
    * @param group   channel group name
    * @param presets preset (channel config) names; state index = array index
    * @throws Exception if any Core call fails
    */
   public void defineChannelGroup(String group, String... presets) throws Exception {
      for (int i = 0; i < presets.length; i++) {
         core_.defineConfig(group, presets[i], "TSwitcher", "State",
               Integer.toString(i));
      }
      core_.setChannelGroup(group);
   }

   /** Convenience builder for a channel preset in the standard group. */
   public ChannelSpec channel(String group, String preset, double exposureMs) {
      return new ChannelSpec.Builder()
            .channelGroup(group)
            .config(preset)
            .exposure(exposureMs)
            .build();
   }

   /** Resets Core and Studio state between tests. */
   public void reset() throws Exception {
      // The position list is Studio state, not Core state, so a device reload
      // does not clear it. Clear it so a list set by one test does not bleed
      // into the next.
      setPositionList(new org.micromanager.PositionList());
      loadStandardDevices();
   }

   /**
    * Sets the Studio position list and lets the resulting NewPositionListEvent
    * propagate. The acquisition engines keep their own {@code posList_} field
    * that is updated only when this event is delivered on the bus, so we must
    * drain the EDT before running an acquisition or the engine will use a stale
    * (or empty) list.
    *
    * @param pl the position list to install
    */
   public void setPositionList(org.micromanager.PositionList pl) {
      studio_.positions().setPositionList(pl);
      drainEdt();
   }
}

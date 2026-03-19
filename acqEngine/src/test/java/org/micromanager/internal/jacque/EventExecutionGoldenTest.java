package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentHashSet;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import mmcorej.TaggedImage;
import org.micromanager.PositionList;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EventExecutionGoldenTest {

   private static final String GOLDEN_RESOURCE_DIR =
         "org/micromanager/internal/jacque/execution-golden";

   // Clojure keywords for events
   private static final Keyword KW_FRAME_INDEX =
         Keyword.intern("frame-index");
   private static final Keyword KW_SLICE_INDEX =
         Keyword.intern("slice-index");
   private static final Keyword KW_CHANNEL_INDEX =
         Keyword.intern("channel-index");
   private static final Keyword KW_POSITION_INDEX =
         Keyword.intern("position-index");
   private static final Keyword KW_POSITION = Keyword.intern("position");
   private static final Keyword KW_EXPOSURE = Keyword.intern("exposure");
   private static final Keyword KW_SLICE = Keyword.intern("slice");
   private static final Keyword KW_WAIT_TIME_MS =
         Keyword.intern("wait-time-ms");
   private static final Keyword KW_AUTOFOCUS = Keyword.intern("autofocus");
   private static final Keyword KW_NEW_POSITION =
         Keyword.intern("new-position");
   private static final Keyword KW_CLOSE_SHUTTER =
         Keyword.intern("close-shutter");
   private static final Keyword KW_RELATIVE_Z =
         Keyword.intern("relative-z");
   private static final Keyword KW_TASK = Keyword.intern("task");
   private static final Keyword KW_NEXT_FRAME_INDEX =
         Keyword.intern("next-frame-index");
   private static final Keyword KW_CHANNEL = Keyword.intern("channel");
   private static final Keyword KW_BURST_LENGTH =
         Keyword.intern("burst-length");
   private static final Keyword KW_BURST_DATA =
         Keyword.intern("burst-data");
   private static final Keyword KW_TRIGGER_SEQUENCE =
         Keyword.intern("trigger-sequence");
   private static final Keyword KW_METADATA = Keyword.intern("metadata");
   private static final Keyword KW_CAMERA_CHANNEL_INDEX =
         Keyword.intern("camera-channel-index");
   private static final Keyword KW_CAMERA = Keyword.intern("camera");
   private static final Keyword KW_RUNNABLES = Keyword.intern("runnables");

   // Clojure keywords for channel maps
   private static final Keyword KW_NAME = Keyword.intern("name");
   private static final Keyword KW_Z_OFFSET = Keyword.intern("z-offset");
   private static final Keyword KW_USE_Z_STACK =
         Keyword.intern("use-z-stack");
   private static final Keyword KW_USE_CHANNEL =
         Keyword.intern("use-channel");
   private static final Keyword KW_SKIP_FRAMES =
         Keyword.intern("skip-frames");
   private static final Keyword KW_PROPERTIES =
         Keyword.intern("properties");
   private static final Keyword KW_SLICES = Keyword.intern("slices");

   // Clojure keywords for state map
   private static final Keyword KW_PAUSE = Keyword.intern("pause");
   private static final Keyword KW_STOP = Keyword.intern("stop");
   private static final Keyword KW_FINISHED = Keyword.intern("finished");
   private static final Keyword KW_LAST_WAKE_TIME =
         Keyword.intern("last-wake-time");
   private static final Keyword KW_LAST_STAGE_POSITIONS =
         Keyword.intern("last-stage-positions");
   private static final Keyword KW_REFERENCE_Z =
         Keyword.intern("reference-z");
   private static final Keyword KW_START_TIME =
         Keyword.intern("start-time");
   private static final Keyword KW_INIT_AUTO_SHUTTER =
         Keyword.intern("init-auto-shutter");
   private static final Keyword KW_INIT_EXPOSURE =
         Keyword.intern("init-exposure");
   private static final Keyword KW_INIT_SHUTTER_STATE =
         Keyword.intern("init-shutter-state");
   private static final Keyword KW_CLJ_EXPOSURE =
         Keyword.intern("exposure");
   private static final Keyword KW_DEFAULT_Z_DRIVE =
         Keyword.intern("default-z-drive");
   private static final Keyword KW_DEFAULT_XY_STAGE =
         Keyword.intern("default-xy-stage");
   private static final Keyword KW_AUTOFOCUS_DEVICE =
         Keyword.intern("autofocus-device");
   private static final Keyword KW_POSITION_LIST =
         Keyword.intern("position-list");
   private static final Keyword KW_INIT_Z_POSITION =
         Keyword.intern("init-z-position");
   private static final Keyword KW_INIT_SYSTEM_STATE =
         Keyword.intern("init-system-state");
   private static final Keyword KW_INIT_CONTINUOUS_FOCUS =
         Keyword.intern("init-continuous-focus");
   private static final Keyword KW_INIT_WIDTH =
         Keyword.intern("init-width");
   private static final Keyword KW_INIT_HEIGHT =
         Keyword.intern("init-height");
   private static final Keyword KW_BINNING = Keyword.intern("binning");
   private static final Keyword KW_BIT_DEPTH =
         Keyword.intern("bit-depth");
   private static final Keyword KW_PIXEL_SIZE_UM =
         Keyword.intern("pixel-size-um");
   private static final Keyword KW_PIXEL_SIZE_AFFINE =
         Keyword.intern("pixel-size-affine");
   private static final Keyword KW_PIXEL_TYPE =
         Keyword.intern("pixel-type");

   // Clojure keywords for settings
   private static final Keyword KW_CAMERA_TIMEOUT =
         Keyword.intern("camera-timeout");

   // Clojure interop handles
   private static IFn storeMmcoreFn;
   private static Object cljMakeEventFns;
   private static Object cljExecute;
   private static IFn resetBang;
   private static IFn atomFn;

   private final String testName;
   private final HelperExecutionGoldenIO.TestCase testCase;

   public EventExecutionGoldenTest(String testName,
         HelperExecutionGoldenIO.TestCase testCase) {
      this.testName = testName;
      this.testCase = testCase;
   }

   @BeforeClass
   public static void setup() throws Exception {
      IFn require = (IFn) RT.var("clojure.core", "require");
      require.invoke(Symbol.intern("org.micromanager.mm"));
      storeMmcoreFn = (IFn) RT.var("org.micromanager.mm", "store-mmcore");
      storeMmcoreFn.invoke(new HelperRecordingMockCore());
      require.invoke(Symbol.intern("org.micromanager.acq-engine"));
      cljMakeEventFns = RT.var("org.micromanager.acq-engine",
            "make-event-fns");
      cljExecute = RT.var("org.micromanager.acq-engine", "execute");
      resetBang = (IFn) RT.var("clojure.core", "reset!").deref();
      atomFn = (IFn) RT.var("clojure.core", "atom").deref();
   }

   @Parameterized.Parameters(name = "{0}")
   public static Collection<Object[]> data() throws Exception {
      List<Object[]> params = new ArrayList<>();
      ClassLoader cl = EventExecutionGoldenTest.class.getClassLoader();
      URL dirUrl = cl.getResource(GOLDEN_RESOURCE_DIR);
      if (dirUrl == null) {
         return params;
      }
      Path base = new File(dirUrl.toURI()).toPath();
      try (Stream<Path> walk = Files.walk(base)) {
         walk.filter(p -> p.toString().endsWith(".json"))
               .sorted()
               .forEach(p -> {
                  String relative = base.relativize(p).toString()
                        .replace(".json", "");
                  try (InputStream in = Files.newInputStream(p)) {
                     HelperExecutionGoldenIO.TestCase tc =
                           HelperExecutionGoldenIO.readTestCase(in);
                     params.add(new Object[] { relative, tc });
                  } catch (IOException e) {
                     throw new RuntimeException(e);
                  }
               });
      }
      return params;
   }

   // --- Record mode ---

   private boolean isRecordMode() {
      return System.getProperty("record") != null;
   }

   private boolean recordFromJava() {
      return "java".equals(System.getProperty("record.source"));
   }

   private void recordGolden(HelperExecutionGoldenIO.TestCase tc,
         List<HelperRecordingMockCore.MethodCall> calls) throws Exception {
      tc.expectedCalls = HelperExecutionGoldenIO.callLogToJson(calls);
      File outFile = goldenFile();
      outFile.getParentFile().mkdirs();
      HelperExecutionGoldenIO.writeTestCase(tc, outFile);
      System.out.println("Recorded golden file: " + outFile);
   }

   // --- Java test ---

   @Test
   public void testJavaMatchesGolden() throws Exception {
      if (isRecordMode() && !recordFromJava()) {
         return;
      }

      HelperRecordingMockCore.Config coreConfig =
            HelperExecutionGoldenIO.mockCoreConfig(testCase.mockCore);
      HelperRecordingMockCore mockCore =
            new HelperRecordingMockCore(coreConfig);
      InstantClock clock = new InstantClock();
      Jacque2010 engine = new Jacque2010(mockCore, clock);

      HelperExecutionGoldenIO.applyInitialState(
            engine.state, testCase.initialState, coreConfig);

      AcqSettings settings =
            HelperExecutionGoldenIO.settingsFromJson(testCase.settings);

      List<AcqEvent> events = new ArrayList<>();
      if (testCase.events != null) {
         for (HelperGoldenFileIO.EventJson ej : testCase.events) {
            events.add(HelperExecutionGoldenIO.eventFromJson(ej));
         }
      }

      LinkedBlockingQueue<TaggedImage> outQueue =
            new LinkedBlockingQueue<>(100);
      engine.executeEvents(events, settings, outQueue);

      List<HelperRecordingMockCore.MethodCall> calls =
            mockCore.getCallLog();

      if (isRecordMode() && recordFromJava()) {
         recordGolden(testCase, calls);
         return;
      }

      assertNotNull(testName + ": expectedCalls missing from golden file",
            testCase.expectedCalls);
      assertCallsMatch(testCase.expectedCalls, calls);
   }

   // --- Clojure test ---

   @Test
   public void testClojureMatchesGolden() throws Exception {
      if (isRecordMode() && recordFromJava()) {
         return;
      }

      HelperRecordingMockCore.Config coreConfig =
            HelperExecutionGoldenIO.mockCoreConfig(testCase.mockCore);
      HelperRecordingMockCore mockCore =
            new HelperRecordingMockCore(coreConfig);
      storeMmcoreFn.invoke(mockCore);

      // Install fake clock so timelapse waits don't block
      IFn resetBang = (IFn) RT.var("clojure.core", "reset!").deref();
      Object clockAtom =
            RT.var("org.micromanager.acq-engine", "clock").deref();
      InstantClock clojureClock = new InstantClock();
      resetBang.invoke(clockAtom, clojureClock);

      // Reset global atoms
      resetGlobalAtoms();

      // Build Clojure state atom
      IPersistentMap stateMap =
            buildClojureStateMap(testCase.initialState, coreConfig);
      Object stateAtom = atomFn.invoke(stateMap);

      // Convert events to Clojure maps
      List<Object> cljEvents = new ArrayList<>();
      if (testCase.events != null) {
         for (HelperGoldenFileIO.EventJson ej : testCase.events) {
            cljEvents.add(eventToCljMap(ej));
         }
      }

      // Convert settings
      Object cljSettings = toClojureSettings(testCase.settings);

      // Build output queue
      LinkedBlockingQueue<TaggedImage> outQueue =
            new LinkedBlockingQueue<>(100);

      // Clear call log (state building may have caused calls via
      // storeMmcoreFn)
      mockCore.clearCallLog();

      // Bind the dynamic state var and execute
      Var stateVar = RT.var("org.micromanager.acq-engine", "state");
      Var.pushThreadBindings(RT.map(stateVar, stateAtom));
      try {
         // Generate event-fns and execute them, matching the Clojure
         // pattern: (execute (mapcat #(make-event-fns % out-queue
         //                             settings) acq-seq))
         List<Object> allFns = new ArrayList<>();
         for (Object event : cljEvents) {
            Object fns = ((IFn) cljMakeEventFns).invoke(
                  event, outQueue, cljSettings);
            for (Object fn : realizeCljSeq(fns)) {
               allFns.add(fn);
            }
         }
         ((IFn) cljExecute).invoke(
               PersistentVector.create(allFns));
      } finally {
         Var.popThreadBindings();
         resetBang.invoke(clockAtom, null);
      }

      List<HelperRecordingMockCore.MethodCall> calls =
            mockCore.getCallLog();

      if (isRecordMode()) {
         recordGolden(testCase, calls);
         return;
      }

      assertNotNull(testName + ": expectedCalls missing from golden file",
            testCase.expectedCalls);
      assertCallsMatch(testCase.expectedCalls, calls);
   }

   // --- Clojure interop helpers ---

   private static void resetGlobalAtoms() {
      Object activePropSeq = RT.var("org.micromanager.acq-engine",
            "active-property-sequences").deref();
      resetBang.invoke(activePropSeq, null);
      Object activeSliceSeq = RT.var("org.micromanager.acq-engine",
            "active-slice-sequence").deref();
      resetBang.invoke(activeSliceSeq, null);
      // Use sorted-set so pending device iteration order is
      // deterministic (alphabetical), matching the Java engine.
      Object sortedSet = ((IFn) RT.var("clojure.core",
            "sorted-set").deref()).invoke();
      Object pendingDevices = RT.var("org.micromanager.acq-engine",
            "pending-devices").deref();
      resetBang.invoke(pendingDevices, sortedSet);
   }

   private static IPersistentMap buildClojureStateMap(
         HelperExecutionGoldenIO.InitialStateJson isj,
         HelperRecordingMockCore.Config coreConfig) {
      String zDrive = isj != null && isj.defaultZDrive != null
            ? isj.defaultZDrive : coreConfig.focusDevice;
      String xyStage = isj != null && isj.defaultXYStage != null
            ? isj.defaultXYStage : coreConfig.xyStageDevice;
      double refZ = isj != null && isj.referenceZ != null
            ? isj.referenceZ : 0.0;
      boolean initAutoShutter = isj != null && isj.initAutoShutter != null
            ? isj.initAutoShutter : coreConfig.autoShutter;
      double initExposure = isj != null && isj.initExposure != null
            ? isj.initExposure : coreConfig.exposure;
      boolean initShutterState = isj != null && isj.initShutterState != null
            ? isj.initShutterState : coreConfig.shutterOpen;
      boolean initCF = isj != null && isj.initContinuousFocus != null
            ? isj.initContinuousFocus : coreConfig.continuousFocusEnabled;

      // Initial Z position from config
      Double initZ = coreConfig.positions.get(zDrive);
      double zPos = initZ != null ? initZ : 0.0;

      // Initial XY position
      double[] xyPos = coreConfig.xyPositions.get(xyStage);

      // Build last-stage-positions map
      IPersistentMap stagePositions = PersistentHashMap.EMPTY;
      stagePositions = stagePositions.assoc(zDrive, zPos);
      if (xyPos != null) {
         stagePositions = stagePositions.assoc(xyStage,
               PersistentVector.create(xyPos[0], xyPos[1]));
      }

      // Build init-system-state from config properties
      IPersistentMap initSysState = PersistentHashMap.EMPTY;
      if (coreConfig.properties != null) {
         for (Map.Entry<String, Map<String, String>> devEntry
               : coreConfig.properties.entrySet()) {
            for (Map.Entry<String, String> propEntry
                  : devEntry.getValue().entrySet()) {
               List<String> key = new ArrayList<>(2);
               key.add(devEntry.getKey());
               key.add(propEntry.getKey());
               initSysState = initSysState.assoc(
                     PersistentVector.create(key), propEntry.getValue());
            }
         }
      }

      // Binning from properties
      String binning = "1";
      if (coreConfig.properties != null) {
         Map<String, String> camProps =
               coreConfig.properties.get(coreConfig.cameraDevice);
         if (camProps != null && camProps.containsKey("Binning")) {
            binning = camProps.get("Binning");
         }
      }

      // Pixel type
      String pixelType = coreConfig.pixelType;
      if (pixelType == null) {
         int components = (int) coreConfig.numberOfComponents;
         String prefix = components == 1 ? "GRAY"
               : components == 4 ? "RGB" : String.valueOf(components);
         pixelType = prefix + (8 * coreConfig.bytesPerPixel);
      }

      String affine = coreConfig.pixelSizeAffine != null
            ? coreConfig.pixelSizeAffine
            : "1.0;0.0;0.0;0.0;1.0;0.0";

      // Exposure map: {camera-device exposure}
      IPersistentMap exposureMap = PersistentHashMap.EMPTY;
      exposureMap = exposureMap.assoc(
            coreConfig.cameraDevice, coreConfig.exposure);

      return (IPersistentMap) RT.map(
            KW_PAUSE, false,
            KW_STOP, false,
            KW_FINISHED, false,
            KW_LAST_WAKE_TIME, 0L,
            KW_LAST_STAGE_POSITIONS, stagePositions,
            KW_REFERENCE_Z, refZ,
            KW_START_TIME, 0L,
            KW_INIT_AUTO_SHUTTER, initAutoShutter,
            KW_INIT_EXPOSURE, initExposure,
            KW_INIT_SHUTTER_STATE, initShutterState,
            KW_CLJ_EXPOSURE, exposureMap,
            KW_DEFAULT_Z_DRIVE, zDrive,
            KW_DEFAULT_XY_STAGE, xyStage,
            KW_AUTOFOCUS_DEVICE, null,
            KW_POSITION_LIST, buildClojurePositionList(isj),
            KW_INIT_Z_POSITION, zPos,
            KW_INIT_SYSTEM_STATE, initSysState,
            KW_INIT_CONTINUOUS_FOCUS, initCF,
            KW_INIT_WIDTH, coreConfig.imageWidth,
            KW_INIT_HEIGHT, coreConfig.imageHeight,
            KW_BINNING, binning,
            KW_BIT_DEPTH, coreConfig.bitDepth,
            KW_PIXEL_SIZE_UM, coreConfig.pixelSizeUm,
            KW_PIXEL_SIZE_AFFINE, affine,
            KW_PIXEL_TYPE, pixelType);
   }

   private static PositionList buildClojurePositionList(
         HelperExecutionGoldenIO.InitialStateJson isj) {
      if (isj == null || isj.positionList == null) {
         return null;
      }
      return HelperExecutionGoldenIO.buildPositionList(isj.positionList);
   }

   private static Object toClojureSettings(
         HelperExecutionGoldenIO.SettingsJson sj) {
      int timeout = sj != null && sj.cameraTimeout != null
            ? sj.cameraTimeout : 5000;
      return RT.map(KW_CAMERA_TIMEOUT, timeout);
   }

   private static Object eventToCljMap(HelperGoldenFileIO.EventJson ej) {
      Map<Object, Object> m = new LinkedHashMap<>();
      m.put(KW_FRAME_INDEX, ej.frameIndex);
      m.put(KW_SLICE_INDEX, ej.sliceIndex);
      m.put(KW_CHANNEL_INDEX, ej.channelIndex);
      m.put(KW_POSITION_INDEX, ej.positionIndex);
      m.put(KW_CAMERA_CHANNEL_INDEX, ej.cameraChannelIndex);
      m.put(KW_POSITION, ej.position);
      m.put(KW_EXPOSURE, ej.exposure);
      m.put(KW_SLICE, ej.slice);
      m.put(KW_WAIT_TIME_MS, ej.waitTimeMs);
      m.put(KW_AUTOFOCUS, ej.autofocus);
      m.put(KW_NEW_POSITION, ej.newPosition);
      m.put(KW_CLOSE_SHUTTER, ej.closeShutter);
      m.put(KW_RELATIVE_Z, ej.relativeZ);
      if (ej.task != null) {
         m.put(KW_TASK, Keyword.intern(ej.task));
      }
      if (ej.nextFrameIndex != null) {
         m.put(KW_NEXT_FRAME_INDEX, ej.nextFrameIndex);
      }
      if (ej.channelName != null) {
         m.put(KW_CHANNEL, channelToCljMap(ej));
      }
      m.put(KW_BURST_LENGTH, ej.burstLength);
      if (ej.camera != null) {
         m.put(KW_CAMERA, ej.camera);
      }
      if (ej.burstData != null) {
         List<Object> burstList = new ArrayList<>();
         for (HelperGoldenFileIO.EventJson sub : ej.burstData) {
            burstList.add(eventToCljMap(sub));
         }
         m.put(KW_BURST_DATA, PersistentVector.create(burstList));
      }
      if (ej.triggerSequence != null) {
         m.put(KW_TRIGGER_SEQUENCE, triggerSeqToCljMap(
               ej.triggerSequence));
      }
      if (ej.metadata != null) {
         m.put(KW_METADATA, PersistentHashMap.create(ej.metadata));
      }
      return PersistentHashMap.create(m);
   }

   private static Object channelToCljMap(HelperGoldenFileIO.EventJson ej) {
      Map<Object, Object> ch = new LinkedHashMap<>();
      ch.put(KW_NAME, ej.channelName);
      if (ej.channelExposure != null) {
         ch.put(KW_EXPOSURE, ej.channelExposure);
      }
      if (ej.channelZOffset != null) {
         ch.put(KW_Z_OFFSET, ej.channelZOffset);
      }
      if (ej.channelUseZStack != null) {
         ch.put(KW_USE_Z_STACK, ej.channelUseZStack);
      }
      if (ej.channelUseChannel != null) {
         ch.put(KW_USE_CHANNEL, ej.channelUseChannel);
      }
      if (ej.channelSkipFrames != null) {
         ch.put(KW_SKIP_FRAMES, ej.channelSkipFrames);
      }
      ch.put(KW_PROPERTIES, toClojureProperties(
            HelperGoldenFileIO.propsFromJsonPublic(
                  ej.channelProperties)));
      return PersistentHashMap.create(ch);
   }

   private static IPersistentMap toClojureProperties(
         Map<List<String>, String> props) {
      IPersistentMap m = PersistentArrayMap.EMPTY;
      if (props == null) {
         return m;
      }
      List<Map.Entry<List<String>, String>> sorted =
            new ArrayList<>(props.entrySet());
      sorted.sort((a, b) -> {
         List<String> ka = a.getKey(), kb = b.getKey();
         for (int j = 0; j < Math.min(ka.size(), kb.size()); j++) {
            int c = ka.get(j).compareTo(kb.get(j));
            if (c != 0) return c;
         }
         return Integer.compare(ka.size(), kb.size());
      });
      for (Map.Entry<List<String>, String> e : sorted) {
         m = m.assoc(PersistentVector.create(e.getKey()), e.getValue());
      }
      return m;
   }

   private static Object triggerSeqToCljMap(
         HelperGoldenFileIO.TriggerSequenceJson tj) {
      Map<Object, Object> m = new LinkedHashMap<>();
      if (tj.properties != null) {
         IPersistentMap props = PersistentArrayMap.EMPTY;
         for (Map.Entry<String, Map<String, List<String>>> devEntry
               : tj.properties.entrySet()) {
            String device = devEntry.getKey();
            for (Map.Entry<String, List<String>> propEntry
                  : devEntry.getValue().entrySet()) {
               List<String> key = new ArrayList<>(2);
               key.add(device);
               key.add(propEntry.getKey());
               props = props.assoc(PersistentVector.create(key),
                     PersistentVector.create(propEntry.getValue()));
            }
         }
         m.put(KW_PROPERTIES, props);
      }
      if (tj.slices != null) {
         m.put(KW_SLICES, PersistentVector.create(tj.slices));
      }
      return PersistentHashMap.create(m);
   }

   private static List<Object> realizeCljSeq(Object cljResult) {
      List<Object> result = new ArrayList<>();
      Object seq = ((IFn) RT.var("clojure.core", "seq")).invoke(cljResult);
      if (seq == null) {
         return result;
      }
      clojure.lang.ISeq s = (clojure.lang.ISeq) seq;
      while (s != null) {
         result.add(s.first());
         s = s.next();
      }
      return result;
   }

   // --- Assertion ---

   private void assertCallsMatch(
         List<HelperExecutionGoldenIO.MethodCallJson> expected,
         List<HelperRecordingMockCore.MethodCall> actual) {
      int n = Math.min(expected.size(), actual.size());
      for (int i = 0; i < n; i++) {
         HelperExecutionGoldenIO.MethodCallJson exp = expected.get(i);
         HelperRecordingMockCore.MethodCall act = actual.get(i);
         assertEquals(testName + " call[" + i + "] method",
               exp.method, act.method);
         assertArgsEqual(testName + " call[" + i + "]",
               exp.args, act.args);
      }
      assertEquals(testName + ": total call count",
            expected.size(), actual.size());
   }

   private void assertArgsEqual(String context, List<Object> expected,
         List<Object> actual) {
      assertEquals(context + " arg count",
            expected.size(), actual.size());
      for (int i = 0; i < expected.size(); i++) {
         Object exp = expected.get(i);
         Object act = actual.get(i);
         if (exp instanceof Number && act instanceof Number) {
            assertEquals(context + " arg[" + i + "]",
                  ((Number) exp).doubleValue(),
                  ((Number) act).doubleValue(), 0.0001);
         } else {
            assertEquals(context + " arg[" + i + "]", exp, act);
         }
      }
   }

   private File goldenFile() {
      URL dirUrl = getClass().getClassLoader().getResource(
            GOLDEN_RESOURCE_DIR);
      File testBuildDir;
      try {
         testBuildDir = new File(dirUrl.toURI());
      } catch (java.net.URISyntaxException e) {
         throw new RuntimeException(e);
      }
      File projectDir = testBuildDir;
      String[] parts = GOLDEN_RESOURCE_DIR.split("/");
      for (int i = 0; i < parts.length; i++) {
         projectDir = projectDir.getParentFile();
      }
      projectDir = projectDir.getParentFile();
      return new File(projectDir,
            "src/test/resources/" + GOLDEN_RESOURCE_DIR + "/"
            + testName + ".json");
   }
}

package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import clojure.lang.Symbol;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SequenceGeneratorGoldenTest {

   private static final String GOLDEN_RESOURCE_DIR =
         "org/micromanager/internal/jacque/golden";

   // Clojure keywords
   private static final Keyword KW_NUM_FRAMES =
         Keyword.intern("numFrames");
   private static final Keyword KW_FRAMES = Keyword.intern("frames");
   private static final Keyword KW_POSITIONS =
         Keyword.intern("positions");
   private static final Keyword KW_CHANNELS =
         Keyword.intern("channels");
   private static final Keyword KW_SLICES = Keyword.intern("slices");
   private static final Keyword KW_SLICES_FIRST =
         Keyword.intern("slices-first");
   private static final Keyword KW_TIME_FIRST =
         Keyword.intern("time-first");
   private static final Keyword KW_KEEP_SHUTTER_OPEN_SLICES =
         Keyword.intern("keep-shutter-open-slices");
   private static final Keyword KW_KEEP_SHUTTER_OPEN_CHANNELS =
         Keyword.intern("keep-shutter-open-channels");
   private static final Keyword KW_USE_AUTOFOCUS =
         Keyword.intern("use-autofocus");
   private static final Keyword KW_AUTOFOCUS_SKIP =
         Keyword.intern("autofocus-skip");
   private static final Keyword KW_RELATIVE_SLICES =
         Keyword.intern("relative-slices");
   private static final Keyword KW_INTERVAL_MS =
         Keyword.intern("interval-ms");
   private static final Keyword KW_DEFAULT_EXPOSURE =
         Keyword.intern("default-exposure");
   private static final Keyword KW_CUSTOM_INTERVALS_MS =
         Keyword.intern("custom-intervals-ms");

   private static final Keyword KW_NAME = Keyword.intern("name");
   private static final Keyword KW_EXPOSURE =
         Keyword.intern("exposure");
   private static final Keyword KW_Z_OFFSET =
         Keyword.intern("z-offset");
   private static final Keyword KW_USE_Z_STACK =
         Keyword.intern("use-z-stack");
   private static final Keyword KW_SKIP_FRAMES =
         Keyword.intern("skip-frames");
   private static final Keyword KW_USE_CHANNEL =
         Keyword.intern("use-channel");
   private static final Keyword KW_COLOR = Keyword.intern("color");
   private static final Keyword KW_PROPERTIES =
         Keyword.intern("properties");

   private static final Keyword KW_FRAME_INDEX =
         Keyword.intern("frame-index");
   private static final Keyword KW_SLICE_INDEX =
         Keyword.intern("slice-index");
   private static final Keyword KW_CHANNEL_INDEX =
         Keyword.intern("channel-index");
   private static final Keyword KW_POSITION_INDEX =
         Keyword.intern("position-index");
   private static final Keyword KW_POSITION =
         Keyword.intern("position");
   private static final Keyword KW_SLICE = Keyword.intern("slice");
   private static final Keyword KW_WAIT_TIME_MS =
         Keyword.intern("wait-time-ms");
   private static final Keyword KW_AUTOFOCUS =
         Keyword.intern("autofocus");
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
   private static final Keyword KW_METADATA =
         Keyword.intern("metadata");
   private static final Keyword KW_CAMERA_CHANNEL_INDEX =
         Keyword.intern("camera-channel-index");
   private static final Keyword KW_CAMERA = Keyword.intern("camera");
   private static final Keyword KW_FRAME = Keyword.intern("frame");
   private static final Keyword KW_RUNNABLES =
         Keyword.intern("runnables");

   private static final Set<Keyword> ALLOWED_EVENT_KEYS = Set.of(
         KW_FRAME_INDEX, KW_SLICE_INDEX, KW_CHANNEL_INDEX,
         KW_POSITION_INDEX, KW_POSITION, KW_EXPOSURE, KW_SLICE,
         KW_WAIT_TIME_MS, KW_AUTOFOCUS, KW_NEW_POSITION,
         KW_CLOSE_SHUTTER, KW_RELATIVE_Z, KW_TASK,
         KW_NEXT_FRAME_INDEX, KW_CHANNEL, KW_BURST_LENGTH,
         KW_CAMERA_CHANNEL_INDEX, KW_CAMERA, KW_BURST_DATA,
         KW_TRIGGER_SEQUENCE, KW_METADATA, KW_FRAME, KW_RUNNABLES);

   private static final Set<Keyword> ALLOWED_BURST_EVENT_KEYS = Set.of(
         KW_FRAME_INDEX, KW_SLICE_INDEX, KW_CHANNEL_INDEX,
         KW_POSITION_INDEX, KW_POSITION, KW_EXPOSURE, KW_SLICE,
         KW_WAIT_TIME_MS, KW_AUTOFOCUS, KW_NEW_POSITION,
         KW_CLOSE_SHUTTER, KW_RELATIVE_Z, KW_NEXT_FRAME_INDEX,
         KW_CHANNEL, KW_CAMERA_CHANNEL_INDEX, KW_CAMERA,
         KW_METADATA, KW_FRAME, KW_RUNNABLES);

   private static final Set<Keyword> ALLOWED_CHANNEL_KEYS = Set.of(
         KW_NAME, KW_EXPOSURE, KW_Z_OFFSET, KW_USE_Z_STACK,
         KW_SKIP_FRAMES, KW_USE_CHANNEL, KW_COLOR, KW_PROPERTIES);

   private static final Set<Keyword> ALLOWED_TRIGGER_SEQ_KEYS = Set.of(
         KW_PROPERTIES, KW_SLICES);

   private static Object cljGenerate;
   private static clojure.lang.IFn storeMmcoreFn;

   private final String testName;
   private final String fileName;

   public SequenceGeneratorGoldenTest(String testName, String fileName) {
      this.testName = testName;
      this.fileName = fileName;
   }

   @Parameterized.Parameters(name = "{0}")
   public static Collection<Object[]> data() {
      URL dirUrl = SequenceGeneratorGoldenTest.class.getClassLoader()
            .getResource(GOLDEN_RESOURCE_DIR);
      assertNotNull("Golden resource directory not found: "
            + GOLDEN_RESOURCE_DIR, dirUrl);
      Path goldenDir = new File(dirUrl.getFile()).toPath();

      List<Object[]> params = new ArrayList<>();
      try (Stream<Path> walk = Files.walk(goldenDir)) {
         walk.filter(p -> p.toString().endsWith(".json"))
               .forEach(p -> {
                  String rel = goldenDir.relativize(p).toString()
                        .replace(File.separatorChar, '/');
                  String name = rel.replaceFirst("\\.json$", "");
                  params.add(new Object[] { name, rel });
               });
      } catch (IOException e) {
         fail("Failed to walk golden directory: " + e.getMessage());
      }
      params.sort((a, b) -> ((String) a[0]).compareTo((String) b[0]));
      return params;
   }

   @BeforeClass
   public static void setup() throws Exception {
      Object require = RT.var("clojure.core", "require");
      ((clojure.lang.IFn) require).invoke(
            Symbol.intern("org.micromanager.mm"));
      storeMmcoreFn = (clojure.lang.IFn) RT.var(
            "org.micromanager.mm", "store-mmcore");
      storeMmcoreFn.invoke(new HelperMockCore());
      ((clojure.lang.IFn) require).invoke(
            Symbol.intern("org.micromanager.sequence-generator"));
      cljGenerate = RT.var("org.micromanager.sequence-generator",
            "generate-acq-sequence");
   }

   private HelperGoldenFileIO.TestCase loadTestCase() throws IOException {
      String resourcePath = GOLDEN_RESOURCE_DIR + "/" + fileName;
      InputStream in = getClass().getClassLoader()
            .getResourceAsStream(resourcePath);
      assertNotNull("Cannot load resource: " + resourcePath, in);
      return HelperGoldenFileIO.readTestCase(in);
   }

   private File goldenSourceFile() {
      // Resolve the source tree path for writing in record mode.
      // We walk up from the test-build output to find the project root.
      URL dirUrl = getClass().getClassLoader()
            .getResource(GOLDEN_RESOURCE_DIR);
      File testBuildDir = new File(dirUrl.getFile());
      // testBuildDir = <project>/test-build/org/.../golden
      // project root = testBuildDir / ../../../../..
      File projectDir = testBuildDir;
      String[] parts = GOLDEN_RESOURCE_DIR.split("/");
      for (int i = 0; i < parts.length; i++) {
         projectDir = projectDir.getParentFile();
      }
      // projectDir is now test-build/; go up one more for the project root
      projectDir = projectDir.getParentFile();
      return new File(projectDir,
            "src/test/resources/" + GOLDEN_RESOURCE_DIR + "/" + fileName);
   }

   // --- Record mode ---

   private boolean isRecordMode() {
      return System.getProperty("record") != null;
   }

   private boolean recordFromJava() {
      return "java".equals(System.getProperty("record.source"));
   }

   private void recordGolden(HelperGoldenFileIO.TestCase tc,
         List<HelperGoldenFileIO.EventJson> events) throws Exception {
      tc.expectedEvents = events;
      File outFile = goldenSourceFile();
      outFile.getParentFile().mkdirs();
      HelperGoldenFileIO.writeTestCase(tc, outFile);
      System.out.println("Recorded golden file: " + outFile);
   }

   // --- Java test ---

   @Test
   public void testJavaMatchesGolden() throws Exception {
      HelperGoldenFileIO.TestCase tc = loadTestCase();
      AcqSettings settings =
            HelperGoldenFileIO.settingsFromJson(tc.settings);
      HelperMockCore core =
            HelperGoldenFileIO.mockCoreFromJson(tc.mockCore);
      List<SequenceGenerator.AttachedRunnable> runnables =
            toJavaRunnables(tc.runnables);
      List<AcqEvent> javaResult = SequenceGenerator
            .generateAcqSequence(settings, runnables, core).toList();

      if (isRecordMode()) {
         if (recordFromJava()) {
            List<HelperGoldenFileIO.EventJson> events = new ArrayList<>();
            for (AcqEvent e : javaResult) {
               events.add(HelperGoldenFileIO.eventToJson(e));
            }
            recordGolden(tc, events);
         }
         return;
      }

      if (tc.expectedEvents == null) {
         fail(testName + ": expectedEvents is null. "
               + "Run with -Drecord=true to generate golden data.");
      }

      assertEquals(testName + ": event count",
            tc.expectedEvents.size(), javaResult.size());
      for (int i = 0; i < tc.expectedEvents.size(); i++) {
         HelperGoldenFileIO.assertEventEquals(
               tc.expectedEvents.get(i), javaResult.get(i), i);
      }
   }

   // --- Clojure test ---

   @Test
   public void testClojureMatchesGolden() throws Exception {
      HelperGoldenFileIO.TestCase tc = loadTestCase();
      AcqSettings settings =
            HelperGoldenFileIO.settingsFromJson(tc.settings);

      HelperMockCore core =
            HelperGoldenFileIO.mockCoreFromJson(tc.mockCore);
      storeMmcoreFn.invoke(core);

      Object cljSettings = toClojureSettings(settings);
      Object cljRunnables = toCljRunnables(tc.runnables);
      Object cljResult = ((clojure.lang.IFn) cljGenerate)
            .invoke(cljSettings, cljRunnables);
      List<AcqEvent> cljEvents = cljSeqToAcqEvents(cljResult);

      if (isRecordMode()) {
         if (!recordFromJava()) {
            List<HelperGoldenFileIO.EventJson> events = new ArrayList<>();
            for (AcqEvent e : cljEvents) {
               events.add(HelperGoldenFileIO.eventToJson(e));
            }
            recordGolden(tc, events);
         }
         return;
      }

      if (tc.expectedEvents == null) {
         fail(testName + ": expectedEvents is null. "
               + "Run with -Drecord=true to generate golden data.");
      }

      assertEquals(testName + ": event count",
            tc.expectedEvents.size(), cljEvents.size());
      for (int i = 0; i < tc.expectedEvents.size(); i++) {
         HelperGoldenFileIO.assertEventEquals(
               tc.expectedEvents.get(i), cljEvents.get(i), i);
      }
   }

   // --- Clojure interop ---

   private static IPersistentMap toClojureProperties(
         Map<List<String>, String> props) {
      IPersistentMap m = PersistentArrayMap.EMPTY;
      for (Map.Entry<List<String>, String> e : props.entrySet()) {
         m = m.assoc(PersistentVector.create(e.getKey()), e.getValue());
      }
      return m;
   }

   private static Object toClojureChannel(AcqChannel ch) {
      return RT.map(
            KW_NAME, ch.name,
            KW_EXPOSURE, ch.exposure,
            KW_Z_OFFSET, ch.zOffset,
            KW_USE_Z_STACK, ch.useZStack,
            KW_SKIP_FRAMES, ch.skipFrames,
            KW_USE_CHANNEL, ch.useChannel,
            KW_COLOR, ch.color,
            KW_PROPERTIES, toClojureProperties(ch.properties));
   }

   private static Object toClojureSettings(AcqSettings s) {
      List<Object> cljChannels = new ArrayList<>();
      for (AcqChannel ch : s.channels) {
         cljChannels.add(toClojureChannel(ch));
      }
      Object frames = s.frames != null
            ? PersistentVector.create(s.frames) : null;
      Object slices = s.slices != null
            ? PersistentVector.create(s.slices) : null;
      Object positions = s.positions != null
            ? PersistentVector.create(s.positions) : null;
      Object channels = PersistentVector.create(cljChannels);
      Object customIntervals = (s.customIntervalsMs != null
            && !s.customIntervalsMs.isEmpty())
            ? PersistentVector.create(s.customIntervalsMs) : null;
      return RT.map(
            KW_NUM_FRAMES, s.numFrames,
            KW_FRAMES, frames,
            KW_POSITIONS, positions,
            KW_CHANNELS, channels,
            KW_SLICES, slices,
            KW_SLICES_FIRST, s.slicesFirst,
            KW_TIME_FIRST, s.timeFirst,
            KW_KEEP_SHUTTER_OPEN_SLICES, s.keepShutterOpenSlices,
            KW_KEEP_SHUTTER_OPEN_CHANNELS, s.keepShutterOpenChannels,
            KW_USE_AUTOFOCUS, s.useAutofocus,
            KW_AUTOFOCUS_SKIP, s.autofocusSkip,
            KW_RELATIVE_SLICES, s.relativeSlices,
            KW_INTERVAL_MS, s.intervalMs,
            KW_DEFAULT_EXPOSURE, s.defaultExposure,
            KW_CUSTOM_INTERVALS_MS, customIntervals);
   }

   // --- Runnables conversion ---

   private static final Runnable NOOP = () -> { };

   private static List<SequenceGenerator.AttachedRunnable>
         toJavaRunnables(
               List<HelperGoldenFileIO.RunnableSpecJson> specs) {
      if (specs == null || specs.isEmpty()) {
         return null;
      }
      List<SequenceGenerator.AttachedRunnable> result =
            new ArrayList<>(specs.size());
      for (HelperGoldenFileIO.RunnableSpecJson s : specs) {
         result.add(new SequenceGenerator.AttachedRunnable(
               s.frameIndex, s.positionIndex,
               s.channelIndex, s.sliceIndex, NOOP));
      }
      return result;
   }

   private static Object toCljRunnables(
         List<HelperGoldenFileIO.RunnableSpecJson> specs) {
      if (specs == null || specs.isEmpty()) {
         return null;
      }
      List<Object> pairs = new ArrayList<>();
      for (HelperGoldenFileIO.RunnableSpecJson s : specs) {
         IPersistentMap template = PersistentArrayMap.EMPTY;
         if (s.frameIndex >= 0) {
            template = template.assoc(KW_FRAME_INDEX, s.frameIndex);
         }
         if (s.positionIndex >= 0) {
            template = template.assoc(KW_POSITION_INDEX,
                  s.positionIndex);
         }
         if (s.channelIndex >= 0) {
            template = template.assoc(KW_CHANNEL_INDEX,
                  s.channelIndex);
         }
         if (s.sliceIndex >= 0) {
            template = template.assoc(KW_SLICE_INDEX, s.sliceIndex);
         }
         pairs.add(PersistentVector.create(template, NOOP));
      }
      return PersistentVector.create(pairs);
   }

   private static List<AcqEvent> cljSeqToAcqEvents(Object cljResult) {
      List<Object> raw = realizeCljSeq(cljResult);
      List<AcqEvent> result = new ArrayList<>(raw.size());
      for (int i = 0; i < raw.size(); i++) {
         result.add(cljEventToAcqEvent((IPersistentMap) raw.get(i),
               ALLOWED_EVENT_KEYS, "event[" + i + "]"));
      }
      return result;
   }

   private static List<Object> realizeCljSeq(Object cljResult) {
      List<Object> result = new ArrayList<>();
      Object seq = ((clojure.lang.IFn) RT.var("clojure.core", "seq"))
            .invoke(cljResult);
      if (seq == null) {
         return result;
      }
      ISeq s = (ISeq) seq;
      while (s != null) {
         result.add(s.first());
         s = s.next();
      }
      return result;
   }

   @SuppressWarnings("unchecked")
   private static void assertNoUnexpectedKeys(IPersistentMap m,
         Set<Keyword> allowed, String context) {
      for (Object obj : (Iterable<?>) m) {
         Object key = ((Map.Entry<?, ?>) obj).getKey();
         if (!allowed.contains(key)) {
            fail(context + ": unexpected key " + key);
         }
      }
   }

   private static AcqEvent cljEventToAcqEvent(IPersistentMap m,
         Set<Keyword> allowedKeys, String context) {
      assertNoUnexpectedKeys(m, allowedKeys, context);
      AcqEvent e = new AcqEvent();
      e.frameIndex = intVal(m.valAt(KW_FRAME_INDEX));
      e.sliceIndex = intVal(m.valAt(KW_SLICE_INDEX));
      e.channelIndex = intVal(m.valAt(KW_CHANNEL_INDEX));
      e.positionIndex = intVal(m.valAt(KW_POSITION_INDEX));
      e.position = intVal(m.valAt(KW_POSITION));
      e.exposure = ((Number) m.valAt(KW_EXPOSURE)).doubleValue();
      e.slice = doubleOrNull(m.valAt(KW_SLICE));
      e.waitTimeMs = doubleOrNull(m.valAt(KW_WAIT_TIME_MS));
      e.autofocus = Boolean.TRUE.equals(m.valAt(KW_AUTOFOCUS));
      e.newPosition = Boolean.TRUE.equals(m.valAt(KW_NEW_POSITION));
      e.closeShutter = Boolean.TRUE.equals(m.valAt(KW_CLOSE_SHUTTER));
      e.relativeZ = Boolean.TRUE.equals(m.valAt(KW_RELATIVE_Z));
      Object cljTask = m.valAt(KW_TASK);
      e.task = cljTask instanceof Keyword
            ? ((Keyword) cljTask).getName() : null;
      Object cljNext = m.valAt(KW_NEXT_FRAME_INDEX);
      e.nextFrameIndex = cljNext != null
            ? Integer.valueOf(intVal(cljNext)) : null;
      Object cljChannel = m.valAt(KW_CHANNEL);
      if (cljChannel instanceof IPersistentMap) {
         IPersistentMap cljChMap = (IPersistentMap) cljChannel;
         assertNoUnexpectedKeys(cljChMap, ALLOWED_CHANNEL_KEYS,
               context + ".channel");
         AcqChannel ch = new AcqChannel();
         ch.name = (String) cljChMap.valAt(KW_NAME);
         Object cljProps = cljChMap.valAt(KW_PROPERTIES);
         if (cljProps instanceof IPersistentMap) {
            ch.properties = cljPropsToJavaProps((IPersistentMap) cljProps);
         }
         Object cljExp = cljChMap.valAt(KW_EXPOSURE);
         if (cljExp instanceof Number) {
            ch.exposure = ((Number) cljExp).doubleValue();
         }
         Object cljZOff = cljChMap.valAt(KW_Z_OFFSET);
         if (cljZOff instanceof Number) {
            ch.zOffset = ((Number) cljZOff).doubleValue();
         }
         ch.useZStack = Boolean.TRUE.equals(
               cljChMap.valAt(KW_USE_Z_STACK));
         ch.useChannel = Boolean.TRUE.equals(
               cljChMap.valAt(KW_USE_CHANNEL));
         Object cljSkip = cljChMap.valAt(KW_SKIP_FRAMES);
         if (cljSkip instanceof Number) {
            ch.skipFrames = ((Number) cljSkip).intValue();
         }
         Object cljColor = cljChMap.valAt(KW_COLOR);
         if (cljColor instanceof java.awt.Color) {
            ch.color = (java.awt.Color) cljColor;
         }
         e.channel = ch;
      }
      Object cljBurst = m.valAt(KW_BURST_LENGTH);
      if (cljBurst != null) {
         e.burstLength = intVal(cljBurst);
      }
      Object cljCamIdx = m.valAt(KW_CAMERA_CHANNEL_INDEX);
      if (cljCamIdx != null) {
         e.cameraChannelIndex = intVal(cljCamIdx);
      }
      Object cljCamera = m.valAt(KW_CAMERA);
      if (cljCamera instanceof String) {
         e.camera = (String) cljCamera;
      }
      Object cljBurstData = m.valAt(KW_BURST_DATA);
      if (cljBurstData != null) {
         List<Object> rawBurst = realizeCljSeq(cljBurstData);
         e.burstData = new ArrayList<>(rawBurst.size());
         for (int j = 0; j < rawBurst.size(); j++) {
            e.burstData.add(cljEventToAcqEvent(
                  (IPersistentMap) rawBurst.get(j),
                  ALLOWED_BURST_EVENT_KEYS,
                  context + ".burst[" + j + "]"));
         }
      }
      Object cljTrigSeq = m.valAt(KW_TRIGGER_SEQUENCE);
      if (cljTrigSeq instanceof IPersistentMap) {
         assertNoUnexpectedKeys((IPersistentMap) cljTrigSeq,
               ALLOWED_TRIGGER_SEQ_KEYS,
               context + ".trigger-sequence");
         e.triggerSequence = cljTriggerSeqToJava(
               (IPersistentMap) cljTrigSeq);
      }
      Object cljMeta = m.valAt(KW_METADATA);
      if (cljMeta instanceof IPersistentMap) {
         e.metadata = cljStringMapToJava((IPersistentMap) cljMeta);
      }
      Object cljRunnables = m.valAt(KW_RUNNABLES);
      if (cljRunnables != null) {
         List<Object> runnableItems = realizeCljSeq(cljRunnables);
         if (!runnableItems.isEmpty()) {
            e.runnables = new ArrayList<>();
            for (Object ignored : runnableItems) {
               e.runnables.add(() -> { });
            }
         }
      }
      return e;
   }

   @SuppressWarnings("unchecked")
   private static Map<List<String>, String> cljPropsToJavaProps(
         IPersistentMap cljProps) {
      Map<List<String>, String> result = new LinkedHashMap<>();
      for (Object obj : (Iterable<?>) cljProps) {
         Map.Entry<List<String>, String> entry =
               (Map.Entry<List<String>, String>) obj;
         result.put(entry.getKey(), entry.getValue());
      }
      return result;
   }

   @SuppressWarnings("unchecked")
   private static TriggerSequence cljTriggerSeqToJava(
         IPersistentMap cljTrigSeq) {
      TriggerSequence ts = new TriggerSequence();
      Object cljTrigProps = cljTrigSeq.valAt(KW_PROPERTIES);
      if (cljTrigProps instanceof IPersistentMap) {
         ts.properties = new LinkedHashMap<>();
         for (Object obj : (Iterable<?>) (IPersistentMap) cljTrigProps) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) obj;
            List<?> key = (List<?>) entry.getKey();
            String device = (String) key.get(0);
            String prop = (String) key.get(1);
            List<String> values = new ArrayList<>();
            for (Object v : realizeCljSeq(entry.getValue())) {
               values.add((String) v);
            }
            List<String> dpKey = new ArrayList<>(2);
            dpKey.add(device);
            dpKey.add(prop);
            ts.properties.put(dpKey, values);
         }
      }
      Object cljSlices = cljTrigSeq.valAt(KW_SLICES);
      if (cljSlices != null) {
         ts.slices = new ArrayList<>();
         for (Object s : realizeCljSeq(cljSlices)) {
            ts.slices.add(((Number) s).doubleValue());
         }
      }
      return ts;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, String> cljStringMapToJava(
         IPersistentMap cljMap) {
      Map<String, String> result = new LinkedHashMap<>();
      for (Object obj : (Iterable<?>) cljMap) {
         Map.Entry<String, String> entry =
               (Map.Entry<String, String>) obj;
         result.put(entry.getKey(), entry.getValue());
      }
      return result;
   }

   private static int intVal(Object o) {
      return o != null ? ((Number) o).intValue() : 0;
   }

   private static Double doubleOrNull(Object o) {
      if (o instanceof Number) {
         return ((Number) o).doubleValue();
      }
      return null;
   }
}

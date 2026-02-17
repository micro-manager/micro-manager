package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class HelperGoldenFileIO {

   // --- DTO classes matching JSON schema ---

   static class MockCoreJson {
      String focusDevice;
      Boolean stageSequenceable;
      Integer stageSequenceMaxLength;
      Map<String, Map<String, PropertySeqJson>> propertySequencing;
   }

   static class PropertySeqJson {
      Boolean sequenceable;
      Integer maxLength;
   }

   static class RunnableSpecJson {
      int frameIndex;
      int positionIndex;
      int channelIndex;
      int sliceIndex;
   }

   static class TestCase {
      String description;
      SettingsJson settings;
      MockCoreJson mockCore;
      List<RunnableSpecJson> runnables;
      List<EventJson> expectedEvents;
   }

   static class SettingsJson {
      int numFrames;
      List<Integer> frames;
      List<Double> slices;
      List<ChannelJson> channels;
      List<Integer> positions;
      boolean slicesFirst;
      boolean timeFirst;
      boolean keepShutterOpenSlices;
      boolean keepShutterOpenChannels;
      boolean useAutofocus;
      int autofocusSkip;
      boolean relativeSlices;
      double intervalMs;
      double defaultExposure;
      List<Double> customIntervalsMs;
   }

   static class ChannelJson {
      String name;
      double exposure;
      double zOffset;
      boolean useZStack;
      boolean useChannel;
      int skipFrames;
      String color;
      Map<String, Map<String, String>> properties;
   }

   static class TriggerSequenceJson {
      Map<String, Map<String, List<String>>> properties;
      List<Double> slices;
   }

   static class EventJson {
      int frameIndex;
      int sliceIndex;
      int channelIndex;
      int positionIndex;
      int cameraChannelIndex;
      int position;
      double exposure;
      Double slice;
      Double waitTimeMs;
      boolean autofocus;
      boolean newPosition;
      boolean closeShutter;
      boolean relativeZ;
      String task;
      Integer nextFrameIndex;
      String channelName;
      int burstLength;
      String camera;
      Map<String, Map<String, String>> channelProperties;
      Double channelExposure;
      Double channelZOffset;
      Boolean channelUseZStack;
      Boolean channelUseChannel;
      Integer channelSkipFrames;
      String channelColor;
      List<EventJson> burstData;
      TriggerSequenceJson triggerSequence;
      Map<String, String> metadata;
      Integer runnableCount;
   }

   // --- Gson instance ---

   private static final Gson GSON = new GsonBuilder()
         .serializeNulls()
         .setPrettyPrinting()
         .create();

   // --- Read/write ---

   static TestCase readTestCase(InputStream in) throws IOException {
      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
         return GSON.fromJson(reader, TestCase.class);
      }
   }

   static void writeTestCase(TestCase tc, File file) throws IOException {
      try (OutputStreamWriter w = new OutputStreamWriter(
            new FileOutputStream(file), StandardCharsets.UTF_8)) {
         GSON.toJson(tc, w);
         w.write('\n');
      }
   }

   // --- Conversion: JSON DTO -> domain objects ---

   static AcqSettings settingsFromJson(SettingsJson sj) {
      AcqSettings s = new AcqSettings();
      s.numFrames = sj.numFrames;
      s.frames = sj.frames;
      s.slices = sj.slices;
      s.channels = new ArrayList<>();
      if (sj.channels != null) {
         for (ChannelJson cj : sj.channels) {
            s.channels.add(channelFromJson(cj));
         }
      }
      s.positions = sj.positions;
      s.slicesFirst = sj.slicesFirst;
      s.timeFirst = sj.timeFirst;
      s.keepShutterOpenSlices = sj.keepShutterOpenSlices;
      s.keepShutterOpenChannels = sj.keepShutterOpenChannels;
      s.useAutofocus = sj.useAutofocus;
      s.autofocusSkip = sj.autofocusSkip;
      s.relativeSlices = sj.relativeSlices;
      s.intervalMs = sj.intervalMs;
      s.defaultExposure = sj.defaultExposure;
      s.customIntervalsMs = sj.customIntervalsMs != null
            ? sj.customIntervalsMs : new ArrayList<>();
      return s;
   }

   private static AcqChannel channelFromJson(ChannelJson cj) {
      AcqChannel ch = new AcqChannel();
      ch.name = cj.name;
      ch.exposure = cj.exposure;
      ch.zOffset = cj.zOffset;
      ch.useZStack = cj.useZStack;
      ch.useChannel = cj.useChannel;
      ch.skipFrames = cj.skipFrames;
      ch.color = parseColor(cj.color);
      ch.properties = propsFromJson(cj.properties);
      return ch;
   }

   private static Color parseColor(String hex) {
      if (hex == null) {
         return null;
      }
      return Color.decode(hex);
   }

   private static Map<List<String>, String> propsFromJson(
         Map<String, Map<String, String>> json) {
      if (json == null || json.isEmpty()) {
         return Collections.emptyMap();
      }
      Map<List<String>, String> result = new LinkedHashMap<>();
      for (Map.Entry<String, Map<String, String>> deviceEntry
            : json.entrySet()) {
         String device = deviceEntry.getKey();
         for (Map.Entry<String, String> propEntry
               : deviceEntry.getValue().entrySet()) {
            List<String> key = new ArrayList<>(2);
            key.add(device);
            key.add(propEntry.getKey());
            result.put(Collections.unmodifiableList(key),
                  propEntry.getValue());
         }
      }
      return result;
   }

   // --- Conversion: domain objects -> JSON DTO ---

   static SettingsJson settingsToJson(AcqSettings s) {
      SettingsJson sj = new SettingsJson();
      sj.numFrames = s.numFrames;
      sj.frames = s.frames;
      sj.slices = s.slices;
      sj.channels = new ArrayList<>();
      if (s.channels != null) {
         for (AcqChannel ch : s.channels) {
            sj.channels.add(channelToJson(ch));
         }
      }
      sj.positions = s.positions;
      sj.slicesFirst = s.slicesFirst;
      sj.timeFirst = s.timeFirst;
      sj.keepShutterOpenSlices = s.keepShutterOpenSlices;
      sj.keepShutterOpenChannels = s.keepShutterOpenChannels;
      sj.useAutofocus = s.useAutofocus;
      sj.autofocusSkip = s.autofocusSkip;
      sj.relativeSlices = s.relativeSlices;
      sj.intervalMs = s.intervalMs;
      sj.defaultExposure = s.defaultExposure;
      sj.customIntervalsMs = s.customIntervalsMs;
      return sj;
   }

   private static ChannelJson channelToJson(AcqChannel ch) {
      ChannelJson cj = new ChannelJson();
      cj.name = ch.name;
      cj.exposure = ch.exposure;
      cj.zOffset = ch.zOffset;
      cj.useZStack = ch.useZStack;
      cj.useChannel = ch.useChannel;
      cj.skipFrames = ch.skipFrames;
      cj.color = formatColor(ch.color);
      cj.properties = propsToJson(ch.properties);
      return cj;
   }

   private static String formatColor(Color c) {
      if (c == null) {
         return null;
      }
      return String.format("#%02x%02x%02x",
            c.getRed(), c.getGreen(), c.getBlue());
   }

   private static Map<String, Map<String, String>> propsToJson(
         Map<List<String>, String> props) {
      Map<String, Map<String, String>> result = new TreeMap<>();
      if (props == null) {
         return result;
      }
      for (Map.Entry<List<String>, String> entry : props.entrySet()) {
         String device = entry.getKey().get(0);
         String prop = entry.getKey().get(1);
         result.computeIfAbsent(device, k -> new TreeMap<>())
               .put(prop, entry.getValue());
      }
      return result;
   }

   private static TriggerSequenceJson triggerSeqToJson(
         TriggerSequence ts) {
      if (ts == null) {
         return null;
      }
      TriggerSequenceJson tj = new TriggerSequenceJson();
      tj.properties = new TreeMap<>();
      if (ts.properties != null) {
         for (Map.Entry<List<String>, List<String>> entry
               : ts.properties.entrySet()) {
            String device = entry.getKey().get(0);
            String prop = entry.getKey().get(1);
            tj.properties.computeIfAbsent(device, k -> new TreeMap<>())
                  .put(prop, entry.getValue());
         }
      }
      tj.slices = ts.slices;
      return tj;
   }

   static EventJson eventToJson(AcqEvent e) {
      EventJson ej = new EventJson();
      ej.frameIndex = e.frameIndex;
      ej.sliceIndex = e.sliceIndex;
      ej.channelIndex = e.channelIndex;
      ej.positionIndex = e.positionIndex;
      ej.cameraChannelIndex = e.cameraChannelIndex;
      ej.position = e.position;
      ej.exposure = e.exposure;
      ej.slice = e.slice;
      ej.waitTimeMs = e.waitTimeMs;
      ej.autofocus = e.autofocus;
      ej.newPosition = e.newPosition;
      ej.closeShutter = e.closeShutter;
      ej.relativeZ = e.relativeZ;
      ej.task = e.task;
      ej.nextFrameIndex = e.nextFrameIndex;
      if (e.channel != null) {
         ej.channelName = e.channel.name;
         ej.channelProperties = propsToJson(e.channel.properties);
         ej.channelExposure = e.channel.exposure;
         ej.channelZOffset = e.channel.zOffset;
         ej.channelUseZStack = e.channel.useZStack;
         ej.channelUseChannel = e.channel.useChannel;
         ej.channelSkipFrames = e.channel.skipFrames;
         ej.channelColor = formatColor(e.channel.color);
      }
      ej.burstLength = e.burstLength;
      ej.camera = e.camera;
      if (e.burstData != null) {
         ej.burstData = new ArrayList<>();
         for (AcqEvent sub : e.burstData) {
            ej.burstData.add(eventToJson(sub));
         }
      }
      ej.triggerSequence = triggerSeqToJson(e.triggerSequence);
      ej.metadata = e.metadata;
      if (e.runnables != null && !e.runnables.isEmpty()) {
         ej.runnableCount = e.runnables.size();
      }
      return ej;
   }

   // --- Assertion ---

   static void assertEventEquals(EventJson expected, AcqEvent actual,
         int idx) {
      assertEventEquals("", expected, actual, idx);
   }

   private static void assertEventEquals(String prefix,
         EventJson expected, AcqEvent actual, int idx) {
      String at = prefix + "Event[" + idx + "] ";
      assertEquals(at + "frameIndex",
            expected.frameIndex, actual.frameIndex);
      assertEquals(at + "sliceIndex",
            expected.sliceIndex, actual.sliceIndex);
      assertEquals(at + "channelIndex",
            expected.channelIndex, actual.channelIndex);
      assertEquals(at + "positionIndex",
            expected.positionIndex, actual.positionIndex);
      assertEquals(at + "position",
            expected.position, actual.position);
      assertEquals(at + "exposure",
            expected.exposure, actual.exposure, 0.0001);
      assertDoubleEquals(at + "slice", expected.slice, actual.slice);
      assertDoubleEquals(at + "waitTimeMs",
            expected.waitTimeMs, actual.waitTimeMs);
      assertEquals(at + "autofocus",
            expected.autofocus, actual.autofocus);
      assertEquals(at + "newPosition",
            expected.newPosition, actual.newPosition);
      assertEquals(at + "closeShutter",
            expected.closeShutter, actual.closeShutter);
      assertEquals(at + "relativeZ",
            expected.relativeZ, actual.relativeZ);
      assertEquals(at + "task", expected.task, actual.task);
      assertEquals(at + "nextFrameIndex",
            expected.nextFrameIndex, actual.nextFrameIndex);
      String actualChannelName =
            actual.channel != null ? actual.channel.name : null;
      assertEquals(at + "channelName",
            expected.channelName, actualChannelName);
      Map<String, Map<String, String>> actualChannelProps =
            actual.channel != null
                  ? propsToJson(actual.channel.properties) : null;
      assertEquals(at + "channelProperties",
            expected.channelProperties, actualChannelProps);
      assertEquals(at + "burstLength",
            expected.burstLength, actual.burstLength);
      assertEquals(at + "cameraChannelIndex",
            expected.cameraChannelIndex, actual.cameraChannelIndex);
      assertEquals(at + "camera", expected.camera, actual.camera);

      // Channel fields beyond name/properties
      if (actual.channel != null) {
         assertDoubleEquals(at + "channelExposure",
               expected.channelExposure, actual.channel.exposure);
         assertDoubleEquals(at + "channelZOffset",
               expected.channelZOffset, actual.channel.zOffset);
         assertEquals(at + "channelUseZStack",
               expected.channelUseZStack, actual.channel.useZStack);
         assertEquals(at + "channelUseChannel",
               expected.channelUseChannel, actual.channel.useChannel);
         assertEquals(at + "channelSkipFrames",
               expected.channelSkipFrames,
               Integer.valueOf(actual.channel.skipFrames));
         assertEquals(at + "channelColor",
               expected.channelColor, formatColor(actual.channel.color));
      } else {
         assertNull(at + "channelExposure", expected.channelExposure);
         assertNull(at + "channelZOffset", expected.channelZOffset);
         assertNull(at + "channelUseZStack", expected.channelUseZStack);
         assertNull(at + "channelUseChannel", expected.channelUseChannel);
         assertNull(at + "channelSkipFrames", expected.channelSkipFrames);
         assertNull(at + "channelColor", expected.channelColor);
      }

      // burstData
      if (expected.burstData != null) {
         assertNotNull(at + "burstData", actual.burstData);
         assertEquals(at + "burstData.size",
               expected.burstData.size(), actual.burstData.size());
         for (int i = 0; i < expected.burstData.size(); i++) {
            assertEventEquals(at + "burstData.",
                  expected.burstData.get(i), actual.burstData.get(i), i);
         }
      } else {
         assertNull(at + "burstData", actual.burstData);
      }

      // triggerSequence
      assertTriggerSequenceEquals(at, expected.triggerSequence,
            actual.triggerSequence);

      // metadata
      assertEquals(at + "metadata", expected.metadata, actual.metadata);

      // runnableCount
      int expectedRc = expected.runnableCount != null
            ? expected.runnableCount : 0;
      int actualRc = actual.runnables != null
            ? actual.runnables.size() : 0;
      assertEquals(at + "runnableCount", expectedRc, actualRc);
   }

   private static void assertTriggerSequenceEquals(String at,
         TriggerSequenceJson expected, TriggerSequence actual) {
      if (expected == null) {
         assertNull(at + "triggerSequence", actual);
         return;
      }
      assertNotNull(at + "triggerSequence", actual);
      // Compare properties
      TriggerSequenceJson actualJson = triggerSeqToJson(actual);
      assertEquals(at + "triggerSequence.properties",
            expected.properties, actualJson.properties);
      // Compare slices
      if (expected.slices == null) {
         assertNull(at + "triggerSequence.slices", actual.slices);
      } else {
         assertNotNull(at + "triggerSequence.slices", actual.slices);
         assertEquals(at + "triggerSequence.slices.size",
               expected.slices.size(), actual.slices.size());
         for (int i = 0; i < expected.slices.size(); i++) {
            assertEquals(at + "triggerSequence.slices[" + i + "]",
                  expected.slices.get(i), actual.slices.get(i), 0.0001);
         }
      }
   }

   private static void assertDoubleEquals(String msg,
         Double expected, Double actual) {
      if (expected == null) {
         assertNull(msg, actual);
      } else {
         assertNotNull(msg, actual);
         assertEquals(msg, expected, actual, 0.0001);
      }
   }

   static HelperMockCore mockCoreFromJson(MockCoreJson mcj) {
      if (mcj == null) {
         return new HelperMockCore();
      }
      return new HelperMockCore(mcj);
   }
}

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

   static class TestCase {
      String description;
      SettingsJson settings;
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
      ej.channelName = e.channel != null ? e.channel.name : null;
      ej.burstLength = e.burstLength;
      ej.camera = e.camera;
      return ej;
   }

   // --- Assertion ---

   static void assertEventEquals(EventJson expected, AcqEvent actual,
         int idx) {
      String at = "Event[" + idx + "] ";
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
      assertEquals(at + "burstLength",
            expected.burstLength, actual.burstLength);
      assertEquals(at + "cameraChannelIndex",
            expected.cameraChannelIndex, actual.cameraChannelIndex);
      assertEquals(at + "camera", expected.camera, actual.camera);
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
}

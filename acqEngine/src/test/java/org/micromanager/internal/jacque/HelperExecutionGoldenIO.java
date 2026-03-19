package org.micromanager.internal.jacque;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.micromanager.AutofocusPlugin;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;

final class HelperExecutionGoldenIO {

   // --- DTO classes matching JSON schema ---

   static class MockCoreJson {
      String cameraDevice;
      String shutterDevice;
      String focusDevice;
      String xyStageDevice;
      String autoFocusDevice;
      Boolean autoShutter;
      Double exposure;
      Map<String, Double> positions;
      Map<String, List<Double>> xyPositions;
      Map<String, Map<String, String>> properties;
      Integer numberOfCameraChannels;
      List<String> cameraChannelNames;
      Boolean continuousFocusEnabled;
      Boolean shutterOpen;
      Long imageWidth;
      Long imageHeight;
      Long bitDepth;
      Long bytesPerPixel;
      Double pixelSizeUm;
      String pixelSizeAffine;
      Long numberOfComponents;
      Boolean stageSequenceable;
      Integer stageSequenceMaxLength;
      Map<String, Map<String, PropertySeqJson>> propertySequencing;
   }

   static class PropertySeqJson {
      Boolean sequenceable;
      Integer maxLength;
   }

   static class StagePositionJson {
      String stageName;
      int numAxes;
      double x;
      Double y;
   }

   static class MultiStagePositionJson {
      String label;
      List<StagePositionJson> stagePositions;
   }

   static class AutofocusDeviceJson {
      String name;
      Double resultZ;
   }

   static class InitialStateJson {
      String defaultZDrive;
      String defaultXYStage;
      Double referenceZ;
      Boolean initAutoShutter;
      Double initExposure;
      Boolean initShutterState;
      Boolean initContinuousFocus;
      List<MultiStagePositionJson> positionList;
      AutofocusDeviceJson autofocusDevice;
   }

   static class SettingsJson {
      Integer cameraTimeout;
   }

   static class MethodCallJson {
      String method;
      List<Object> args;
   }

   static class TestCase {
      String description;
      MockCoreJson mockCore;
      InitialStateJson initialState;
      SettingsJson settings;
      List<HelperGoldenFileIO.EventJson> events;
      List<MethodCallJson> expectedCalls;
   }

   // --- Gson instance ---

   private static final Gson GSON = new GsonBuilder()
         .serializeNulls()
         .setPrettyPrinting()
         .disableHtmlEscaping()
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

   // --- Conversion helpers ---

   static HelperRecordingMockCore.Config mockCoreConfig(MockCoreJson mcj) {
      HelperRecordingMockCore.Config c = new HelperRecordingMockCore.Config();
      if (mcj == null) {
         return c;
      }
      if (mcj.cameraDevice != null) c.cameraDevice = mcj.cameraDevice;
      if (mcj.shutterDevice != null) c.shutterDevice = mcj.shutterDevice;
      if (mcj.focusDevice != null) c.focusDevice = mcj.focusDevice;
      if (mcj.xyStageDevice != null) c.xyStageDevice = mcj.xyStageDevice;
      if (mcj.autoFocusDevice != null) {
         c.autoFocusDevice = mcj.autoFocusDevice;
      }
      if (mcj.autoShutter != null) c.autoShutter = mcj.autoShutter;
      if (mcj.exposure != null) c.exposure = mcj.exposure;
      if (mcj.positions != null) c.positions = new HashMap<>(mcj.positions);
      if (mcj.xyPositions != null) {
         for (Map.Entry<String, List<Double>> e
               : mcj.xyPositions.entrySet()) {
            List<Double> xy = e.getValue();
            c.xyPositions.put(e.getKey(),
                  new double[] { xy.get(0), xy.get(1) });
         }
      }
      if (mcj.properties != null) {
         c.properties = new HashMap<>();
         for (Map.Entry<String, Map<String, String>> e
               : mcj.properties.entrySet()) {
            c.properties.put(e.getKey(), new HashMap<>(e.getValue()));
         }
      }
      if (mcj.numberOfCameraChannels != null) {
         c.numberOfCameraChannels = mcj.numberOfCameraChannels;
      }
      if (mcj.cameraChannelNames != null) {
         c.cameraChannelNames = mcj.cameraChannelNames;
      }
      if (mcj.continuousFocusEnabled != null) {
         c.continuousFocusEnabled = mcj.continuousFocusEnabled;
      }
      if (mcj.shutterOpen != null) c.shutterOpen = mcj.shutterOpen;
      if (mcj.imageWidth != null) c.imageWidth = mcj.imageWidth;
      if (mcj.imageHeight != null) c.imageHeight = mcj.imageHeight;
      if (mcj.bitDepth != null) c.bitDepth = mcj.bitDepth;
      if (mcj.bytesPerPixel != null) c.bytesPerPixel = mcj.bytesPerPixel;
      if (mcj.pixelSizeUm != null) c.pixelSizeUm = mcj.pixelSizeUm;
      if (mcj.pixelSizeAffine != null) {
         c.pixelSizeAffine = mcj.pixelSizeAffine;
      }
      if (mcj.numberOfComponents != null) {
         c.numberOfComponents = mcj.numberOfComponents;
      }
      // Ensure properties map is never empty; Clojure code assumes
      // getSystemStateCache is non-empty (JSONArray(keys nil) → NPE)
      if (c.properties.isEmpty()) {
         Map<String, String> camProps = new HashMap<>();
         camProps.put("Binning", "1");
         c.properties.put(c.cameraDevice, camProps);
      }
      if (mcj.stageSequenceable != null) {
         c.stageSequenceable = mcj.stageSequenceable;
      }
      if (mcj.stageSequenceMaxLength != null) {
         c.stageSequenceMaxLength = mcj.stageSequenceMaxLength;
      }
      if (mcj.propertySequencing != null) {
         for (Map.Entry<String, Map<String, PropertySeqJson>> devEntry
               : mcj.propertySequencing.entrySet()) {
            String device = devEntry.getKey();
            for (Map.Entry<String, PropertySeqJson> propEntry
                  : devEntry.getValue().entrySet()) {
               List<String> key = new ArrayList<>(2);
               key.add(device);
               key.add(propEntry.getKey());
               PropertySeqJson psj = propEntry.getValue();
               c.propertySequenceable.put(key,
                     psj.sequenceable != null && psj.sequenceable);
               c.propertySequenceMaxLength.put(key,
                     psj.maxLength != null ? psj.maxLength : 0);
            }
         }
      }
      return c;
   }

   static void applyInitialState(EngineState state,
         InitialStateJson isj, HelperRecordingMockCore.Config coreConfig) {
      if (isj == null) {
         return;
      }
      if (isj.defaultZDrive != null) {
         state.defaultZDrive = isj.defaultZDrive;
      }
      if (isj.defaultXYStage != null) {
         state.defaultXYStage = isj.defaultXYStage;
      }
      if (isj.referenceZ != null) {
         state.referenceZ = isj.referenceZ;
      }
      if (isj.initAutoShutter != null) {
         state.initAutoShutter = isj.initAutoShutter;
      }
      if (isj.initExposure != null) {
         state.initExposure = isj.initExposure;
      }
      if (isj.initShutterState != null) {
         state.initShutterState = isj.initShutterState;
      }
      if (isj.initContinuousFocus != null) {
         state.initContinuousFocus = isj.initContinuousFocus;
      }
      if (isj.positionList != null) {
         state.positionList = buildPositionList(isj.positionList);
      }
      // Don't pre-populate cameraExposures: Clojure's prepare-state
      // puts exposure in :exposure, not :cameras, so set-exposure's
      // cache check always misses on the first call.

      // Initialize lastStagePositions to match prepare-state's
      // :last-stage-positions
      String zDrive = state.defaultZDrive;
      if (zDrive != null && !zDrive.isEmpty()) {
         Double zPos = coreConfig.positions.get(zDrive);
         state.lastStagePositions.put(zDrive, zPos != null ? zPos : 0.0);
      }
      String xyStage = state.defaultXYStage;
      if (xyStage != null && !xyStage.isEmpty()) {
         double[] xy = coreConfig.xyPositions.get(xyStage);
         if (xy != null) {
            state.lastStagePositions.put(xyStage, xy.clone());
         } else {
            state.lastStagePositions.put(xyStage, new double[] {0, 0});
         }
      }
   }

   static PositionList buildPositionList(
         List<MultiStagePositionJson> jsonList) {
      PositionList pl = new PositionList();
      for (MultiStagePositionJson mspJson : jsonList) {
         MultiStagePosition msp = new MultiStagePosition();
         msp.setLabel(mspJson.label);
         for (StagePositionJson spJson : mspJson.stagePositions) {
            if (spJson.numAxes == 1) {
               msp.add(StagePosition.create1D(spJson.stageName, spJson.x));
            } else {
               msp.add(StagePosition.create2D(spJson.stageName,
                     spJson.x, spJson.y != null ? spJson.y : 0));
            }
         }
         pl.addPosition(msp);
      }
      return pl;
   }

   static AcqEvent eventFromJson(HelperGoldenFileIO.EventJson ej) {
      AcqEvent e = new AcqEvent();
      e.frameIndex = ej.frameIndex;
      e.sliceIndex = ej.sliceIndex;
      e.channelIndex = ej.channelIndex;
      e.positionIndex = ej.positionIndex;
      e.cameraChannelIndex = ej.cameraChannelIndex;
      e.position = ej.position;
      e.exposure = ej.exposure;
      e.slice = ej.slice;
      e.waitTimeMs = ej.waitTimeMs;
      e.autofocus = ej.autofocus;
      e.newPosition = ej.newPosition;
      e.closeShutter = ej.closeShutter;
      e.relativeZ = ej.relativeZ;
      e.task = ej.task;
      e.nextFrameIndex = ej.nextFrameIndex;
      e.burstLength = ej.burstLength;
      e.camera = ej.camera;
      e.metadata = ej.metadata;
      if (ej.channelName != null) {
         AcqChannel ch = new AcqChannel();
         ch.name = ej.channelName;
         if (ej.channelExposure != null) {
            ch.exposure = ej.channelExposure;
         }
         if (ej.channelZOffset != null) {
            ch.zOffset = ej.channelZOffset;
         }
         if (ej.channelUseZStack != null) {
            ch.useZStack = ej.channelUseZStack;
         }
         if (ej.channelUseChannel != null) {
            ch.useChannel = ej.channelUseChannel;
         }
         if (ej.channelSkipFrames != null) {
            ch.skipFrames = ej.channelSkipFrames;
         }
         ch.properties = HelperGoldenFileIO.propsFromJsonPublic(
               ej.channelProperties);
         e.channel = ch;
      }
      if (ej.burstData != null) {
         e.burstData = new ArrayList<>();
         for (HelperGoldenFileIO.EventJson sub : ej.burstData) {
            e.burstData.add(eventFromJson(sub));
         }
      }
      if (ej.triggerSequence != null) {
         e.triggerSequence = triggerSeqFromJson(ej.triggerSequence);
      }
      return e;
   }

   private static TriggerSequence triggerSeqFromJson(
         HelperGoldenFileIO.TriggerSequenceJson tj) {
      TriggerSequence ts = new TriggerSequence();
      ts.slices = tj.slices;
      ts.properties = HelperGoldenFileIO.propsListFromJson(tj.properties);
      return ts;
   }

   static AcqSettings settingsFromJson(SettingsJson sj) {
      AcqSettings s = new AcqSettings();
      s.cameraTimeout = sj != null && sj.cameraTimeout != null
            ? sj.cameraTimeout : 5000;
      return s;
   }

   static AutofocusPlugin createFakeAutofocus(
         InitialStateJson isj, HelperRecordingMockCore mockCore) {
      if (isj == null || isj.autofocusDevice == null) {
         return null;
      }
      AutofocusDeviceJson afj = isj.autofocusDevice;
      return new HelperFakeAutofocus(afj.name, afj.resultZ,
            mockCore, isj.defaultZDrive);
   }

   static List<MethodCallJson> callLogToJson(
         List<HelperRecordingMockCore.MethodCall> calls) {
      List<MethodCallJson> result = new ArrayList<>();
      for (HelperRecordingMockCore.MethodCall mc : calls) {
         MethodCallJson mcj = new MethodCallJson();
         mcj.method = mc.method;
         mcj.args = mc.args;
         result.add(mcj);
      }
      return result;
   }
}

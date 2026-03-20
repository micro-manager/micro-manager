package org.micromanager.internal.jacque;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import mmcorej.Configuration;
import mmcorej.DoubleVector;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;

public final class HelperRecordingMockCore implements ExecutionCoreOps {

   // --- Call recording ---

   static final class MethodCall {
      final String method;
      final List<Object> args;

      MethodCall(String method, Object... args) {
         this.method = method;
         this.args = Collections.unmodifiableList(Arrays.asList(args));
      }

      @Override
      public String toString() {
         return method + "(" + args + ")";
      }
   }

   private final List<MethodCall> callLog = new ArrayList<>();
   private final List<MethodCall> logMessages = new ArrayList<>();

   List<MethodCall> getCallLog() {
      return Collections.unmodifiableList(callLog);
   }

   List<MethodCall> getLogMessages() {
      return Collections.unmodifiableList(logMessages);
   }

   void clearCallLog() {
      callLog.clear();
      logMessages.clear();
   }

   private void record(String method, Object... args) {
      callLog.add(new MethodCall(method, args));
   }

   // --- Configuration DTO ---

   static final class Config {
      String cameraDevice = "Camera";
      String shutterDevice = "Shutter";
      String focusDevice = "Z";
      String xyStageDevice = "XY";
      String autoFocusDevice = "AutoFocus";
      boolean autoShutter = true;
      double exposure = 10.0;
      Map<String, Double> positions = new HashMap<>();
      Map<String, double[]> xyPositions = new HashMap<>();
      Map<String, Map<String, String>> properties = new HashMap<>();
      int numberOfCameraChannels = 1;
      List<String> cameraChannelNames;
      boolean continuousFocusEnabled = false;
      boolean shutterOpen = false;
      long imageWidth = 512;
      long imageHeight = 512;
      long bitDepth = 16;
      long bytesPerPixel = 2;
      double pixelSizeUm = 1.0;
      String pixelSizeAffine = "1.0;0.0;0.0;0.0;1.0;0.0";
      long numberOfComponents = 1;
      String pixelType = "GRAY16";
      int cameraTimeout = 5000;

      Set<String> continuousFocusDrives = new HashSet<>();

      // Sequencing (inherited CoreOps)
      boolean stageSequenceable = false;
      int stageSequenceMaxLength = 0;
      Map<List<String>, Boolean> propertySequenceable = new HashMap<>();
      Map<List<String>, Integer> propertySequenceMaxLength = new HashMap<>();

   }

   // --- State ---

   private final Config config;
   private boolean autoShutter;
   private double exposure;
   private final Map<String, Double> positions;
   private final Map<String, double[]> xyPositions;
   private final Map<String, Map<String, String>> properties;
   private boolean shutterOpen;
   private boolean continuousFocusEnabled;

   // Image buffers
   private TaggedImage snappedImage;
   private final Queue<TaggedImage> sequenceImages = new LinkedList<>();
   private boolean sequenceRunning;
   private int nextImageNumber;

   HelperRecordingMockCore() {
      this(new Config());
   }

   HelperRecordingMockCore(Config config) {
      this.config = config;
      this.autoShutter = config.autoShutter;
      this.exposure = config.exposure;
      this.positions = new HashMap<>(config.positions);
      this.xyPositions = new HashMap<>();
      for (Map.Entry<String, double[]> e : config.xyPositions.entrySet()) {
         this.xyPositions.put(e.getKey(), e.getValue().clone());
      }
      this.properties = new HashMap<>();
      for (Map.Entry<String, Map<String, String>> e
            : config.properties.entrySet()) {
         this.properties.put(e.getKey(), new HashMap<>(e.getValue()));
      }
      this.shutterOpen = config.shutterOpen;
      this.continuousFocusEnabled = config.continuousFocusEnabled;
   }

   // --- CoreOps (sequencing queries) ---

   @Override
   public boolean isPropertySequenceable(String device, String property) {
      record("isPropertySequenceable", device, property);
      List<String> key = Arrays.asList(device, property);
      Boolean val = config.propertySequenceable.get(key);
      return val != null && val;
   }

   @Override
   public int getPropertySequenceMaxLength(String device, String property) {
      record("getPropertySequenceMaxLength", device, property);
      List<String> key = Arrays.asList(device, property);
      Integer val = config.propertySequenceMaxLength.get(key);
      return val != null ? val : 0;
   }

   @Override
   public boolean isStageSequenceable(String device) {
      record("isStageSequenceable", device);
      return config.stageSequenceable;
   }

   @Override
   public int getStageSequenceMaxLength(String device) {
      record("getStageSequenceMaxLength", device);
      return config.stageSequenceMaxLength;
   }

   // --- Device names ---

   @Override
   public String getCameraDevice() {
      record("getCameraDevice");
      return config.cameraDevice;
   }

   @Override
   public String getShutterDevice() {
      record("getShutterDevice");
      return config.shutterDevice;
   }

   @Override
   public String getFocusDevice() {
      record("getFocusDevice");
      return config.focusDevice;
   }

   @Override
   public String getAutoFocusDevice() {
      record("getAutoFocusDevice");
      return config.autoFocusDevice;
   }

   @Override
   public String getXYStageDevice() {
      record("getXYStageDevice");
      return config.xyStageDevice;
   }

   // --- Queries ---

   @Override
   public boolean getAutoShutter() {
      record("getAutoShutter");
      return autoShutter;
   }

   @Override
   public double getExposure() {
      record("getExposure");
      return exposure;
   }

   @Override
   public double getPosition(String stage) {
      record("getPosition", stage);
      Double pos = positions.get(stage);
      return pos != null ? pos : 0.0;
   }

   @Override
   public void getXYPosition(String stage, double[] x, double[] y) {
      record("getXYPosition", stage);
      double[] xy = xyPositions.get(stage);
      if (xy != null) {
         x[0] = xy[0];
         y[0] = xy[1];
      }
   }

   @Override
   public long getNumberOfCameraChannels() {
      record("getNumberOfCameraChannels");
      return config.numberOfCameraChannels;
   }

   @Override
   public String getCameraChannelName(int index) {
      record("getCameraChannelName", index);
      if (config.cameraChannelNames != null
            && index < config.cameraChannelNames.size()) {
         return config.cameraChannelNames.get(index);
      }
      return "Default";
   }

   @Override
   public boolean getShutterOpen() {
      record("getShutterOpen");
      return shutterOpen;
   }

   @Override
   public boolean isContinuousFocusEnabled() {
      record("isContinuousFocusEnabled");
      return continuousFocusEnabled;
   }

   @Override
   public boolean isContinuousFocusDrive(String stage) {
      record("isContinuousFocusDrive", stage);
      return config.continuousFocusDrives.contains(stage);
   }

   @Override
   public boolean isBufferOverflowed() {
      record("isBufferOverflowed");
      return false;
   }

   @Override
   public boolean isSequenceRunning() {
      record("isSequenceRunning");
      return sequenceRunning;
   }

   @Override
   public boolean hasProperty(String dev, String prop) {
      record("hasProperty", dev, prop);
      Map<String, String> devProps = properties.get(dev);
      return devProps != null && devProps.containsKey(prop);
   }

   @Override
   public String getProperty(String dev, String prop) {
      record("getProperty", dev, prop);
      Map<String, String> devProps = properties.get(dev);
      return devProps != null ? devProps.get(prop) : "";
   }

   @Override
   public long getImageWidth() {
      record("getImageWidth");
      return config.imageWidth;
   }

   @Override
   public long getImageHeight() {
      record("getImageHeight");
      return config.imageHeight;
   }

   @Override
   public long getImageBitDepth() {
      record("getImageBitDepth");
      return config.bitDepth;
   }

   @Override
   public long getBytesPerPixel() {
      record("getBytesPerPixel");
      return config.bytesPerPixel;
   }

   @Override
   public double getPixelSizeUm() {
      record("getPixelSizeUm");
      return config.pixelSizeUm;
   }

   @Override
   public String getPixelSizeAffineAsString() {
      record("getPixelSizeAffineAsString");
      return config.pixelSizeAffine;
   }

   @Override
   public long getNumberOfComponents() {
      record("getNumberOfComponents");
      return config.numberOfComponents;
   }

   // --- State modification ---

   @Override
   public void setAutoShutter(boolean auto) {
      record("setAutoShutter", auto);
      this.autoShutter = auto;
   }

   @Override
   public void setExposure(double exp) {
      record("setExposure", exp);
      this.exposure = exp;
   }

   @Override
   public void setPosition(String stage, double pos) {
      record("setPosition", stage, pos);
      positions.put(stage, pos);
   }

   @Override
   public void setXYPosition(String stage, double x, double y) {
      record("setXYPosition", stage, x, y);
      xyPositions.put(stage, new double[] { x, y });
   }

   @Override
   public void setShutterOpen(boolean open) {
      record("setShutterOpen", open);
      this.shutterOpen = open;
   }

   @Override
   public void setProperty(String dev, String prop, String val) {
      record("setProperty", dev, prop, val);
      properties.computeIfAbsent(dev, k -> new HashMap<>()).put(prop, val);
   }

   @Override
   public void enableContinuousFocus(boolean on) {
      record("enableContinuousFocus", on);
      this.continuousFocusEnabled = on;
   }

   @Override
   public void waitForDevice(String dev) {
      record("waitForDevice", dev);
   }

   // --- Image acquisition ---

   @Override
   public void snapImage() {
      record("snapImage");
      snappedImage = makeDummyImage(0);
   }

   @Override
   public TaggedImage getTaggedImage(int channelIndex) {
      record("getTaggedImage", channelIndex);
      return snappedImage != null ? snappedImage : makeDummyImage(0);
   }

   @Override
   public TaggedImage popNextTaggedImage() {
      record("popNextTaggedImage");
      TaggedImage img = sequenceImages.poll();
      if (img == null) {
         sequenceRunning = false;
         return makeDummyImage(nextImageNumber++);
      }
      return img;
   }

   private TaggedImage makeDummyImage(int imageNumber) {
      int size = (int) (config.imageWidth * config.imageHeight
            * config.bytesPerPixel);
      byte[] pixels = new byte[size];
      JSONObject tags = new JSONObject();
      try {
         tags.put("ElapsedTime-ms", "0.0");
         tags.put("ImageNumber", String.valueOf(imageNumber));
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      return new TaggedImage(pixels, tags);
   }

   // --- Sequencing ---

   @Override
   public void loadPropertySequence(String dev, String prop,
         StrVector vals) {
      List<String> list = new ArrayList<>();
      for (int i = 0; i < vals.size(); i++) {
         list.add(vals.get(i));
      }
      record("loadPropertySequence", dev, prop, list);
   }

   @Override
   public void loadStageSequence(String stage, DoubleVector positions) {
      List<Double> list = new ArrayList<>();
      for (int i = 0; i < positions.size(); i++) {
         list.add(positions.get(i));
      }
      record("loadStageSequence", stage, list);
   }

   @Override
   public void startPropertySequence(String dev, String prop) {
      record("startPropertySequence", dev, prop);
   }

   @Override
   public void startStageSequence(String stage) {
      record("startStageSequence", stage);
   }

   @Override
   public void startSequenceAcquisition(long numImages, double intervalMs,
         boolean stopOnOverflow) {
      record("startSequenceAcquisition", numImages, intervalMs,
            stopOnOverflow);
      sequenceRunning = true;
      nextImageNumber = 0;
      sequenceImages.clear();
      for (int i = 0; i < numImages; i++) {
         sequenceImages.add(makeDummyImage(i));
      }
   }

   @Override
   public void stopSequenceAcquisition() {
      record("stopSequenceAcquisition");
      sequenceRunning = false;
   }

   @Override
   public void stopPropertySequence(String dev, String prop) {
      record("stopPropertySequence", dev, prop);
   }

   @Override
   public void stopStageSequence(String stage) {
      record("stopStageSequence", stage);
   }

   // --- System state ---

   @Override
   public Configuration getSystemStateCache() {
      record("getSystemStateCache");
      Configuration config = new Configuration();
      for (Map.Entry<String, Map<String, String>> devEntry
            : properties.entrySet()) {
         for (Map.Entry<String, String> propEntry
               : devEntry.getValue().entrySet()) {
            config.addSetting(new PropertySetting(
                  devEntry.getKey(), propEntry.getKey(),
                  propEntry.getValue()));
         }
      }
      return config;
   }

   @Override
   public Configuration getConfigData(String group, String configName) {
      record("getConfigData", group, configName);
      return new Configuration();
   }

   @Override
   public void getROI(int[] x, int[] y, int[] w, int[] h) {
      record("getROI");
      x[0] = 0;
      y[0] = 0;
      w[0] = (int) config.imageWidth;
      h[0] = (int) config.imageHeight;
   }

   // --- Logging ---

   void updatePositionSilently(String stage, double pos) {
      positions.put(stage, pos);
   }

   @Override
   public void logMessage(String msg, boolean debug) {
      logMessages.add(new MethodCall("logMessage", msg, debug));
   }
}

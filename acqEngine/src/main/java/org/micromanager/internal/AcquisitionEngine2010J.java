package org.micromanager.internal;

import java.awt.Color;
import java.io.EOFException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.AutofocusPlugin;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.AcquisitionSleepEvent;
import org.micromanager.acquisition.internal.IAcquisitionEngine2010;
import org.micromanager.acquisition.internal.TaggedImageQueue;
import org.micromanager.data.Coords;
import org.micromanager.internal.jacque.AcqChannel;
import org.micromanager.internal.jacque.AcqEvent;
import org.micromanager.internal.jacque.AcqSettings;
import org.micromanager.internal.jacque.CoreOps;
import org.micromanager.internal.jacque.EngineState;
import org.micromanager.internal.jacque.MmUtils;
import org.micromanager.internal.jacque.SequenceGenerator;
import org.micromanager.internal.jacque.TriggerSequence;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.MMStudio;

public class AcquisitionEngine2010J implements IAcquisitionEngine2010 {

   private final Studio gui;
   private final CMMCore mmc;
   private final EngineState state = new EngineState();
   private final List<SequenceGenerator.AttachedRunnable> attachedRunnables =
         new ArrayList<>();
   private final Set<String> pendingDevices = new HashSet<>();
   private Map<List<String>, List<String>> activePropertySequences;
   private Object[] activeSliceSequence; // [String zStage, List<Double> slices]

   public AcquisitionEngine2010J(Studio studio) {
      this.gui = studio;
      this.mmc = studio.getCMMCore();
   }

   public AcquisitionEngine2010J(CMMCore mmc) {
      this.gui = null;
      this.mmc = mmc;
   }

   // --- Time ---

   private static long jvmTimeMs() {
      return System.nanoTime() / 1_000_000;
   }

   private long elapsedTime() {
      return state.startTime != 0 ? jvmTimeMs() - state.startTime : 0;
   }

   private static Double coreTimeFromTags(Map<String, Object> tags) {
      try {
         Object val = tags.get("ElapsedTime-ms");
         if (val != null) {
            return Double.parseDouble(val.toString());
         }
      } catch (Exception e) {
         // ignore
      }
      return null;
   }

   private Double burstTime(Map<String, Object> tags) {
      if (state.burstTimeOffset != null
            && tags.get("ElapsedTime-ms") != null) {
         Double coreTime = coreTimeFromTags(tags);
         if (coreTime != null) {
            return coreTime + state.burstTimeOffset;
         }
      }
      return null;
   }

   // --- Channels ---

   private List<String> getCameraChannelNames() throws Exception {
      int n = (int) mmc.getNumberOfCameraChannels();
      List<String> names = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         names.add(mmc.getCameraChannelName(i));
      }
      return names;
   }

   private static String superChannelName(String simpleName,
         String cameraChannelName, int numCameraChannels) {
      if (numCameraChannels <= 1) {
         return simpleName;
      }
      if (simpleName == null || simpleName.isEmpty()
            || "Default".equals(simpleName)) {
         return cameraChannelName;
      }
      return simpleName + "-" + cameraChannelName;
   }

   // --- Metadata ---

   @SuppressWarnings("unchecked")
   private Map<String, Object> generateMetadata(AcqEvent event) {
      Map<String, Object> meta = new HashMap<>();
      if (event.metadata != null) {
         meta.putAll(event.metadata);
      }
      String xyStage = state.defaultXYStage;
      Double x = null, y = null;
      if (xyStage != null && !xyStage.isEmpty()) {
         Object xyObj = state.lastStagePositions.get(xyStage);
         if (xyObj instanceof double[]) {
            double[] xy = (double[]) xyObj;
            x = xy[0];
            y = xy[1];
         }
      }
      String posName = null;
      if (state.positionList != null) {
         MultiStagePosition msp = getMultiStagePosition(
               state.positionList, event.position);
         if (msp != null) {
            posName = msp.getLabel();
         }
      }
      meta.put("Binning", state.binning);
      meta.put("BitDepth", state.bitDepth);
      meta.put("Camera", event.camera);
      meta.put("CameraChannelIndex", event.cameraChannelIndex);
      meta.put("Channel", event.channel != null ? event.channel.name : null);
      meta.put("ChannelIndex", event.channelIndex);
      meta.put("Exposure-ms", event.exposure);
      meta.put("Frame", event.frameIndex);
      meta.put("FrameIndex", event.frameIndex);
      meta.put("Height", state.initHeight);
      meta.put("NextFrame", event.nextFrameIndex);
      meta.put("PixelSizeUm", state.pixelSizeUm);
      meta.put("PixelSizeAffine", state.pixelSizeAffine);
      meta.put("PixelType", state.pixelType);
      meta.put("PositionIndex", event.positionIndex);
      meta.put("PositionName", posName);
      meta.put("ReceivedTime", MmUtils.getCurrentTimeStr());
      meta.put("Slice", event.sliceIndex);
      meta.put("SliceIndex", event.sliceIndex);
      meta.put("SlicePosition", event.slice);
      meta.put("Summary", state.summaryMetadata);
      meta.put("Time", MmUtils.getCurrentTimeStr());
      meta.put("UUID", UUID.randomUUID());
      meta.put("WaitInterval", event.waitTimeMs);
      meta.put("Width", state.initWidth);
      meta.put("XPositionUm", x);
      meta.put("YPositionUm", y);
      Object zPos = state.lastStagePositions.get(state.defaultZDrive);
      meta.put("ZPositionUm", zPos);
      if (event.runnables != null && !event.runnables.isEmpty()) {
         List<String> runnableStrs = new ArrayList<>();
         for (Runnable r : event.runnables) {
            runnableStrs.add(r.toString());
         }
         meta.put("AttachedTasks", new JSONArray(runnableStrs));
      }
      return meta;
   }

   private TaggedImage annotateImage(Object pix, Map<String, Object> tags,
         AcqEvent event, Object elapsedTimeMs) {
      Map<String, Object> merged = new HashMap<>();
      if (tags != null) {
         merged.putAll(tags);
      }
      Map<String, Object> genMeta = generateMetadata(event);
      for (Map.Entry<String, Object> entry : genMeta.entrySet()) {
         if (entry.getValue() != null) {
            merged.put(entry.getKey(), entry.getValue());
         }
      }
      merged.put("StateCache-keys",
            new JSONArray(state.systemState.keySet()));
      merged.put("ElapsedTime-ms", elapsedTimeMs);
      return new TaggedImage(pix, new JSONObject(merged));
   }

   // --- Hardware error handling ---

   private void waitForDevice(String dev) {
      if (dev == null || dev.isEmpty()) {
         return;
      }
      try {
         mmc.waitForDevice(dev);
         pendingDevices.remove(dev);
      } catch (Exception e) {
         log("wait for device", dev, "failed.");
      }
   }

   private void addToPending(String dev) {
      pendingDevices.add(dev);
   }

   private interface DeviceAction {
      void run() throws Exception;
   }

   private void deviceBestEffort(String device, DeviceAction action)
         throws Exception {
      Runnable attempt = () -> {
         waitForDevice(device);
         addToPending(device);
         try {
            action.run();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      };
      boolean success = false;
      try {
         attempt.run();
         success = true;
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      if (!success) {
         log("second attempt");
         try {
            attempt.run();
            success = true;
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }
      if (!success) {
         state.stop = true;
         throw new Exception("Device failure: " + device);
      }
   }

   // --- Hardware control ---

   private void setExposure(String camera, double exp) throws Exception {
      Double current = state.cameraExposures.get(camera);
      if (current != null && current == exp) {
         return;
      }
      deviceBestEffort(camera, () -> mmc.setExposure(exp));
      state.cameraExposures.put(camera, exp);
   }

   private void waitForPendingDevices() {
      log("pending devices:", pendingDevices.toString());
      for (String dev : new ArrayList<>(pendingDevices)) {
         waitForDevice(dev);
      }
   }

   private double getZStagePosition(String stage) throws Exception {
      if (stage == null || stage.isEmpty()) {
         return 0;
      }
      return mmc.getPosition(stage);
   }

   private double[] getXYStagePosition(String stage) throws Exception {
      if (stage == null || stage.isEmpty()) {
         return null;
      }
      double[] x = new double[1];
      double[] y = new double[1];
      mmc.getXYPosition(stage, x, y);
      return new double[] { x[0], y[0] };
   }

   private void enableContinuousFocus(boolean on) throws Exception {
      String autofocus = mmc.getAutoFocusDevice();
      deviceBestEffort(autofocus, () -> mmc.enableContinuousFocus(on));
   }

   private void setShutterOpen(boolean open) throws Exception {
      String shutter = mmc.getShutterDevice();
      deviceBestEffort(shutter, () -> {
         Boolean current = state.shutterStates.get(shutter);
         if (current == null || current != open) {
            mmc.setShutterOpen(open);
            state.shutterStates.put(shutter, open);
         }
      });
   }

   private boolean isContinuousFocusDrive(String stage) throws Exception {
      if (stage == null || stage.isEmpty()) {
         return false;
      }
      return mmc.isContinuousFocusDrive(stage);
   }

   private void setZStagePosition(String stage, double pos)
         throws Exception {
      if (state.initContinuousFocus
            && !isContinuousFocusDrive(stage)
            && mmc.isContinuousFocusEnabled()) {
         enableContinuousFocus(false);
      }
      deviceBestEffort(stage, () -> mmc.setPosition(stage, pos));
   }

   private void setStagePositionZ(String stageDev, double z)
         throws Exception {
      if (stageDev == null || stageDev.isEmpty()) {
         return;
      }
      Object current = state.lastStagePositions.get(stageDev);
      if (current instanceof Double && (Double) current == z) {
         return;
      }
      setZStagePosition(stageDev, z);
      state.lastStagePositions.put(stageDev, z);
   }

   private void setStagePositionXY(String stageDev, double x, double y)
         throws Exception {
      Object current = state.lastStagePositions.get(stageDev);
      if (current instanceof double[]) {
         double[] xy = (double[]) current;
         if (xy[0] == x && xy[1] == y) {
            return;
         }
      }
      deviceBestEffort(stageDev,
            () -> mmc.setXYPosition(stageDev, x, y));
      state.lastStagePositions.put(stageDev, new double[] { x, y });
   }

   private void setProperty(List<String> key, String value)
         throws Exception {
      String d = key.get(0);
      String p = key.get(1);
      Map<String, String> devProps =
            state.lastPropertySettings.get(d);
      if (devProps != null && value.equals(devProps.get(p))) {
         return;
      }
      deviceBestEffort(d, () -> mmc.setProperty(d, p, value));
      state.lastPropertySettings
            .computeIfAbsent(d, k -> new HashMap<>())
            .put(p, value);
   }

   private void runAutofocus() throws Exception {
      String zDrive = state.defaultZDrive;
      double z0 = getZStagePosition(zDrive);
      try {
         log("running autofocus",
               state.autofocusDevice.getName());
         double z = state.autofocusDevice.fullFocus();
         state.lastStagePositions.put(state.defaultZDrive, z);
      } catch (Exception e) {
         ReportingUtils.logError(e, "Autofocus failed.");
         setStagePositionZ(zDrive, z0 + 1.0e-6);
      }
   }

   private void snapImage(boolean openBefore, boolean closeAfter)
         throws Exception {
      boolean savedAutoShutter = mmc.getAutoShutter();
      try {
         mmc.setAutoShutter(false);
         String shutter = mmc.getShutterDevice();
         waitForPendingDevices();
         if (openBefore) {
            setShutterOpen(true);
            waitForDevice(shutter);
         }
         String camera = mmc.getCameraDevice();
         deviceBestEffort(camera, () -> mmc.snapImage());
         state.lastImageTime = elapsedTime();
         if (closeAfter) {
            setShutterOpen(false);
            waitForDevice(shutter);
         }
      } finally {
         mmc.setAutoShutter(savedAutoShutter);
      }
   }

   // --- Triggering ---

   private void loadPropertySequences(
         Map<List<String>, List<String>> propertySequences)
         throws Exception {
      if (propertySequences.equals(activePropertySequences)) {
         return;
      }
      for (Map.Entry<List<String>, List<String>> entry
            : propertySequences.entrySet()) {
         String d = entry.getKey().get(0);
         String p = entry.getKey().get(1);
         mmc.loadPropertySequence(d, p,
               MmUtils.toStrVector(entry.getValue()));
      }
      activePropertySequences = propertySequences;
   }

   private List<Double> loadSliceSequence(List<Double> sliceSequence,
         boolean relativeZ) throws Exception {
      if (sliceSequence == null) {
         return null;
      }
      String z = mmc.getFocusDevice();
      double ref = state.referenceZ;
      List<Double> adjusted;
      if (relativeZ) {
         adjusted = new ArrayList<>(sliceSequence.size());
         for (double s : sliceSequence) {
            adjusted.add(ref + s);
         }
      } else {
         adjusted = new ArrayList<>(sliceSequence);
      }
      Object[] currentSeq = activeSliceSequence;
      if (currentSeq == null
            || !z.equals(currentSeq[0])
            || !adjusted.equals(currentSeq[1])) {
         mmc.loadStageSequence(z, MmUtils.toDoubleVector(adjusted));
         activeSliceSequence = new Object[] { z, adjusted };
      }
      return adjusted;
   }

   private void startPropertySequences(
         Map<List<String>, List<String>> propertySequences)
         throws Exception {
      for (Map.Entry<List<String>, List<String>> entry
            : propertySequences.entrySet()) {
         String d = entry.getKey().get(0);
         String p = entry.getKey().get(1);
         mmc.startPropertySequence(d, p);
         List<String> vals = entry.getValue();
         state.lastPropertySettings
               .computeIfAbsent(d, k -> new HashMap<>())
               .put(p, vals.get(vals.size() - 1));
      }
   }

   private void startSliceSequence(List<Double> slices) throws Exception {
      String zStage = state.defaultZDrive;
      mmc.startStageSequence(zStage);
      state.lastStagePositions.put(zStage,
            slices.get(slices.size() - 1));
   }

   private boolean firstTriggerMissing() throws Exception {
      return "1".equals(MmUtils.getPropertyValue(mmc,
            mmc.getCameraDevice(), "OutputTriggerFirstMissing"));
   }

   private long extraTriggers() throws Exception {
      String val = MmUtils.getPropertyValue(mmc,
            mmc.getCameraDevice(), "ExtraTriggers");
      return val != null ? Long.parseLong(val) : 0;
   }

   private <T> List<T> offsetCycle(long offset, List<T> coll) {
      if (coll == null || coll.isEmpty()) {
         return coll;
      }
      int n = coll.size();
      int toDrop = (int) (((offset % n) + n) % n);
      List<T> result = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         result.add(coll.get((i + toDrop) % n));
      }
      return result;
   }

   private List<Double> compensateForExtraTrigger(List<Double> slices)
         throws Exception {
      if (slices == null) {
         return null;
      }
      long extra = extraTriggers();
      List<Double> result = new ArrayList<>();
      for (long i = 0; i < extra; i++) {
         result.add(slices.get(0));
      }
      result.addAll(slices);
      return result;
   }

   private void initBurst(int length, TriggerSequence triggerSequence,
         boolean relativeZ) throws Exception {
      mmc.setAutoShutter(state.initAutoShutter);
      long extra = extraTriggers();
      Map<List<String>, List<String>> offsetProps = new HashMap<>();
      for (Map.Entry<List<String>, List<String>> entry
            : triggerSequence.properties.entrySet()) {
         offsetProps.put(entry.getKey(),
               offsetCycle(-extra, entry.getValue()));
      }
      loadPropertySequences(offsetProps);
      List<Double> absoluteSlices = loadSliceSequence(
            compensateForExtraTrigger(triggerSequence.slices), relativeZ);
      startPropertySequences(triggerSequence.properties);
      if (absoluteSlices != null) {
         startSliceSequence(triggerSequence.slices);
      }
      int burstLen = firstTriggerMissing() ? length + 1 : length;
      mmc.startSequenceAcquisition(burstLen, 0, true);
   }

   // --- Image collection ---

   private TaggedImage popTaggedImage() {
      try {
         return mmc.popNextTaggedImage();
      } catch (Exception e) {
         return null;
      }
   }

   private TaggedImage popTaggedImageTimeout(double timeoutMs)
         throws Exception {
      log("waiting for burst image with timeout",
            String.valueOf(timeoutMs), "ms");
      long deadline = System.currentTimeMillis() + (long) timeoutMs;
      while (true) {
         if (state.stop) {
            log("halting image collection due to engine stop");
            throw new EOFException("(Aborted)");
         }
         TaggedImage image = popTaggedImage();
         if (image != null) {
            return image;
         }
         if (System.currentTimeMillis() > deadline) {
            log("halting image collection due to timeout");
            throw new Exception(
                  "Timed out waiting for image to arrive from camera.");
         }
         if (mmc.isBufferOverflowed()) {
            log("halting image collection due to circular buffer overflow");
            throw new Exception("Sequence buffer overflowed.");
         }
         Thread.sleep(1);
      }
   }

   @SuppressWarnings("unchecked")
   private Object[] popBurstImage(double timeoutMs) throws Exception {
      TaggedImage ti = popTaggedImageTimeout(timeoutMs);
      return new Object[] {
         ti.pix,
         (Map<String, Object>) MmUtils.jsonToData(ti.tags)
      };
   }

   private void sendTaggedImage(BlockingQueue<TaggedImage> outQueue,
         TaggedImage taggedImage) throws Exception {
      while (true) {
         if (state.stop) {
            log("canceling image output due to engine stop");
            throw new EOFException("(Aborted)");
         }
         if (outQueue.offer(taggedImage, 1000, TimeUnit.MILLISECONDS)) {
            return;
         }
      }
   }

   private int makeMulticameraChannel(int rawChannelIndex,
         int cameraChannel, int numCameraChannels) {
      return cameraChannel + numCameraChannels * rawChannelIndex;
   }

   private List<AcqEvent> makeMulticameraEvents(AcqEvent event)
         throws Exception {
      int numCameraChannels = (int) mmc.getNumberOfCameraChannels();
      List<String> cameraChannelNames = getCameraChannelNames();
      List<AcqEvent> result = new ArrayList<>(numCameraChannels);
      for (int camCh = 0; camCh < numCameraChannels; camCh++) {
         AcqEvent e = event.copy();
         e.channelIndex = makeMulticameraChannel(
               event.channelIndex, camCh, numCameraChannels);
         if (e.channel != null) {
            AcqChannel chCopy = new AcqChannel();
            chCopy.name = superChannelName(event.channel.name,
                  cameraChannelNames.get(camCh), numCameraChannels);
            chCopy.exposure = event.channel.exposure;
            chCopy.zOffset = event.channel.zOffset;
            chCopy.useZStack = event.channel.useZStack;
            chCopy.useChannel = event.channel.useChannel;
            chCopy.skipFrames = event.channel.skipFrames;
            chCopy.color = event.channel.color;
            chCopy.properties = event.channel.properties;
            e.channel = chCopy;
         }
         e.cameraChannelIndex = camCh;
         e.camera = cameraChannelNames.get(camCh);
         result.add(e);
      }
      return result;
   }

   @SuppressWarnings("unchecked")
   private List<AcqEvent> assignZOffsets(List<AcqEvent> burstEvents) {
      if (activeSliceSequence != null) {
         List<Double> slices = (List<Double>) activeSliceSequence[1];
         if (slices != null) {
            for (int i = 0; i < burstEvents.size() && i < slices.size(); i++) {
               burstEvents.get(i).slice = slices.get(i);
            }
         }
      }
      return burstEvents;
   }

   private void burstCleanup() throws Exception {
      log("burst-cleanup");
      mmc.stopSequenceAcquisition();
      while (!state.stop && mmc.isSequenceRunning()) {
         Thread.sleep(5);
      }
   }

   @SuppressWarnings("unchecked")
   private TaggedImage tagBurstImage(Object[] image,
         List<AcqEvent> burstEvents, List<String> cameraChannelNames,
         String cameraIndexTag, int imageNumberOffset) {
      Object pix = image[0];
      Map<String, Object> tags = (Map<String, Object>) image[1];
      if (state.burstTimeOffset == null) {
         Double coreTime = coreTimeFromTags(tags);
         if (coreTime != null) {
            state.burstTimeOffset = (double) elapsedTime() - coreTime;
         }
      }
      int camChan = 0;
      Object camChanStr = tags.get(cameraIndexTag);
      if (camChanStr != null) {
         camChan = (int) Long.parseLong(camChanStr.toString());
      }
      int imageNumber = imageNumberOffset
            + (int) Long.parseLong(tags.get("ImageNumber").toString());
      AcqEvent burstEvent = burstEvents.get(imageNumber);
      int numCameraChannels = cameraChannelNames.size();
      int index = camChan < numCameraChannels ? camChan : 0;
      String cameraChannelName = cameraChannelNames.get(index);

      AcqEvent ev = burstEvent.copy();
      ev.channelIndex = makeMulticameraChannel(
            burstEvent.channelIndex, index, numCameraChannels);
      if (ev.channel != null) {
         AcqChannel chCopy = new AcqChannel();
         chCopy.name = superChannelName(burstEvent.channel.name,
               cameraChannelName, numCameraChannels);
         chCopy.exposure = burstEvent.channel.exposure;
         chCopy.zOffset = burstEvent.channel.zOffset;
         chCopy.useZStack = burstEvent.channel.useZStack;
         chCopy.properties = burstEvent.channel.properties;
         ev.channel = chCopy;
      }
      ev.cameraChannelIndex = index;

      Double timeStamp = burstTime(tags);
      return annotateImage(pix, tags, ev, timeStamp);
   }

   private void produceBurstImages(List<AcqEvent> burstEvents,
         List<String> cameraChannelNames, double timeoutMs,
         BlockingQueue<TaggedImage> outQueue) throws Exception {
      int total = burstEvents.size() * cameraChannelNames.size();
      String cameraIndexTag = mmc.getCameraDevice()
            + "-CameraChannelIndex";
      int imageNumberOffset = firstTriggerMissing() ? -1 : 0;
      // Pop images on a background thread into a bounded queue
      LinkedBlockingQueue<Object> imageQueue =
            new LinkedBlockingQueue<>(10);
      double tms = timeoutMs;
      Thread popThread = new Thread(() -> {
         try {
            for (int i = 0; i < total; i++) {
               try {
                  Object[] img = popBurstImage(tms);
                  imageQueue.put(img);
               } catch (Throwable t) {
                  imageQueue.put(t);
                  return;
               }
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      });
      popThread.start();
      try {
         for (int i = 0; i < total; i++) {
            Object item = imageQueue.take();
            if (item instanceof Throwable) {
               Throwable t = (Throwable) item;
               if (t instanceof Exception) {
                  throw (Exception) t;
               }
               throw new RuntimeException(t);
            }
            Object[] img = (Object[]) item;
            TaggedImage tagged = tagBurstImage(img, burstEvents,
                  cameraChannelNames, cameraIndexTag, imageNumberOffset);
            sendTaggedImage(outQueue, tagged);
         }
      } finally {
         burstCleanup();
      }
   }

   private void collectBurstImages(AcqEvent event,
         BlockingQueue<TaggedImage> outQueue, AcqSettings settings)
         throws Exception {
      double popTimeout = settings.cameraTimeout + 10 * event.exposure;
      if (firstTriggerMissing()) {
         popBurstImage(popTimeout);
      }
      state.burstTimeOffset = null;
      List<AcqEvent> burstEvents = assignZOffsets(
            new ArrayList<>(event.burstData));
      List<String> cameraChannelNames = getCameraChannelNames();
      produceBurstImages(burstEvents, cameraChannelNames,
            popTimeout, outQueue);
   }

   @SuppressWarnings("unchecked")
   private void collectSnapImage(AcqEvent event,
         BlockingQueue<TaggedImage> outQueue) throws Exception {
      TaggedImage raw = mmc.getTaggedImage(event.cameraChannelIndex);
      Map<String, Object> tags =
            (Map<String, Object>) MmUtils.jsonToData(raw.tags);
      if (outQueue != null) {
         TaggedImage annotated = annotateImage(
               raw.pix, tags, event, elapsedTime());
         sendTaggedImage(outQueue, annotated);
      }
   }

   private void returnConfig() throws Exception {
      Map<List<String>, String> current =
            MmUtils.getSystemConfigCached(mmc);
      // Restore properties that differ from initial state
      if (state.initSystemState != null) {
         Set<Map.Entry<List<String>, String>> initSet =
               new HashSet<>(state.initSystemState.entrySet());
         initSet.removeAll(current.entrySet());
         for (Map.Entry<List<String>, String> entry : initSet) {
            setProperty(entry.getKey(), entry.getValue());
         }
      }
   }

   private void stopTriggering() throws Exception {
      if (activePropertySequences != null) {
         for (List<String> key : activePropertySequences.keySet()) {
            mmc.stopPropertySequence(key.get(0), key.get(1));
         }
      }
      if (activeSliceSequence != null) {
         mmc.stopStageSequence((String) activeSliceSequence[0]);
      }
   }

   // --- Sleeping ---

   private void awaitResume() throws InterruptedException {
      while (state.pause && !state.stop) {
         Thread.sleep(5);
      }
   }

   private void interruptibleSleep(long timeMs)
         throws InterruptedException {
      CountDownLatch sleepy = new CountDownLatch(1);
      if (gui != null) {
         AcquisitionSleepEvent event = new AcquisitionSleepEvent(
               jvmTimeMs() + timeMs);
         gui.events().post(event);
      }
      state.sleepy = sleepy;
      state.nextWakeTime = jvmTimeMs() + timeMs;
      sleepy.await(timeMs, TimeUnit.MILLISECONDS);
   }

   private void acqSleep(double intervalMs) throws Exception {
      log("acq-sleep");
      if (state.initContinuousFocus
            && !mmc.isContinuousFocusEnabled()) {
         try {
            enableContinuousFocus(true);
         } catch (Throwable t) {
            // don't quit if this fails
         }
      }
      long targetTime = state.lastWakeTime + (long) intervalMs;
      long delta = targetTime - jvmTimeMs();
      if (gui != null && delta > 1000 && state.liveModeOn
            && !gui.live().isLiveModeOn()) {
         gui.live().setLiveMode(true);
      }
      if (delta > 0) {
         interruptibleSleep(delta);
      }
      awaitResume();
      if (gui != null) {
         state.liveModeOn = gui.live().isLiveModeOn();
         if (gui.live().isLiveModeOn()) {
            gui.live().setLiveMode(false);
         }
      }
      long now = jvmTimeMs();
      state.lastWakeTime = (now > targetTime + 10) ? now : targetTime;
   }

   // --- Higher level ---

   private void expose(AcqEvent event) throws Exception {
      boolean openBefore, closeAfter;
      if (mmc.getAutoShutter()) {
         openBefore = true;
         closeAfter = event.closeShutter;
      } else {
         openBefore = false;
         closeAfter = false;
      }
      state.systemState = MmUtils.mapConfig(mmc.getSystemStateCache());
      if ("snap".equals(event.task)) {
         snapImage(openBefore, closeAfter);
      } else if ("burst".equals(event.task)) {
         initBurst(event.burstData.size(),
               event.triggerSequence, event.relativeZ);
      }
   }

   private void collect(AcqEvent event, BlockingQueue<TaggedImage> outQueue,
         AcqSettings settings) throws Exception {
      log("collecting image(s)");
      try {
         if ("snap".equals(event.task)) {
            for (AcqEvent subEvent : makeMulticameraEvents(event)) {
               collectSnapImage(subEvent, outQueue);
            }
         } else if ("burst".equals(event.task)) {
            collectBurstImages(event, outQueue, settings);
         }
      } catch (EOFException e) {
         log("halted image collection and output due to engine stop");
      }
   }

   private Double zInMsp(MultiStagePosition msp, String zDrive) {
      if (msp == null || zDrive == null) {
         return null;
      }
      StagePosition sp = msp.get(zDrive);
      if (sp == null) {
         return null;
      }
      return sp.x;
   }

   private double computeZPosition(AcqEvent event) {
      Double zRef = null;
      MultiStagePosition msp = getMultiStagePosition(
            state.positionList, event.position);
      if (msp != null) {
         zRef = zInMsp(msp, state.defaultZDrive);
      }
      if (zRef == null) {
         zRef = state.referenceZ;
      }
      double channelOffset = (event.channel != null)
            ? event.channel.zOffset : 0;
      if (event.slice != null) {
         return channelOffset + event.slice
               + (event.relativeZ ? zRef : 0);
      }
      return channelOffset + zRef;
   }

   private boolean zStageNeedsAdjustment(String stageName)
         throws Exception {
      return !(state.initContinuousFocus
            && mmc.isContinuousFocusEnabled()
            && !isContinuousFocusDrive(stageName));
   }

   private void updateZPositions(int mspIndex) throws Exception {
      MultiStagePosition msp = getMultiStagePosition(
            state.positionList, mspIndex);
      if (msp == null) {
         return;
      }
      for (int i = 0; i < msp.size(); i++) {
         StagePosition sp = msp.get(i);
         String stageName = sp.getStageDeviceLabel();
         if (sp.numAxes == 1 && zStageNeedsAdjustment(stageName)) {
            MmUtils.setMspZPosition(state.positionList, mspIndex,
                  stageName, getZStagePosition(stageName));
         }
      }
   }

   private void recallZReference(int currentPosition) throws Exception {
      String zDrive = state.defaultZDrive;
      if (!zStageNeedsAdjustment(zDrive)) {
         return;
      }
      Double mspZ = MmUtils.getMspZPosition(
            state.positionList, currentPosition, zDrive);
      double z = (mspZ != null) ? mspZ : state.referenceZ;
      setStagePositionZ(zDrive, z);
      waitForDevice(zDrive);
   }

   private void storeZReference(int currentPosition) throws Exception {
      String zDrive = state.defaultZDrive;
      if (zDrive == null || zDrive.isEmpty()) {
         return;
      }
      if (zStageNeedsAdjustment(zDrive)) {
         state.referenceZ = getZStagePosition(zDrive);
      }
   }

   private static MultiStagePosition getMultiStagePosition(
         PositionList pl, int index) {
      if (pl == null || pl.getNumberOfPositions() <= 0
            || index < 0 || index >= pl.getNumberOfPositions()) {
         return null;
      }
      return pl.getPosition(index);
   }

   // --- Startup and shutdown ---

   private void prepareState(PositionList positionList,
         AutofocusPlugin autofocusDevice) throws Exception {
      String defaultZDrive = mmc.getFocusDevice();
      String defaultXYStage = mmc.getXYStageDevice();
      double z = getZStagePosition(defaultZDrive);
      double[] xy = getXYStagePosition(defaultXYStage);
      double exposure = mmc.getExposure();

      state.pause = false;
      state.stop = false;
      state.finished = false;
      state.lastWakeTime = jvmTimeMs();
      state.lastStagePositions.clear();
      state.lastStagePositions.put(defaultZDrive, z);
      if (xy != null) {
         state.lastStagePositions.put(defaultXYStage, xy);
      }
      state.referenceZ = z;
      state.startTime = jvmTimeMs();
      state.initAutoShutter = mmc.getAutoShutter();
      state.initExposure = exposure;
      state.initShutterState = mmc.getShutterOpen();
      state.cameraExposures.clear();
      state.cameraExposures.put(mmc.getCameraDevice(), exposure);
      state.defaultZDrive = defaultZDrive;
      state.defaultXYStage = defaultXYStage;
      state.autofocusDevice = autofocusDevice;
      state.positionList = positionList;
      state.initZPosition = z;
      state.initSystemState = MmUtils.getSystemConfigCached(mmc);
      state.initContinuousFocus = mmc.isContinuousFocusEnabled();
      state.initWidth = (int) mmc.getImageWidth();
      state.initHeight = (int) mmc.getImageHeight();
      state.binning = mmc.getProperty(mmc.getCameraDevice(), "Binning");
      state.bitDepth = (int) mmc.getImageBitDepth();
      state.pixelSizeUm = mmc.getPixelSizeUm();
      state.pixelSizeAffine = mmc.getPixelSizeAffineAsString();
      state.pixelType = MmUtils.getPixelType(mmc);
      state.lastPropertySettings.clear();
      state.shutterStates.clear();
   }

   private void cleanup() {
      try {
         MmUtils.attemptAll(
            () -> log("cleanup"),
            () -> { state.finished = true; },
            () -> {
               try {
                  if (mmc.isSequenceRunning()) {
                     mmc.stopSequenceAcquisition();
                  }
               } catch (Exception e) { throw new RuntimeException(e); }
            },
            () -> {
               try { stopTriggering(); }
               catch (Exception e) { throw new RuntimeException(e); }
            },
            () -> { activePropertySequences = null; },
            () -> { activeSliceSequence = null; },
            () -> {
               try { returnConfig(); }
               catch (Exception e) { throw new RuntimeException(e); }
            },
            () -> {
               try { mmc.setAutoShutter(state.initAutoShutter); }
               catch (Exception e) { throw new RuntimeException(e); }
            },
            () -> {
               try { setExposure(mmc.getCameraDevice(), state.initExposure); }
               catch (Exception e) { throw new RuntimeException(e); }
            },
            () -> {
               try { setStagePositionZ(state.defaultZDrive, state.initZPosition); }
               catch (Exception e) { throw new RuntimeException(e); }
            },
            () -> {
               try { setShutterOpen(state.initShutterState); }
               catch (Exception e) { throw new RuntimeException(e); }
            },
            () -> {
               try {
                  if (state.initContinuousFocus
                        && !mmc.isContinuousFocusEnabled()) {
                     enableContinuousFocus(true);
                  }
               } catch (Exception e) { throw new RuntimeException(e); }
            },
            () -> {
               if (gui instanceof MMStudio) {
                  ((MMStudio) gui).uiManager().frame()
                        .enableRoiButtons(true);
               }
            }
         );
      } catch (Throwable t) {
         ReportingUtils.showError(t, "Acquisition cleanup failed.");
      }
   }

   // --- Running events ---

   private List<Runnable> makeEventFns(AcqEvent event,
         BlockingQueue<TaggedImage> outQueue, AcqSettings settings) {
      int currentPosition = event.position;
      String zDrive = state.defaultZDrive;
      boolean checkZRef = zDrive != null && !zDrive.isEmpty()
            && (event.autofocus
                || (event.waitTimeMs != null && event.waitTimeMs > 1000));

      List<Runnable> fns = new ArrayList<>();
      fns.add(() -> log("#####", "BEGIN acquisition event"));

      if (event.newPosition) {
         MultiStagePosition msp = getMultiStagePosition(
               state.positionList, currentPosition);
         if (msp != null) {
            for (int i = 0; i < msp.size(); i++) {
               StagePosition sp = msp.get(i);
               String axis = sp.getStageDeviceLabel();
               final int numAxes = sp.numAxes;
               final double sx = sp.x;
               final double sy = sp.y;
               fns.add(() -> {
                  try {
                     log("BEGIN set position of stage", axis);
                     if (numAxes == 1) {
                        setStagePositionZ(axis, sx);
                     } else if (numAxes == 2) {
                        setStagePositionXY(axis, sx, sy);
                     }
                     log("END set position of stage", axis);
                  } catch (Exception e) {
                     throw new RuntimeException(e);
                  }
               });
            }
         }
      }

      fns.add(() -> log("BEGIN channel properties and exposure"));
      if (event.channel != null && event.channel.properties != null) {
         for (Map.Entry<List<String>, String> prop
               : event.channel.properties.entrySet()) {
            List<String> key = prop.getKey();
            String value = prop.getValue();
            fns.add(() -> {
               try {
                  setProperty(key, value);
               } catch (Exception e) {
                  throw new RuntimeException(e);
               }
            });
         }
      }
      fns.add(() -> {
         try {
            String camera = mmc.getCameraDevice();
            if (camera != null && !camera.isEmpty()) {
               setExposure(camera, event.exposure);
            }
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      });
      fns.add(() -> log("END channel properties and exposure"));

      if (checkZRef) {
         fns.add(() -> {
            try {
               log("BEGIN recall-z-reference");
               recallZReference(currentPosition);
               log("END recall-z-reference");
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });
      }

      fns.add(() -> {
         try {
            if (event.waitTimeMs != null) {
               acqSleep(event.waitTimeMs);
            }
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      });

      if (event.autofocus) {
         fns.add(() -> {
            try {
               waitForPendingDevices();
               runAutofocus();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });
      }

      if (checkZRef) {
         fns.add(() -> {
            try {
               log("BEGIN store/update z reference");
               storeZReference(currentPosition);
               updateZPositions(currentPosition);
               log("END store/update z reference");
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });
      }

      if (zDrive != null && !zDrive.isEmpty()) {
         fns.add(() -> {
            try {
               log("BEGIN set z position");
               double z = computeZPosition(event);
               setStagePositionZ(zDrive, z);
               log("END set z position");
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });
      }

      if (event.runnables != null) {
         for (Runnable r : event.runnables) {
            fns.add(() -> {
               log("BEGIN run one runnable");
               r.run();
               log("END run one runnable");
            });
         }
      }

      fns.add(() -> {
         try {
            waitForPendingDevices();
            log("BEGIN acquire");
            expose(event);
            collect(event, outQueue, settings);
            stopTriggering();
            log("END acquire");
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      });

      fns.add(() -> log("#####", "END acquisition event"));
      return fns;
   }

   private void execute(List<Runnable> eventFns) throws Exception {
      for (Runnable fn : eventFns) {
         if (state.stop) {
            break;
         }
         fn.run();
         awaitResume();
      }
   }

   private void runAcquisition(AcqSettings settings,
         BlockingQueue<TaggedImage> outQueue, boolean cleanup,
         PositionList positionList, AutofocusPlugin autofocusDevice) {
      try {
         log("Starting MD Acquisition");
         if (gui != null) {
            gui.live().setLiveMode(false);
            if (gui instanceof MMStudio) {
               ((MMStudio) gui).uiManager().frame()
                     .enableRoiButtons(false);
            }
         }
         prepareState(settings.usePositionList ? positionList : null,
               autofocusDevice);
         CoreOps coreOps = CoreOps.fromCMMCore(mmc);
         Iterable<AcqEvent> acqSeq = SequenceGenerator.generateAcqSequence(
               settings, attachedRunnables, coreOps);
         List<Runnable> allFns = new ArrayList<>();
         for (AcqEvent event : acqSeq) {
            allFns.addAll(makeEventFns(event, outQueue, settings));
         }
         execute(allFns);
      } catch (Throwable t) {
         ReportingUtils.showError(t, "Acquisition failed.");
      } finally {
         if (cleanup) {
            cleanup();
         }
         if (state.stop) {
            if (!outQueue.offer(TaggedImageQueue.POISON)) {
               outQueue.clear();
               try {
                  outQueue.put(TaggedImageQueue.POISON);
               } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
               }
            }
         } else {
            try {
               outQueue.put(TaggedImageQueue.POISON);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }
         log("acquisition thread exiting");
      }
   }

   private AcqSettings convertSettings(SequenceSettings ss,
         PositionList pl) throws Exception {
      return AcqSettings.fromSequenceSettings(ss, pl, mmc);
   }

   // --- Summary metadata ---

   @SuppressWarnings("deprecation")
   private JSONObject makeSummaryMetadata(AcqSettings settings,
         PositionList positionList) throws Exception {
      int depth = (int) mmc.getBytesPerPixel();
      List<AcqChannel> channels = settings.channels;
      List<String> cameraChannelNames = getCameraChannelNames();
      int numCameraChannels = cameraChannelNames.size();

      List<AcqChannel> simpleChannels;
      if (!channels.isEmpty()) {
         simpleChannels = channels;
      } else {
         AcqChannel defaultCh = new AcqChannel();
         defaultCh.name = "Default";
         defaultCh.color = Color.WHITE;
         simpleChannels = new ArrayList<>();
         simpleChannels.add(defaultCh);
      }

      List<AcqChannel> superChans = new ArrayList<>();
      for (AcqChannel ch : simpleChannels) {
         if (numCameraChannels > 1) {
            for (String camName : cameraChannelNames) {
               AcqChannel sc = new AcqChannel();
               sc.name = superChannelName(ch.name, camName,
                     numCameraChannels);
               sc.color = ch.color;
               superChans.add(sc);
            }
         } else {
            superChans.add(ch);
         }
      }

      List<String> chNames = new ArrayList<>();
      for (AcqChannel sc : superChans) {
         chNames.add(sc.name);
      }

      List<Integer> chColors = new ArrayList<>();
      if (simpleChannels.size() == superChans.size()) {
         for (String name : chNames) {
            int idx = chNames.indexOf(name);
            if (idx >= 0 && idx < superChans.size()
                  && superChans.get(idx).color != null) {
               chColors.add(superChans.get(idx).color.getRGB());
            }
         }
      }

      String computer;
      try {
         computer = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
         computer = "";
      }

      String[] axisOrder;
      if (settings.slicesFirst) {
         axisOrder = new String[] { Coords.Z, Coords.CHANNEL };
      } else {
         axisOrder = new String[] { Coords.CHANNEL, Coords.Z };
      }
      String[] outerOrder;
      if (settings.timeFirst) {
         outerOrder = new String[] { Coords.TIME, Coords.STAGE_POSITION };
      } else {
         outerOrder = new String[] { Coords.STAGE_POSITION, Coords.TIME };
      }

      double zStep = 0;
      if (settings.slices != null && settings.slices.size() > 1) {
         zStep = settings.slices.get(1) - settings.slices.get(0);
      }

      int[] roi = MmUtils.getCameraRoi(mmc);

      JSONObject json = new JSONObject();
      json.put("AxisOrder", new JSONArray(Arrays.asList(
         axisOrder[0], axisOrder[1], outerOrder[0], outerOrder[1])));
      json.put("BitDepth", mmc.getImageBitDepth());
      json.put("CameraTimeout", settings.cameraTimeout);
      json.put("Channels", Math.max(1, superChans.size()));
      json.put("ChNames", new JSONArray(chNames));
      json.put("ChColors", new JSONArray(chColors));
      json.put("ChannelGroup", settings.channelGroup);
      json.put("Comment", settings.comment);
      json.put("ComputerName", computer);
      json.put("Depth", depth);
      json.put("Directory",
            settings.save ? (settings.root != null ? settings.root : "") : "");
      json.put("Frames", Math.max(1,
            settings.frames != null ? settings.frames.size() : 0));
      json.put("GridColumn", 0);
      json.put("GridRow", 0);
      json.put("Height", mmc.getImageHeight());
      if (settings.usePositionList && positionList != null) {
         json.put("InitialPositionList",
               summarizePositionList(positionList));
      }
      json.put("Interval_ms", settings.intervalMs);
      json.put("CustomIntervals_ms", new JSONArray(
            settings.customIntervalsMs != null
                  ? settings.customIntervalsMs : new ArrayList<>()));
      json.put("IJType", getIJType(depth));
      json.put("KeepShutterOpenChannels", settings.keepShutterOpenChannels);
      json.put("KeepShutterOpenSlices", settings.keepShutterOpenSlices);
      json.put("MicroManagerVersion",
            gui != null ? gui.compat().getVersion() : "N/A");
      json.put("PixelSize_um", mmc.getPixelSizeUm());
      json.put("PixelSizeAffine", mmc.getPixelSizeAffineAsString());
      json.put("PixelType", MmUtils.getPixelType(mmc));
      json.put("Positions", Math.max(1,
            settings.positions != null ? settings.positions.size() : 0));
      json.put("Prefix",
            settings.save ? (settings.prefix != null ? settings.prefix : "") : "");
      json.put("ProfileName",
            gui != null ? gui.profile().getProfileName() : "");
      JSONArray roiArr = new JSONArray();
      for (int r : roi) {
         roiArr.put(r);
      }
      json.put("ROI", roiArr);
      json.put("Slices", Math.max(1,
            settings.slices != null ? settings.slices.size() : 0));
      json.put("SlicesFirst", settings.slicesFirst);
      json.put("Source", "Micro-Manager");
      json.put("StartTime", MmUtils.getCurrentTimeStr());
      json.put("TimeFirst", settings.timeFirst);
      json.put("UserName", System.getProperty("user.name"));
      json.put("UUID", UUID.randomUUID());
      json.put("Width", mmc.getImageWidth());
      json.put("z-step_um", zStep);
      return json;
   }

   private static int getIJType(int depth) {
      switch (depth) {
         case 1: return 0; // ImagePlus.GRAY8
         case 2: return 1; // ImagePlus.GRAY16
         case 4: return 4; // ImagePlus.COLOR_RGB
         case 8: return 64;
         default: return 0;
      }
   }

   @SuppressWarnings("deprecation")
   private JSONArray summarizePositionList(PositionList positionList)
         throws Exception {
      JSONArray arr = new JSONArray();
      for (int pi = 0; pi < positionList.getNumberOfPositions(); pi++) {
         MultiStagePosition msp = positionList.getPosition(pi);
         JSONObject devCoords = new JSONObject();
         for (int i = 0; i < msp.size(); i++) {
            StagePosition sp = msp.get(i);
            String devName = sp.getStageDeviceLabel();
            if (sp.numAxes == 1) {
               JSONArray coords = new JSONArray();
               coords.put(sp.x);
               devCoords.put(devName, coords);
            } else if (sp.numAxes == 2) {
               JSONArray coords = new JSONArray();
               coords.put(sp.x);
               coords.put(sp.y);
               devCoords.put(devName, coords);
            }
         }
         JSONObject posJson = new JSONObject();
         posJson.put("Label", msp.getLabel());
         posJson.put("GridRowIndex", msp.getGridRow());
         posJson.put("GridColumnIndex", msp.getGridColumn());
         posJson.put("DefaultXYStage", msp.getDefaultXYStage());
         posJson.put("DefaultZStage", msp.getDefaultZStage());
         posJson.put("DeviceCoordinatesUm", devCoords);
         arr.put(posJson);
      }
      return arr;
   }

   // --- Logging ---

   private void log(String... parts) {
      MmUtils.log(mmc, parts);
   }

   // --- run() implementation ---

   private BlockingQueue<TaggedImage> doRun(AcqSettings settings,
         boolean cleanup, PositionList positionList,
         AutofocusPlugin autofocusDevice) {
      state.stop = false;
      state.pause = false;
      state.finished = false;

      LinkedBlockingQueue<TaggedImage> outQueue =
            new LinkedBlockingQueue<>(10);

      try {
         state.summaryMetadata = makeSummaryMetadata(settings,
               positionList);
      } catch (Exception e) {
         ReportingUtils.logError(e, "Failed to create summary metadata");
         state.summaryMetadata = new JSONObject();
      }

      if (state.stop) {
         return null;
      }

      Thread acqThread = new Thread(
            () -> runAcquisition(settings, outQueue, cleanup,
                  positionList, autofocusDevice),
            "AcquisitionEngine2010J Thread");
      state.acqThread = acqThread;
      acqThread.start();
      return outQueue;
   }

   // --- IAcquisitionEngine2010 implementation ---

   @Override
   public BlockingQueue<TaggedImage> run(SequenceSettings sequenceSettings) {
      return run(sequenceSettings, true);
   }

   @Override
   public BlockingQueue<TaggedImage> run(SequenceSettings sequenceSettings,
         boolean cleanup, PositionList positionList,
         AutofocusPlugin device) {
      try {
         AcqSettings settings = convertSettings(sequenceSettings,
               positionList);
         return doRun(settings, cleanup, positionList, device);
      } catch (Exception e) {
         ReportingUtils.logError(e, "Failed to start acquisition");
         return null;
      }
   }

   @Override
   public BlockingQueue<TaggedImage> run(SequenceSettings sequenceSettings,
         boolean cleanup) {
      try {
         PositionList pl = gui != null ? gui.positions().getPositionList()
               : new PositionList();
         AcqSettings settings = convertSettings(sequenceSettings, pl);
         AutofocusPlugin af = gui != null
               ? gui.getAutofocusManager().getAutofocusMethod() : null;
         return doRun(settings, cleanup, pl, af);
      } catch (Exception e) {
         ReportingUtils.logError(e, "Failed to start acquisition");
         return null;
      }
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return state.summaryMetadata;
   }

   @Override
   public void pause() {
      log("pause requested!");
      state.pause = true;
   }

   @Override
   public void resume() {
      log("resume requested!");
      state.pause = false;
   }

   @Override
   public void stop() {
      log("stop requested!");
      state.stop = true;
      CountDownLatch sleepy = state.sleepy;
      if (sleepy != null) {
         sleepy.countDown();
      }
   }

   @Override
   public boolean isRunning() {
      Thread t = state.acqThread;
      return t != null && t.isAlive();
   }

   @Override
   public boolean isPaused() {
      return state.pause;
   }

   @Override
   public boolean isFinished() {
      return state.finished;
   }

   @Override
   public boolean stopHasBeenRequested() {
      return state.stop;
   }

   @Override
   public long nextWakeTime() {
      Long nwt = state.nextWakeTime;
      return nwt != null ? nwt : -1;
   }

   @Override
   public void attachRunnable(int frame, int position, int channel,
         int slice, Runnable runnable) {
      synchronized (attachedRunnables) {
         attachedRunnables.add(new SequenceGenerator.AttachedRunnable(
               frame, position, channel, slice, runnable));
      }
   }

   @Override
   public void clearRunnables() {
      synchronized (attachedRunnables) {
         attachedRunnables.clear();
      }
   }
}

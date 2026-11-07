package org.micromanager.internal.jacque;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import mmcorej.org.json.JSONObject;
import org.micromanager.AutofocusPlugin;
import org.micromanager.PositionList;

final class EngineState {
   public volatile boolean stop;
   public volatile boolean pause;
   public boolean finished;
   public long lastWakeTime;
   public long startTime;
   public long lastImageTime;
   public Thread acqThread;
   public CountDownLatch sleepy;
   public Long nextWakeTime;
   public double referenceZ;
   public double initZPosition;
   public double initExposure;
   public double pixelSizeUm;
   public int initWidth;
   public int initHeight;
   public int bitDepth;
   public boolean initAutoShutter;
   public boolean initShutterState;
   public boolean initContinuousFocus;
   public boolean liveModeOn;
   public String defaultZDrive;
   public String defaultXYStage;
   public String binning;
   public String pixelType;
   public String pixelSizeAffine;
   public Map<String, Object> lastStagePositions;
   public Map<String, Double> cameraExposures;
   public Map<String, Boolean> shutterStates;
   public Map<String, Map<String, String>> lastPropertySettings;
   public Map<List<String>, String> initSystemState;
   public AutofocusPlugin autofocusDevice;
   public PositionList positionList;
   public JSONObject summaryMetadata;
   public Map<String, String> systemState;
   public Double burstTimeOffset;

   public EngineState() {
      lastStagePositions = new HashMap<>();
      cameraExposures = new HashMap<>();
      shutterStates = new HashMap<>();
      lastPropertySettings = new HashMap<>();
      systemState = new HashMap<>();
   }
}

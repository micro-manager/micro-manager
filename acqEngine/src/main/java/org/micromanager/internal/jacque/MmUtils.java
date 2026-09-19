package org.micromanager.internal.jacque;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import mmcorej.Configuration;
import mmcorej.DoubleVector;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;

final class MmUtils {

   private static final SimpleDateFormat IMAGE_DATE_FORMAT =
         new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z");

   private MmUtils() {
   }

   public static void log(ExecutionCoreOps core, String... parts) {
      StringBuilder sb = new StringBuilder("[AE] ");
      for (int i = 0; i < parts.length; i++) {
         if (i > 0) {
            sb.append(' ');
         }
         sb.append(parts[i]);
      }
      core.logMessage(sb.toString(), true);
   }

   public static synchronized String getCurrentTimeStr() {
      return IMAGE_DATE_FORMAT.format(new Date());
   }

   public static String getPixelType(ExecutionCoreOps core) throws Exception {
      int components = (int) core.getNumberOfComponents();
      String prefix;
      if (components == 1) {
         prefix = "GRAY";
      } else if (components == 4) {
         prefix = "RGB";
      } else {
         prefix = String.valueOf(components);
      }
      return prefix + (8 * core.getBytesPerPixel());
   }

   public static int[] getCameraRoi(ExecutionCoreOps core) throws Exception {
      int[] x = new int[1];
      int[] y = new int[1];
      int[] w = new int[1];
      int[] h = new int[1];
      core.getROI(x, y, w, h);
      return new int[] { x[0], y[0], w[0], h[0] };
   }

   @SuppressWarnings("unchecked")
   public static Object jsonToData(Object json) {
      try {
         if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            Map<String, Object> map = new HashMap<>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
               String key = keys.next();
               Object val = obj.isNull(key) ? null : obj.get(key);
               map.put(key, jsonToData(val));
            }
            return map;
         } else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            List<Object> list = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
               list.add(jsonToData(arr.get(i)));
            }
            return list;
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      return json;
   }

   public static Map<List<String>, String> configStruct(
         Configuration config) throws Exception {
      Map<List<String>, String> result = new HashMap<>();
      for (long i = 0; i < config.size(); i++) {
         PropertySetting ps = config.getSetting(i);
         List<String> key = new ArrayList<>(2);
         key.add(ps.getDeviceLabel());
         key.add(ps.getPropertyName());
         result.put(key, ps.getPropertyValue());
      }
      return result;
   }

   public static Map<String, String> mapConfig(Configuration config)
         throws Exception {
      Map<String, String> result = new HashMap<>();
      for (long i = 0; i < config.size(); i++) {
         PropertySetting ps = config.getSetting(i);
         result.put(ps.getDeviceLabel() + "-" + ps.getPropertyName(),
               ps.getPropertyValue());
      }
      return result;
   }

   public static Map<List<String>, String> getSystemConfigCached(
         ExecutionCoreOps core) throws Exception {
      return configStruct(core.getSystemStateCache());
   }

   public static String getPropertyValue(ExecutionCoreOps core, String dev,
         String prop) throws Exception {
      if (core.hasProperty(dev, prop)) {
         return core.getProperty(dev, prop);
      }
      return null;
   }

   public static Double getMspZPosition(PositionList positionList,
         int idx, String zStage) {
      if (positionList == null
            || positionList.getNumberOfPositions() <= 0) {
         return null;
      }
      MultiStagePosition msp = positionList.getPosition(idx);
      if (msp == null) {
         return null;
      }
      StagePosition sp = msp.get(zStage);
      if (sp == null) {
         return null;
      }
      return sp.x;
   }

   public static void setMspZPosition(PositionList positionList,
         int idx, String zStage, double z) {
      if (positionList == null
            || positionList.getNumberOfPositions() <= 0) {
         return;
      }
      MultiStagePosition msp = positionList.getPosition(idx);
      if (msp == null) {
         return;
      }
      StagePosition sp = msp.get(zStage);
      if (sp == null) {
         return;
      }
      sp.x = z;
   }

   public static StrVector toStrVector(List<String> items) {
      StrVector v = new StrVector();
      for (String s : items) {
         v.add(s);
      }
      return v;
   }

   public static DoubleVector toDoubleVector(List<Double> items) {
      DoubleVector v = new DoubleVector();
      for (Double d : items) {
         v.add(d);
      }
      return v;
   }

   public static void attemptAll(Runnable... actions) {
      Throwable firstError = null;
      for (Runnable action : actions) {
         try {
            action.run();
         } catch (Throwable t) {
            if (firstError == null) {
               firstError = t;
            }
         }
      }
      if (firstError != null) {
         if (firstError instanceof RuntimeException) {
            throw (RuntimeException) firstError;
         }
         if (firstError instanceof Error) {
            throw (Error) firstError;
         }
         throw new RuntimeException(firstError);
      }
   }
}

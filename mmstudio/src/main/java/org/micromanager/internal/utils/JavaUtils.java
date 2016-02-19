package org.micromanager.internal.utils;

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class JavaUtils {

   /**
    * Call a private method without arguments.
    * @param obj
    * @param theClass
    * @param methodName
    * @return 
    * @throws java.lang.NoSuchMethodException
    * @throws java.lang.IllegalAccessException
    * @throws java.lang.reflect.InvocationTargetException
    */
   public static Object invokeRestrictedMethod(Object obj, Class theClass, String methodName) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      return invokeRestrictedMethod(obj, theClass, methodName, (Object) null);
   }

   /**
    * Call a private method using reflection. Use looks like
    * invokeRestrictedMethod(Object obj, Class theClass, String methodName, Object param1, Class paramType1, Object param2, Class paramType2, ...)
    * @param obj
    * @param theClass
    * @param methodName
    * @param paramsAndTypes
    * @return 
    * @throws java.lang.NoSuchMethodException
    * @throws java.lang.IllegalAccessException
    * @throws java.lang.reflect.InvocationTargetException
    */
   public static Object invokeRestrictedMethod(Object obj, Class theClass, String methodName, Object... paramsAndTypes) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      Object[] params;
      Class[] paramTypes;
      int l;
      if (paramsAndTypes != null) {
         l = paramsAndTypes.length;
         params = new Object[l / 2];
         paramTypes = new Class[l / 2];
      } else {
         l = 0;
         params = null;
         paramTypes = null;
      }

      for (int i = 0; i < l / 2; ++i) {
         params[i] = paramsAndTypes[i * 2];
         paramTypes[i] = (Class) paramsAndTypes[i * 2 + 1];
      }
      return invokeRestrictedMethod(obj, theClass, methodName, params, paramTypes);
   }

   /*
    * Invoked a method of a private or protected field.
    * Pass a null first argument for static methods.
    */
   public static Object invokeRestrictedMethod(Object obj, Class<?> theClass, String methodName, Object[] params, Class[] paramTypes) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      Method method = theClass.getDeclaredMethod(methodName, paramTypes);
      Object result;

      boolean wasAccessible = method.isAccessible();
      method.setAccessible(true);
      if (params == null) {
         result = method.invoke(obj);
      } else {
         result = method.invoke(obj, params);
      }
      method.setAccessible(wasAccessible);
      return result;
   }

   /*
    * Returns a value of a private or protected field. Method of last resort!
    * Pass a null first argument for static fields.
    */
   public static Object getRestrictedFieldValue(Object obj, Class theClass, String fieldName) throws NoSuchFieldException {
      Field field = theClass.getDeclaredField(fieldName);
      field.setAccessible(true);
      try {
         return field.get(obj);
      } catch (IllegalAccessException ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   /*
    * Allows private or protected field values to be changed. Method of
    * last resort!
    * Pass a null first argument for static fields.
    */
   public static void setRestrictedFieldValue(Object obj, Class theClass, String fieldName, Object value) throws NoSuchFieldException {
      Field field = theClass.getDeclaredField(fieldName);
      field.setAccessible(true);
      try {
         field.set(obj, value);
      } catch (IllegalAccessException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public static Dimension getScreenDimensions() {
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] gs = ge.getScreenDevices();
      Rectangle bounds = gs[0].getDefaultConfiguration().getBounds();
      return new Dimension(bounds.width, bounds.height);
   }

   public static File createDirectory(String dirPath) throws Exception {
      File dir = new File(dirPath);
      if (!dir.exists()) {
         if (!dir.mkdirs()) {
            throw new Exception("Unable to create directory " + dirPath);
         }
      }
      return dir;
   }

   public static boolean isWindows() {
      String os = System.getProperty("os.name").toLowerCase();
      return (os.contains("win"));
   }

   public static boolean isMac() {
      String os = System.getProperty("os.name").toLowerCase();
      return (os.contains("mac"));
   }

   public static boolean isUnix() {
      String os = System.getProperty("os.name").toLowerCase();
      //linux or unix
      return (os.contains("nix") || os.contains("nux"));
   }

   public static void sleep(int time_ms) {
      try {
         Thread.sleep(time_ms);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      }
   }

   /**
    * Find out how much unused memory (in bytes) is still available 
    * for the JVM to use.
    * On a MacBook Pro this call takes 0.5 usec.
    * @return amount of unused memory in bytes
    */
   public static long getAvailableUnusedMemory() {
      Runtime r = Runtime.getRuntime();
      return   r.maxMemory()   // how large the JVM heap can get
             - r.totalMemory() // current size of heap (<= r.maxMemory())
             + r.freeMemory(); // how much of currently allocated heap is unused
   }

   public static String getApplicationDataPath() {
      if (isMac()) {
         return System.getenv("HOME") + "/Library/Application Support/Micro-Manager/";
      }
      if (isWindows()) {
         String os = System.getProperty("os.name").toLowerCase();
         if (os.contains("xp")) {
            return System.getenv("APPDATA") + "/Micro-Manager/";
         } else { // Assume Vista or newer
            return System.getenv("LOCALAPPDATA") + "/Micro-Manager/";
         }
      }
      if (isUnix()) {
         return System.getenv("HOME") + "/.config/Micro-Manager/";
      }
      return null;
   }

   public static void createApplicationDataPathIfNeeded() {
      String path = getApplicationDataPath();
      if (path == null) {
         ReportingUtils.logError("Unable to determine application data path");
         return;
      }
      File file = new File(path);
      if (!file.exists()) {
         file.mkdirs();
      }
   }

   public static void printAllStackTraces() {
      System.err.println("\n\nDumping all stack traces:");
      Map<Thread, StackTraceElement[]> liveThreads = Thread.getAllStackTraces();
      for (Thread key : liveThreads.keySet()) {
         System.err.println("Thread " + key.getName());
         StackTraceElement[] trace = liveThreads.get(key);
         for (StackTraceElement trace1 : trace) {
            System.err.println("\tat " + trace1);
         }
      }
      System.err.println("End all stack traces. =============");
   }

   /**
    * Return the path of the jar containing this class.
    * Copied from the same-named method in MMCoreJ.i.
    */
   public static String getJarPath() {
      String classPath = "/" + JavaUtils.class.getName();
      classPath = classPath.replace(".", "/") + ".class";
      try {
         URL url = JavaUtils.class.getResource(classPath);
         String path = URLDecoder.decode(url.getPath(), "UTF-8");
         path = URLDecoder.decode(new URL(path).getPath(), "UTF-8");
         int bang = path.indexOf("!");
         if (bang >= 0) {
            path = path.substring(0, bang);
         }
         return path;
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Unable to get path of jar");
         return "";
      }
   }
}

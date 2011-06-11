package org.micromanager.utils;

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;
import org.micromanager.MMStudioMainFrame;

public class JavaUtils {

   private static final String BACKING_STORE_AVAIL = "BackingStoreAvail";

   public static List<Class<?>> findClasses(File directory, int recursionLevel) throws ClassNotFoundException {
      List<Class<?>> classes = new ArrayList<Class<?>>();
      if (!directory.exists()) {
         return classes;
      }
      File[] files = directory.listFiles();
      URL url;
      try {
         url = directory.toURI().toURL();

         for (File file : files) {
            if (file.isDirectory() && recursionLevel > 0) {
               try {
                  addFile(file);
               } catch (IOException e1) {
                  ReportingUtils.logError(e1);
               }
               classes.addAll(findClasses(file, recursionLevel - 1));
            } else if (file.getName().endsWith(".class")) {
               try {
                  addFile(file);
               } catch (IOException e) {
                  ReportingUtils.logError(e);
               }
               try {
                  classes.add(Class.forName(stripFilenameExtension(file.getName())));
               } catch (NoClassDefFoundError e2) {
                  ReportingUtils.logError(e2, "Not found:" + url + "  :  " + file.getName());
               }
            } else if (file.getName().endsWith(".jar")) {
               try {
                  addURL(new URL("jar:file:" + file.getAbsolutePath() + "!/"));
                  JarInputStream jarFile = new JarInputStream(new FileInputStream(file));
                  JarEntry jarEntry;
                  do {
                     jarEntry = jarFile.getNextJarEntry();
                     if (jarEntry != null) {
                        String classFile = jarEntry.getName();
                        if (classFile.endsWith(".class")) {
                           String className = stripFilenameExtension(classFile).replace("/", ".");
                           try {
                              //String cl = JavaUtils.class.getClassLoader().toString();
                              //System.out.println("Attempting to load: "+className + " in " + cl);
                              //long t1 = System.currentTimeMillis();
                              classes.add(Class.forName(className));
                              //System.out.println(className +": " + (System.currentTimeMillis() - t1));
                           } catch (Throwable e3) {
                              ReportingUtils.logError(e3, "Failed to load "+className + " " + e3.getCause());
                              for (StackTraceElement t:e3.getCause().getStackTrace()) {
                                 ReportingUtils.logMessage(t.toString());
                              }
                           }
                        }
                     }
                  } while (jarEntry != null);
               } catch (Exception e) {
                  ReportingUtils.logError(e);
               }
            }
         }

      } catch (MalformedURLException e1) {
         ReportingUtils.logError(e1);
      }

      return classes;
   }

   private static String stripFilenameExtension(String filename) {
      int i = filename.lastIndexOf('.');
      if (i > 0) {
         return filename.substring(0, i);
      } else {
         return filename;
      }
   }
// Borrowed from http://forums.sun.com/thread.jspa?threadID=300557
   private static final Class<?>[] parameters = new Class[]{URL.class};

   public static void addFile(String s) throws IOException {
      File f = new File(s);
      addFile(f);
   }//end method

   public static void addFile(File f) throws IOException {
      addURL(f.toURI().toURL());
   }//end method

   public static void addURL(URL u) throws IOException {

      URLClassLoader loader = (URLClassLoader) JavaUtils.class.getClassLoader();
      Class<?> sysclass = URLClassLoader.class;

      try {
         Method method = sysclass.getDeclaredMethod("addURL", parameters);
         method.setAccessible(true);
         method.invoke(loader, new Object[]{u});
      } catch (Throwable t) {
         ReportingUtils.logError(t);
         throw new IOException("Error, could not add URL to system classloader");
      }//end try catch

   }//end method

   /*
    * Call a private method without arguments.
    */
   public static Object invokeRestrictedMethod(Object obj, Class theClass, String methodName) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      return invokeRestrictedMethod(obj, theClass, methodName, (Object) null);
   }

   /*
    * Call a private method using reflection. Use looks like
    * invokeRestrictedMethod(Object obj, Class theClass, String methodName, Object param1, Class paramType1, Object param2, Class paramType2, ...)
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

   public static Object invokeRestrictedMethod(Object obj, Class theClass, String methodName, Object[] params, Class[] paramTypes) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
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

   public static void setRestrictedFieldValue(Object obj, Class theClass, String fieldName, Object value) throws NoSuchFieldException {
      Field field = theClass.getDeclaredField(fieldName);
      field.setAccessible(true);
      try {
         field.set(obj, value);
      } catch (IllegalAccessException ex) {
         ReportingUtils.logError(ex);
      }
   }

   /*
    * Test whether preference can be written to disk
    * from:
    * http://java.sun.com/j2se/1.4.2/docs/guide/lang/preferences.html#prefs-usage-backingstore
    */
   public static boolean backingStoreAvailable(Preferences prefs) {
      try {
         boolean oldValue = prefs.getBoolean(BACKING_STORE_AVAIL, false);
         prefs.putBoolean(BACKING_STORE_AVAIL, !oldValue);
         prefs.flush();
      } catch (BackingStoreException e) {
         return false;
      }
      return true;
   }

   /*
    * Serializes an object and stores it in Preferences
    */
   public static void putObjectInPrefs(Preferences prefs, String key, Serializable obj) {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      try {
         ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
         objectStream.writeObject(obj);
      } catch (Exception e) {
         ReportingUtils.logError(e, "Failed to save object in Preferences.");
         return;
      }
      byte[] serialBytes = byteStream.toByteArray();
      prefs.putByteArray(key, serialBytes);
   }

   /*
    * Retrieves an object from Preferences (deserialized).
    */
   public static Object getObjectFromPrefs(Preferences prefs, String key, Object def) {
      byte[] serialBytes = prefs.getByteArray(key, new byte[0]);
      if (serialBytes.length == 0) {
         return def;
      }
      ByteArrayInputStream byteStream = new ByteArrayInputStream(serialBytes);
      try {
         ObjectInputStream objectStream = new ObjectInputStream(byteStream);
         return objectStream.readObject();
      } catch (Exception e) {
         ReportingUtils.logError(e, "Failed to get object from preferences.");
         return def;
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
            throw new Exception("Unable to create directory.");
         }
      }
      return dir;
   }

   public static boolean isWindows() {
      String os = System.getProperty("os.name").toLowerCase();
      return (os.indexOf("win") >= 0);
   }

   public static boolean isMac() {
      String os = System.getProperty("os.name").toLowerCase();
      return (os.indexOf("mac") >= 0);
   }

   public static boolean isUnix() {
      String os = System.getProperty("os.name").toLowerCase();
      //linux or unix
      return (os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0);
   }

   public static void sleep(int time_ms) {
      try {
         Thread.sleep(time_ms);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public static void writeTextFile(String filepath, String text) {
      BufferedWriter writer = null;
      try {
         writer = new BufferedWriter(new FileWriter(filepath));
         writer.write(text);
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      } finally {
         if (writer != null) {
            try {
               writer.close();
            } catch (IOException ex) {
               ReportingUtils.logError(ex);
            }
         }
      }
   }

   static public String readTextFile(String filepath) {
      File f = new File(filepath);
      if (!f.exists()) {
         return null;
      }

      StringBuilder contents = new StringBuilder();

      try {
         //use buffering, reading one line at a time
         //FileReader always assumes default encoding is OK!
         BufferedReader input = new BufferedReader(new FileReader(filepath));
         try {
            String line = null; //not declared within while loop
        /*
             * readLine is a bit quirky :
             * it returns the content of a line MINUS the newline.
             * it returns null only for the END of the stream.
             * it returns an empty String if two newlines appear in a row.
             */
            while ((line = input.readLine()) != null) {
               contents.append(line);
               contents.append(System.getProperty("line.separator"));
            }
         } finally {
            input.close();
         }
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }

      return contents.toString();
   }

   /*
    * Find out how much unused memory (in bytes)
    * is still available for the JVM to use.
    * On a MacBook Pro this call takes 0.5 usec.
    */
   public static long getAvailableUnusedMemory() {
      Runtime r = Runtime.getRuntime();
      return   r.maxMemory()   // how large the JVM heap can get
             - r.totalMemory() // current size of heap (<= r.maxMemory())
             + r.freeMemory(); // how much of currently allocated heap is unused
   }

   /**
    * Borrowed from Java 1.6 java.utils.Arrays
    *
    * Copies elements in original array to a new array, from index
    * start(inclusive) to end(exclusive). The first element (if any) in the new
    * array is original[from], and other elements in the new array are in the
    * original order. The padding value whose index is bigger than or equal to
    * original.length - start is null.
    *
    * @param <T>
    *            type of element in array
    *
    * @param original
    *            the original array
    * @param start
    *            the start index, inclusive
    * @param end
    *            the end index, exclusive, may bigger than length of the array
    * @return the new copied array
    * @throws ArrayIndexOutOfBoundsException
    *             if start is smaller than 0 or bigger than original.length
    * @throws IllegalArgumentException
    *             if start is bigger than end
    * @throws NullPointerException
    *             if original is null
    *
    */
   @SuppressWarnings("unchecked")
   public static <T> T[] copyOfRange(T[] original, int start, int end) {
      if (original.length >= start && 0 <= start) {
         if (start <= end) {
            int length = end - start;
            int copyLength = Math.min(length, original.length - start);
            T[] copy = (T[]) Array.newInstance(original.getClass().getComponentType(), length);
            System.arraycopy(original, start, copy, 0, copyLength);
            return copy;
         }
         throw new IllegalArgumentException();
      }
      throw new ArrayIndexOutOfBoundsException();
   }

   public static String getApplicationDataPath() {
      if (isMac()) {
         return System.getenv("HOME")+"/Library/Application Support/Micro-Manager/";
      }
      if (isWindows()) {
         String os = System.getProperty("os.name").toLowerCase();
         if (os.indexOf("xp") >= 0) {
            return System.getenv("USERPROFILE") + "/Local Settings/Application Data/Micro-Manager/";
         } else if ((os.indexOf("windows 7") >= 0) || (os.indexOf("windows vista") >= 0)) {
            return System.getenv("USERPROFILE") + "/AppData/Local/Micro-Manager/";
         }
      }
      if (isUnix()) {
         return System.getenv("HOME") + "/.config/Micro-Manager/";
      }
      return null;
   }

}

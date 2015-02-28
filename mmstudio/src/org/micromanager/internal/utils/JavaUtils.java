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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class JavaUtils {
 
   /**
    * Add directories and JARs to the classpath, and return the classes found
    * in the process.
    *
    * This method does two things that are really completely separate tasks.
    *
    * First, it adds the given directory and all JARs directly in directory to
    * the search path of the system class loader. If recursionLevel is greater
    * than 0, it does the same with subdirectories, up to that level of
    * nesting.
    *
    * Second, it finds all classes in the directories and JARs that were added
    * to the class path in the first step, and returns them. Classes are found
    * anywhere within the JARs, or as .class files directly within directories.
    *
    * There is no support for .class files contained in a hierarchy of
    * directories representing the package names (they will be loadable because
    * the directory is in the search path, but they will not be included in the
    * returned list unless they are within the recursionLevel).
    *
    * On most errors, an empty list is returned and the error is logged.
    *
    * @param directory The directory to search for classes
    * @param recursionLevel Nesting level for searching subdirectories
    * @return The discovered classes
    */
   public static List<Class<?>> findAndLoadClasses(File directory, int recursionLevel) {
      List<Class<?>> classes = new ArrayList<Class<?>>();
      if (!directory.exists()) {
         return classes;
      }

      final URL directoryURL;
      try {
         directoryURL = directory.toURI().toURL();
      }
      catch (MalformedURLException e) {
         ReportingUtils.logError(e, "Failed to search for classes");
         return classes;
      }

      try {
         addURL(directoryURL);
      }
      catch (IOException ignore) {
         // Logged by addURL()
      }

      File[] files = directory.listFiles();
      for (File file : files) {
         final String fileName = file.getName();
         if (file.isDirectory() && recursionLevel > 0) {
            classes.addAll(findAndLoadClasses(file, recursionLevel - 1));
         } else if (fileName.endsWith(".class")) {
            final String className = stripFilenameExtension(fileName);
            try {
               classes.add(Class.forName(className));
            }
            catch (ClassNotFoundException e) {
               ReportingUtils.logError(e, "Failed to load class: " +
                     className + " (expected in " + fileName + ")");
            }
         } else if (file.getName().endsWith(".jar")) {
            try {
               addURL(new URL("jar:file:" + file.getAbsolutePath() + "!/"));
               JarInputStream jarFile = new JarInputStream(new FileInputStream(file));
               for (JarEntry jarEntry = jarFile.getNextJarEntry();
                       jarEntry != null;
                       jarEntry = jarFile.getNextJarEntry()) {
                  final String classFileName = jarEntry.getName();
                  if (classFileName.endsWith(".class")) {
                     final String className = stripFilenameExtension(classFileName).replace("/", ".");
                     try {
                        classes.add(Class.forName(className));
                     } catch (ClassNotFoundException e) {
                        ReportingUtils.logError(e, "Failed to load class: " +
                              className + " (expected in " +
                              file.getAbsolutePath() + " based on JAR entry)");
                     } catch (NoClassDefFoundError e) {
                        ReportingUtils.logError(e, "Failed to load class: " +
                              className + " (expected in " +
                              file.getAbsolutePath() +
                              " ) because no class definition was found");
                     }
                  }
               }
            } catch (Exception e) {
               ReportingUtils.logError(e);
            }
         }
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

   public static void addURL(URL u) throws IOException {

      URLClassLoader loader = (URLClassLoader) JavaUtils.class.getClassLoader();
      Class<?> sysclass = URLClassLoader.class;

      try {
         Method method = sysclass.getDeclaredMethod("addURL", parameters);
         method.setAccessible(true);
         method.invoke(loader, new Object[]{u});
         ReportingUtils.logMessage("Added URL to system class loader: " + u);
      } catch (NoSuchMethodException t) {
         ReportingUtils.logError(t, "Failed to add URL to system class loader: " + u);
         throw new IOException("Failed to add URL to system class loader: " + u);
      } catch (SecurityException t) {
         ReportingUtils.logError(t, "Failed to add URL to system class loader: " + u);
         throw new IOException("Failed to add URL to system class loader: " + u);
      } //end try catch
      catch (IllegalAccessException t) {
         ReportingUtils.logError(t, "Failed to add URL to system class loader: " + u);
         throw new IOException("Failed to add URL to system class loader: " + u);
      } catch (IllegalArgumentException t) {
         ReportingUtils.logError(t, "Failed to add URL to system class loader: " + u);
         throw new IOException("Failed to add URL to system class loader: " + u);
      } catch (InvocationTargetException t) {
         ReportingUtils.logError(t, "Failed to add URL to system class loader: " + u);
         throw new IOException("Failed to add URL to system class loader: " + u);
      }//end try catch

   }//end method

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
            String line; //not declared within while loop
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
         if (os.contains("xp")) {
            return System.getenv("USERPROFILE") + "/Local Settings/Application Data/Micro-Manager/";
         } else if ((os.contains("windows 7")) || (os.contains("windows vista"))) {
            return System.getenv("USERPROFILE") + "/AppData/Local/Micro-Manager/";
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
    * Utility function that converts a double array to a Double array
    * @param input double[] to be converted
    * @return Double[]
    */
   public static Double[] doubleArrayToClass (double[] input)
   {
      Double[] newArray = new Double[input.length];
      for (int i = 0; i < input.length; i++)
      {
         newArray[i] = input[i];
      }
      return newArray;
   }
   
   /**
    * Utility function that converts a Double array to a double array
    * @param input Double[] to be converted
    * @return double[]
    */
   public static double[] doubleArrayToPrimitive(Double[] input)
   {
      double[] newArray = new double[input.length];
      for (int i = 0; i < input.length; i++)
      {
         newArray[i] = input[i];
      }
      return newArray;
   }

}

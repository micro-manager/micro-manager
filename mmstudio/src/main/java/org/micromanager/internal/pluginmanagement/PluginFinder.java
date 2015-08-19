///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Nenad Amodaj, nenad@amodaj.com, Jul 18, 2005
//               Modifications by Arthur Edelstein, Nico Stuurman, Henry Pinkard
//COPYRIGHT:     University of California, San Francisco, 2006-2013
//               100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//CVS:          $Id$
//
package org.micromanager.internal.pluginmanagement;

import com.google.common.io.CharStreams;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.lang.ClassLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

public class PluginFinder {
   // This entry is present only if the jar contains a plugin.
   private static final String PLUGIN_ENTRY = "META-INF/json/org.scijava.plugin.Plugin";

   /**
    * Recursively seek through the directory structure under the specified
    * root and generate a list of files that match the given extension.
    * Just a passthrough to the actual recursive method.
    */
   private static ArrayList<String> findPaths(String root, String extension) {
      ArrayList<String> result = new ArrayList<String>();
      recursiveFindPaths(new File(root), extension, result);
      return result;
   }

   private static void recursiveFindPaths(File root, String extension,
         ArrayList<String> result) {
      File[] items = root.listFiles();
      for (File item : items) {
         if (item.getAbsolutePath().endsWith(extension)) {
            result.add(item.getAbsolutePath());
         }
         else if (item.isDirectory()) {
            recursiveFindPaths(item, extension, result);
         }
      }
   }

   /**
    * Find all jars under the given root, check them for the META-INF file that
    * indicates that they're annotated with the @Plugin annotation, and
    * return their class objects as appropriate.
    */
   public static List<Class> findPlugins(String root) {
      ArrayList<Class> result = new ArrayList<Class>();
      for (String jarPath : findPaths(root, ".jar")) {
         try {
            JarFile jar = new JarFile(jarPath);
            if (jar.getJarEntry(PLUGIN_ENTRY) == null) {
               // Not a plugin jar.
               continue;
            }
            // Read the plugin metadata and figure out which class is the
            // plugin class.
            InputStream stream = jar.getInputStream(jar.getJarEntry(PLUGIN_ENTRY));
            String contents = CharStreams.toString(
                  new InputStreamReader(stream, "UTF-8"));
            stream.close();
            try {
               JSONObject json = new JSONObject(contents);
               String className = json.getString("class");
               result.add(getClassFromJar(jar, className));
            }
            catch (JSONException e) {
               ReportingUtils.logError(e, "Error reading META-INF JSON");
            }
         }
         catch (IOException e) {
            ReportingUtils.logError(e, "Unable to load jar file");
         }
      }
      return result;
   }

   /**
    * Given a path to a jar and the name of a class within that jar, load the
    * jar and return the Class object for the named class.
    */
   public static Class getClassFromJar(JarFile jarFile, String className) {
      PluginLoader loader = new PluginLoader(jarFile);
      try {
         return loader.loadClass(className);
      }
      catch (ClassNotFoundException e) {
         ReportingUtils.logError(e, "Class " + className + " not found in jar " + jarFile);
      }
      return null;
   }

   /**
    * Custom class loader for loading plugin classes. Adapted from
    * http://kalanir.blogspot.com/2010/01/how-to-write-custom-class-loader-to.html
    */
   public static class PluginLoader extends ClassLoader {
      private JarFile jarFile_;
      public PluginLoader(JarFile jarFile) {
         super(Thread.currentThread().getContextClassLoader());
         jarFile_ = jarFile;
      }

      @Override
      public Class findClass(String className) {
         try {
            // Replace "." with "/" for seeking through the jar.
            String classPath = className.replace(".", "/") + ".class";
            JarEntry entry = jarFile_.getJarEntry(classPath);
            if (entry == null) {
               // The required class is probably elsewhere in the classpath.
               try {
                  return MMStudio.getInstance().getClass().getClassLoader().loadClass(className);
               }
               catch (ClassNotFoundException e) {
                  ReportingUtils.logError("Unable to find class " + className + " either in jar " + jarFile_.getName() + " or in the normal classpath");
                  return null;
               }
            }
            InputStream stream = jarFile_.getInputStream(entry);
            String contents = CharStreams.toString(
                  new InputStreamReader(stream));
            stream.close();
            byte[] bytes = contents.getBytes();
            Class result = defineClass(className, bytes, 0, bytes.length);
            return result;
         }
         catch (IOException e) {
            ReportingUtils.logError(e, "Unable to load jar file " + jarFile_);
         }
         catch (ClassFormatError e) {
            ReportingUtils.logError(e, "Unable to read class data for class " + className + " from jar " + jarFile_);
         }
         return null;
      }
   }
}

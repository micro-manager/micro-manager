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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.json.JSONArray;
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
    * return a mapping of their class objects to the text from their META-INF
    * JSON files.
    */
   public static HashMap<Class, JSONObject> findPlugins(String root) {
      HashMap<Class, JSONObject> result = new HashMap<Class, JSONObject>();
      for (String jarPath : findPaths(root, ".jar")) {
         result.putAll(findPluginsInJar(jarPath));
      }
      return result;
   }

   /**
    * Return a mapping of class objects to text from the corresponding META-INF
    * JSON for the class, for a specific jar file.
    */
   public static HashMap<Class, JSONObject> findPluginsInJar(String jarPath) {
      HashMap<Class, JSONObject> result = new HashMap<Class, JSONObject>();
      try {
         JarFile jar = new JarFile(jarPath);
         if (jar.getJarEntry(PLUGIN_ENTRY) == null) {
            // Not a plugin jar.
            return result;
         }
         // Read the plugin metadata and figure out which classes are plugin
         // classes.
         InputStream stream = jar.getInputStream(jar.getJarEntry(PLUGIN_ENTRY));
         String contents = CharStreams.toString(
               new InputStreamReader(stream, "UTF-8"));
         stream.close();
         // HACK: the contents of the plugin metadata file we just read are
         // not necessarily valid JSON: if there are multiple classes in the
         // jar with the @Plugin annotation, then we get multiple dictionaries
         // in the file just kind of crammed together (i.e. not a JSON array,
         // just two JSON dicts with no separation between them). So coerce
         // the text to a JSON array and add the necessary separator(s).
         contents = "[" + contents.replaceAll("\\}\\}\\{", "}},{") + "]";
         try {
            JSONArray json = new JSONArray(contents);
            for (int i = 0; i < json.length(); ++i) {
               try {
                  JSONObject entry = json.getJSONObject(i);
                  String className = entry.getString("class");
                  result.put(getClassFromJar(jarPath, className), entry);
               }
               catch (JSONException e) {
                  ReportingUtils.logError(e, "Unable to get entry " + i + " from META-INF JSON for " + jarPath);
               }
            }
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Error reading META-INF JSON in " + jarPath);
         }
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Unable to load jar file " + jarPath);
      }
      return result;
   }

   /**
    * Given a path to a jar and the name of a class within that jar, load the
    * jar and return the Class object for the named class.
    */
   public static Class getClassFromJar(String jarPath, String className) {
      try {
         PluginLoader loader = new PluginLoader(new File(jarPath).toURI().toURL());
         return loader.loadClass(className);
      }
      catch (ClassNotFoundException e) {
         ReportingUtils.logError(e, "Couldn't find class " + className + " in " + jarPath);
      }
      catch (MalformedURLException e) {
         ReportingUtils.logError(e, "Couldn't construct URL for jar " + jarPath);
      }
      return null;
   }

   /**
    * Custom class loader for loading plugin classes and resources. It loads
    * from the specified jar file, and if that fails then it falls back to
    * Micro-Manager's own ClassLoader, so that plugins can depend on the same
    * jars that Micro-Manager does.
    */
   public static class PluginLoader extends URLClassLoader {
      public PluginLoader(URL jarURL) {
         super(new URL[] {jarURL});
      }

      public Class loadClass(String className) throws ClassNotFoundException {
         Class result;
         try {
            result = super.loadClass(className);
         }
         catch (ClassNotFoundException e) {
            // Fall back to Micro-Manager's classloader.
            result = MMStudio.getInstance().getClass().getClassLoader().loadClass(className);
         }
         if (result == null) {
            ReportingUtils.logError("Couldn't load class " + className);
         }
         return result;
      }

      @Override
      public URL getResource(String name) {
         URL result = super.getResource(name);
         if (result == null) {
            // Fall back to our own jar.
            result = MMStudio.getInstance().getClass().getClassLoader().getResource(name);
         }
         if (result == null) {
            ReportingUtils.logError("Couldn't find resource " + name);
         }
         return result;
      }
   }
}

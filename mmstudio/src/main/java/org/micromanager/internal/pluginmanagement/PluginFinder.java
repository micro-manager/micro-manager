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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.micromanager.internal.utils.ReportingUtils;
import org.scijava.InstantiableException;
import org.scijava.plugin.DefaultPluginFinder;
import org.scijava.plugin.PluginIndex;
import org.scijava.plugin.PluginInfo;

/**
 * Recursively seek through the directory structure under the specified
 * root and generate a list of files that match the given extension.
 * Just a passthrough to the actual recursive method.
 */
public final class PluginFinder {
   private static ArrayList<String> findPaths(String root, String extension) {
      ArrayList<String> result = new ArrayList<>();
      // Short-circuit if we're called with a non-directory.
      if (!(new File(root).isDirectory())) {
         if (root.endsWith(extension)) {
            result.add(root);
         }
         return result;
      }
      recursiveFindPaths(new File(root), extension, result);
      return result;
   }

   private static void recursiveFindPaths(File root, String extension,
                                          ArrayList<String> result) {
      File[] items = root.listFiles();
      for (File item : items) {
         if (item.getAbsolutePath().endsWith(extension)) {
            result.add(item.getAbsolutePath());
         } else if (item.isDirectory()) {
            recursiveFindPaths(item, extension, result);
         }
      }
   }

   /**
    * Add all jars under the given root to the shared plugin class loader, then check them for the
    * META-INF file that indicates they are annotated with the @Plugin annotation, and return a
    * list of the corresponding annotated classes.
    *
    * <p>All plugins are loaded through the single {@code loader} so that they are visible to each
    * other and to Micro-Manager's own code (the loader's parent). The JARs added by previous
    * calls remain on the loader, so a plugin in one directory can reference classes from a plugin
    * in another directory.
    *
    * @param loader the shared plugin class loader to add the discovered JARs to
    * @param root   the directory (or jar file) to search for plugin JARs
    * @return the @Plugin-annotated classes found under {@code root}, loaded by {@code loader}
    */
   public static List<Class<?>> findPlugins(SharedPluginClassLoader loader, String root) {
      List<URL> jarURLs = new ArrayList<>();
      for (String jarPath : findPaths(root, ".jar")) {
         try {
            URL jarURL = new File(jarPath).toURI().toURL();
            jarURLs.add(jarURL);
         } catch (MalformedURLException e) {
            ReportingUtils.logError("Unable to generate URL from path " + jarPath + "; skipping");
         }
      }

      // Add the plugin JARs to the shared loader so the classes we discover (and any classes
      // they reference in sibling plugins) resolve through it. SciJava discovery itself runs on a
      // throwaway loader scoped to just these JARs (see findPluginsInUrls), so it does not pick up
      // @Plugin index files from every JAR on the parent class path.
      try {
         for (URL jarURL : jarURLs) {
            loader.addURL(jarURL);
         }
         return findPluginsInUrls(loader, jarURLs);
      } catch (Throwable e) {
         ReportingUtils.logError(e, "Unable to load JARs at " + root);
         return new ArrayList<>();
      }
   }

   /**
    * Discover @Plugin-annotated classes contained in the given JARs, loading them through the
    * shared loader.
    *
    * <p>Discovery is scoped to {@code jarURLs} (the JARs just added) by running SciJava's index
    * scan on a throwaway loader over only those URLs, then loading the resulting classes by name
    * from the shared loader so they end up on the shared loader, visible to all other plugins.
    */
   private static List<Class<?>> findPluginsInUrls(SharedPluginClassLoader sharedLoader,
                                                    List<URL> jarURLs) {
      ArrayList<Class<?>> result = new ArrayList<>();
      URLClassLoader discoveryLoader = new URLClassLoader(jarURLs.toArray(new URL[0]),
            sharedLoader.getParent()) {
         @Override
         public URL getResource(String name) {
            // Do not defer to the parent during discovery, so SciJava only sees the index files
            // of the JARs we are scanning, not every JAR on the parent class path.
            return findResource(name);
         }

         @Override
         public Enumeration<URL> getResources(String name) throws IOException {
            return findResources(name);
         }
      };
      try {
         DefaultPluginFinder finder = new DefaultPluginFinder(discoveryLoader);
         PluginIndex index = new PluginIndex(finder);
         index.discover();
         for (PluginInfo<?> info : index.getAll()) {
            String className = info.getClassName();
            try {
               // Load the real class through the shared loader so it is visible to other plugins.
               result.add(Class.forName(className, false, sharedLoader));
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
               ReportingUtils.logError(e, "Unable to load plugin class " + className);
            }
         }
      } finally {
         try {
            discoveryLoader.close();
         } catch (IOException e) {
            ReportingUtils.logError(e, "Failed to close plugin discovery class loader");
         }
      }
      return result;
   }

   public static List<Class<?>> findPluginsWithLoader(ClassLoader loader) {
      ArrayList<Class<?>> result = new ArrayList<>();
      DefaultPluginFinder finder = new DefaultPluginFinder(loader);
      PluginIndex index = new PluginIndex(finder);
      index.discover();
      for (PluginInfo<?> info : index.getAll()) {
         try {
            result.add(info.loadClass());
         } catch (InstantiableException e) {
            ReportingUtils.logError(e, "Unable to instantiate class for " + info);
         }
      }
      return result;
   }
}

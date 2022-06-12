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
import org.micromanager.internal.MMStudio;
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
         }
         else if (item.isDirectory()) {
            recursiveFindPaths(item, extension, result);
         }
      }
   }

   /**
    * Find all jars under the given root, check them for the META-INF file that
    * indicates that they're annotated with the @Plugin annotation, and return
    * a list of the corresponding annotated classes.
    */
   public static List<Class> findPlugins(String root) {
      ArrayList<Class> result = new ArrayList<>();
      for (String jarPath : findPaths(root, ".jar")) {
         URL jarURL;
         try {
            jarURL = new File(jarPath).toURI().toURL();
         } catch (MalformedURLException e) {
            ReportingUtils.logError("Unable to generate URL from path " + jarPath + "; skipping");
            continue;
         }

         // The class loader used by the plugin should find classes and
         // resources within the plugin JAR first, then fall back to the
         // default class loader.
         // However, when SciJava is discovering plugin classes, we do NOT
         // want to search all JARs on the class path.
         // So we temporarily set the class loader to look only at the given
         // URL for resources.
         // try/catch ensures that any failure to load a single jar won't
         // cause the entire process of loading plugins to fail.
         try {
            PluginClassLoader loader = new PluginClassLoader(jarURL,
                  MMStudio.getInstance().getClass().getClassLoader());
            loader.setBlockInheritedResources(true);
            result.addAll(findPluginsWithLoader(loader));
            loader.setBlockInheritedResources(false);
         } catch (Throwable e) {
            ReportingUtils.logError(e, "Unable to load JAR at " + jarURL);
         }
      }
      return result;
   }

   public static List<Class> findPluginsWithLoader(ClassLoader loader) {
      ArrayList<Class> result = new ArrayList<>();
      DefaultPluginFinder finder = new DefaultPluginFinder(loader);
      PluginIndex index = new PluginIndex(finder);
      index.discover();
      for (PluginInfo info : index.getAll()) {
         try {
            result.add(info.loadClass());
         } catch (InstantiableException e) {
            ReportingUtils.logError(e, "Unable to instantiate class for " + info);
         }
      }
      return result;
   }

   /**
    * Custom class loader for loading plugin classes and resources.
    *
    * <p>The only difference from URLClassLoader is that it allows temporary
    * blockage of resource enumeration and loading from the parent loader.
    */
   private static class PluginClassLoader extends URLClassLoader {
      private boolean blockInheritedResources_ = false;

      public PluginClassLoader(URL jarURL, ClassLoader parent) {
         super(new URL[] {jarURL}, parent);
      }

      public void setBlockInheritedResources(boolean flag) {
         blockInheritedResources_ = flag;
      }

      @Override
      public URL getResource(String name) {
         if (blockInheritedResources_) {
            // findResource does not defer to the parent ClassLoader, and thus
            // will return null if the resource is not found in our specific
            // jar.
            return findResource(name);
         }
         else {
            return super.getResource(name);
         }
      }

      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
         if (blockInheritedResources_) {
            return findResources(name);
         }
         else {
            return super.getResources(name);
         }
      }
   }
}

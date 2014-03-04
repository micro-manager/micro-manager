///////////////////////////////////////////////////////////////////////////////
//FILE:          PluginLoader.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Nico Stuurman, 2014
//               Based on code previously in MMStudioMainFrame
//COPYRIGHT:     University of California, San Francisco, 2006-2014
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
package org.micromanager;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.SwingUtilities;
import org.micromanager.api.Autofocus;
import org.micromanager.api.MMPlugin;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Code for plugin loading, split out from MMStudioMainFrame.
 */
public class PluginLoader {

   private ArrayList<PluginItem> plugins_ = new ArrayList<PluginItem>();

   public class PluginItem {

      public Class<?> pluginClass = null;
      public String menuItem = "undefined";
      public MMPlugin plugin = null;
      public String className = "";

      public void instantiate() {

         try {
            if (plugin == null) {
               plugin = (MMPlugin) pluginClass.newInstance();
            }
         } catch (InstantiationException e) {
            ReportingUtils.logError(e);
         } catch (IllegalAccessException e) {
            ReportingUtils.logError(e);
         }
         plugin.setApp(MMStudioMainFrame.getInstance());
      }
   }

   /**
    * Utility class that holds a PluginItem and a message
    */
   private class PluginItemAndClass {

      private String msg_;
      private PluginItem pi_;

      public PluginItemAndClass(String msg, PluginItem pi) {
         msg_ = msg;
         pi_ = pi;
      }

      public String getMessage() {
         return msg_;
      }

      public PluginItem getPluginItem() {
         return pi_;
      }
   }

   private class PluginItemAndClassComparator implements Comparator<PluginItemAndClass> {

      public int compare(PluginItemAndClass t1, PluginItemAndClass t2) {
         try {
            String m1 = t1.getPluginItem().menuItem;
            String m2 = t2.getPluginItem().menuItem;
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            return collator.compare(m1, m2);
         } catch (NullPointerException npe) {
            ReportingUtils.logError("NullPointerException in PluginItemAndClassCopmarator");
         }
         return 0;
      }
   }

   public String installPlugin(Class<?> cl) {
      PluginItemAndClass piac = declarePlugin(cl);
      final PluginItem pi = piac.getPluginItem();
      if (pi != null) {
         addPluginToMenuLater(pi);
      }
      String msg = piac.getMessage();
      if (msg != null) {
         return msg;
      }
      ReportingUtils.logError("In MMStudioMainFrame:installPlugin, msg was null");
      return piac.getMessage();
   }

   private PluginItemAndClass declarePlugin(Class<?> cl) {
      String className = cl.getSimpleName();
      String msg = className + " module loaded.";
      PluginItem pi = new PluginItem();
      try {
         for (PluginItem plugin : plugins_) {
            if (plugin.className.contentEquals(className)) {
               msg = className + " already loaded";
               PluginItemAndClass piac = new PluginItemAndClass(msg, null);
               return piac;
            }
         }

         pi.className = className;
         try {
            // Get this static field from the class implementing MMPlugin.
            pi.menuItem = (String) cl.getDeclaredField("menuName").get(null);
         } catch (SecurityException e) {
            ReportingUtils.logError(e);
            pi.menuItem = className;
         } catch (NoSuchFieldException e) {
            pi.menuItem = className;
            ReportingUtils.logMessage(className + " fails to implement static String menuName.");
         } catch (IllegalArgumentException e) {
            ReportingUtils.logError(e);
         } catch (IllegalAccessException e) {
            ReportingUtils.logError(e);
         }

         if (pi.menuItem == null) {
            pi.menuItem = className;
         }
         pi.menuItem = pi.menuItem.replace("_", " ");
         pi.pluginClass = cl;
         plugins_.add(pi);
      } catch (NoClassDefFoundError e) {
         msg = className + " class definition not found.";
         ReportingUtils.logError(e, msg);
      }
      PluginItemAndClass piac = new PluginItemAndClass(msg, pi);
      return piac;
   }

   private static void addPluginToMenuLater(final PluginItem pi) {
      SwingUtilities.invokeLater(
              new Runnable() {
         @Override
         public void run() {
            MMStudioMainFrame.getInstance().addPluginToMenu(pi);
         }
      });
   }

   /**
    * Discovers Micro-Manager plugins and autofocus plugins at runtime Adds
    * these to the plugins menu
    */
   public void loadPlugins() {
      ArrayList<Class<?>> pluginClasses = new ArrayList<Class<?>>();
      ArrayList<Class<?>> autofocusClasses = new ArrayList<Class<?>>();
      List<Class<?>> classes;

      try {
         classes = JavaUtils.findClasses(new File("mmplugins"), 1);
         for (Class<?> clazz : classes) {
            for (Class<?> iface : clazz.getInterfaces()) {
               if (iface == MMPlugin.class) {
                  pluginClasses.add(clazz);
               }
            }
         }
      } catch (ClassNotFoundException e1) {
         ReportingUtils.logError(e1);
      }

      ArrayList<PluginItemAndClass> piacs = new ArrayList<PluginItemAndClass>();
      for (Class<?> plugin : pluginClasses) {
         try {
            ReportingUtils.logMessage("Attempting to install plugin " + plugin.getName());
            PluginItemAndClass piac = declarePlugin(plugin);
            if (piac.getPluginItem() != null) {
               piacs.add(piac);
            }
         } catch (Exception e) {
            ReportingUtils.logError(e, "Failed to install the \"" + plugin.getName() + "\" plugin .");
         }
      }
      Collections.sort(piacs, new PluginItemAndClassComparator());
      for (PluginItemAndClass piac : piacs) {
         final PluginItem pi = piac.getPluginItem();
         if (pi != null) {
            addPluginToMenuLater(pi);
         }
      }


      // Install Autofocus classes found in mmautofocus
      try {
         classes = JavaUtils.findClasses(new File("mmautofocus"), 2);
         for (Class<?> clazz : classes) {
            for (Class<?> iface : clazz.getInterfaces()) {
               if (iface == Autofocus.class) {
                  autofocusClasses.add(clazz);
               }
            }
         }
      } catch (ClassNotFoundException e1) {
         ReportingUtils.logError(e1);
      }

      for (Class<?> autofocus : autofocusClasses) {
         try {
            ReportingUtils.logMessage("Attempting to install autofocus plugin " + autofocus.getName());
            MMStudioMainFrame.getInstance().installAutofocusPlugin(autofocus.getName());
         } catch (Exception e) {
            ReportingUtils.logError("Failed to install the \"" + autofocus.getName() + "\" autofocus plugin.");
         }
      }

   }

   public void disposePlugins() {
      for (int i = 0; i < plugins_.size(); i++) {
         MMPlugin plugin = plugins_.get(i).plugin;
         if (plugin != null) {
            plugin.dispose();
         }
      }
   }
}

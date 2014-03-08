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
import java.io.FilenameFilter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import org.micromanager.api.Autofocus;
import org.micromanager.api.MMPlugin;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Code for plugin loading
 */
public class PluginLoader {
   public static final String MMPLUGINSDIR = "mmplugins";
   
   private ArrayList<PluginItem> plugins_ = new ArrayList<PluginItem>();

   /**
    * Utility class used to to assemble information about the plugin
    */
   public class PluginItem {

      private Class<?> pluginClass_ = null; // raw class as input by caller
      private MMPlugin plugin_ = null;      // MMPlugin instance generated in PluginItem
      private String className_ = "";       // className deduced from pluginClass
      private String menuItem_ = "undefined";  // menuText deduced from plugin_
      private String tooltip_ = "";         // tooltip deduced from plugin_
      private String directory_ = "";       // dir in which the class lives (start at mmplugins)
      private String msg_ = "";             // message generated during insepction of pluginClass_

      public PluginItem(Class<?> pluginClass, String className, String menuItem, 
               String tooltip, String directory,  String msg) {
         pluginClass_ = pluginClass;
         menuItem_ = menuItem;
         tooltip_ = tooltip;
         className_ = className;
         directory_ = directory;
         msg_ = msg;
      }
      
      public PluginItem (PluginItem pio) {
         pluginClass_ = pio.pluginClass_;
         menuItem_ = pio.menuItem_;
         plugin_ = pio.plugin_;
         className_ = pio.className_;
         directory_ = pio.directory_;
         msg_ = pio.msg_;
      }
      
      public String getMenuItem() { return menuItem_; }
      public String getMessage() { return msg_; }
      public String getDirectory() { return directory_; }
      public String getClassName() { return className_; }
      public String getTooltip() {return tooltip_; }
      public MMPlugin getMMPlugin() {return plugin_; }
      
      public void instantiate() {
         try {
            if (plugin_ == null) {
               plugin_ = (MMPlugin) pluginClass_.newInstance();
            }
         } catch (InstantiationException e) {
            ReportingUtils.logError(e);
         } catch (IllegalAccessException e) {
            ReportingUtils.logError(e);
         }
         plugin_.setApp(MMStudioMainFrame.getInstance());
      }
   }


   /**
    * Used to sort list of plugins
    * TODO: sort directories correctly
    */
   private class PluginItemComparator implements Comparator<PluginItem> {
      
      public int compare(PluginItem t1, PluginItem t2) {
         try {
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            String m1 = t1.menuItem_;
            String m2 = t2.menuItem_;
            String path1[] = t1.getDirectory().split(Pattern.quote(File.separator));
            String path2[] = t2.getDirectory().split(Pattern.quote(File.separator));
            if (path1.length == 1 && path2.length == 1) {       
               return collator.compare(m1, m2);
            }
            if (path1.length == 2 && path2.length == 1) {
               return collator.compare(path1[1], m2);
            }
            if (path1.length == 1 && path2.length == 2) {
               return collator.compare(m1, path2[1]);
            }
            if (path1.length == 2 && path2.length == 2) {
               int res = collator.compare(path1[1], path2[1]);
               if (res == 0) {
                  return collator.compare(m1, m2);
               }
               return res;
            }
            
         } catch (NullPointerException npe) {
            ReportingUtils.logError("NullPointerException in PluginItemAndClassCopmarator");
         }
         return 0;
      }
   }

   /**
    * Adds class (which should be a MMPlugin instance) to the plugins menu
    * @param cl - Class (which should be an instance of a MMPlugin
    * @return - Message generated while inspecting class, or error  
    */
   public String installPlugin(Class<?> cl) {
      final PluginItem pi = declarePlugin(cl, "mmplugins");
      if (pi != null) {
         addPluginToMenuLater(pi);
      
         if (pi.msg_ != null) {
            return pi.msg_;
         }
      }
      String error = "In PluginLoader:installPlugin, msg was null";
      ReportingUtils.logError(error);
      return error;
   }

   /**
    * Inspects the provided class and transforms it into a PluginItem instance
    * @param cl - Class that potentially is a plugin
    * @param dir - directory in which it was found (start at mmplugins)
    * @return - PluginItem constructed from provided data
    */
   private PluginItem declarePlugin(Class<?> cl, String dir) {
      String className = cl.getSimpleName();
      String msg = className + " module loaded.";
      try {
         for (PluginItem plugin : plugins_) {
            if (plugin.getClassName().contentEquals(className)) {
               msg = className + " already loaded";
               return new PluginItem(cl, "", "", "", dir, msg);
            }
         }

         String menuItem = className;
         try {
            // Get this static field from the class implementing MMPlugin.
            menuItem = (String) cl.getDeclaredField("menuName").get(null);
         } catch (SecurityException e) {
            ReportingUtils.logError(e);
            menuItem = className;
         } catch (NoSuchFieldException e) {
            menuItem = className;
            ReportingUtils.logMessage(className + " fails to implement static String menuName.");
         } catch (IllegalArgumentException e) {
            ReportingUtils.logError(e);
         } catch (IllegalAccessException e) {
            ReportingUtils.logError(e);
         }
       
         String toolTipDescription = "";
         try {
            // Get this static field from the class implementing MMPlugin.
            toolTipDescription = (String) cl.getDeclaredField("tooltipDescription").get(null);
         } catch (SecurityException e) {
            ReportingUtils.logError(e);
            toolTipDescription = "Description not available";
         } catch (NoSuchFieldException e) {
            toolTipDescription = "Description not available";
            ReportingUtils.logMessage(cl.getName() + " fails to implement static String tooltipDescription.");
         } catch (IllegalArgumentException e) {
            ReportingUtils.logError(e);
         } catch (IllegalAccessException e) {
            ReportingUtils.logError(e);
         }

         menuItem = menuItem.replace("_", " ");
         PluginItem pi = new PluginItem (cl, className, menuItem, 
                 toolTipDescription, dir, msg);
         plugins_.add(pi);
         return pi;
      } catch (NoClassDefFoundError e) {
         msg = className + " class definition not found.";
         ReportingUtils.logError(e, msg);
      }
      return new PluginItem(cl, "", "", "", "", msg);
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
    * Discovers Micro-Manager plugins and autofocus plugins at runtime 
    * Adds these to the plugins menu
    */
   public void loadPlugins() {
      ArrayList<Class<?>> autofocusClasses = new ArrayList<Class<?>>();
      List<Class<?>> classes;
      ArrayList<PluginItem> pis = new ArrayList<PluginItem>();
      
      File file = new File(MMPLUGINSDIR);
      String[] tmpDirs = file.list(new FilenameFilter() {
         @Override
         public boolean accept(File current, String name) {
            return new File(current, name).isDirectory();
         }
      });
      List<String> dirs = new ArrayList<String>();
      dirs.add(MMPLUGINSDIR);
      for (String dir: tmpDirs) {
         dirs.add(MMPLUGINSDIR + File.separator + dir);
      }
      
      for (String dir : dirs) {
         try {
            classes = JavaUtils.findClasses(new File(dir), 0);
            for (Class<?> clazz : classes) {
               for (Class<?> iface : clazz.getInterfaces()) {
                  if (iface == MMPlugin.class) {
                     try {
                        ReportingUtils.logMessage("Attempting to install plugin " + clazz.getName());
                        PluginItem pi = declarePlugin(clazz, dir);
                        if (pi != null && !pi.getClassName().isEmpty()) {
                           pis.add(pi);
                        }
                     } catch (Exception e) {
                        ReportingUtils.logError(e, "Failed to install the \"" + clazz.getName() + "\" plugin .");
                     }
                  }
               }
            }
         } catch (ClassNotFoundException e1) {
            ReportingUtils.logError(e1);
         }
      }

      Collections.sort(pis, new PluginItemComparator());
      for (PluginItem pi : pis) {
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
         MMPlugin plugin = plugins_.get(i).plugin_;
         if (plugin != null) {
            plugin.dispose();
         }
      }
   }
}

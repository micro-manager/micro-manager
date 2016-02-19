/*
 * Arthur Edelstein, UCSF, 2011
 */

package org.micromanager.clojureeditor;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class ClojureEditorPlugin implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Clojure editor";
   public static final String tooltipDescription =
      "Clojure script editor and REPL";
   
   @Override
   public void setContext(Studio app) {
      // do nothing.
   }

   @Override
   public void onPluginSelected() {
      // The current thread's context class loader must be set for Clojure
      // class to load. We don't want to globally set the EDT's context class
      // loader, so let's spawn a new thread on which Clooj is loaded.

      Thread loadingThread = new Thread() {
         @Override public void run() {
            clooj.core.show();
         }
      };

      loadingThread.setContextClassLoader(getClass().getClassLoader());

      loadingThread.start();

      try {
         loadingThread.join();
      }
      catch (InterruptedException ignore) {
         // Nobody should be interrupting the EDT.
      }
   }

   public String getName() {
      return menuName;
   }

   public String getSubMenu() {
      return "Developer Tools";
   }

   public String getHelpText() {
      return tooltipDescription;
   }

   public String getVersion() {
      return "V0.1";
   }

   public String getCopyright() {
      return "Copyright University of California 2011-2015";
   }

}


/*
 * Arthur Edelstein, UCSF, 2011
 */

package org.micromanager.clojureeditor;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

public class ClojureEditorPlugin implements MMPlugin {
   public static final String menuName = "Clojure editor";
   public static final String tooltipDescription =
      "Clojure script editor and REPL";
   
   public void dispose() {
      // do nothing
   }

   public void setApp(ScriptInterface app) {
      // do nothing.
   }

   public void show() {
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

   public void configurationChanged() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getDescription() {
      return tooltipDescription;
   }

   public String getInfo() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getVersion() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getCopyright() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

}


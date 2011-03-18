package org.micromanager.clojurescripting;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import clooj.core;

public class ClojureScripting implements MMPlugin {

   public void dispose() {
      // do nothing
   }

   public void setApp(ScriptInterface app) {
      // do nothing.
   }

   public void show() {
      
      core.main(new String[] {""});
   }

   public void configurationChanged() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getDescription() {
      throw new UnsupportedOperationException("Not supported yet.");
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


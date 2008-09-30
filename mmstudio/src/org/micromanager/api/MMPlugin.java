package org.micromanager.api;

public interface MMPlugin {
   public void dispose();
   public void setApp(ScriptInterface app);
   public void show();
}

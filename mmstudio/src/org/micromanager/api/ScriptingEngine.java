package org.micromanager.api;

import org.micromanager.utils.MMScriptException;

public interface ScriptingEngine {
   public void evaluate(String script) throws MMScriptException;
   public void evaluateAsync(String script)throws MMScriptException;
   public void insertGlobalObject(String name, Object obj) throws MMScriptException;
   //public void stop();
   public void stopRequest();
   public boolean stopRequestPending();
   public void sleep(long ms) throws MMScriptException;
}

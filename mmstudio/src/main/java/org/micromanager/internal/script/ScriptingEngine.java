package org.micromanager.internal.script;

import bsh.Interpreter;
import org.micromanager.internal.utils.MMScriptException;

public interface ScriptingEngine {

   void evaluate(String script) throws MMScriptException;

   void joinEvalThread() throws InterruptedException;

   void evaluateAsync(String script) throws MMScriptException;

   void insertGlobalObject(String name, Object obj) throws MMScriptException;

   void stopRequest(boolean shouldInterrupt);

   boolean stopRequestPending();

   void setInterpreter(Interpreter interp);

   void resetInterpreter();
}

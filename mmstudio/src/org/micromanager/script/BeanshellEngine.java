package org.micromanager.script;

import org.micromanager.api.ScriptingEngine;
import org.micromanager.api.ScriptingGUI;
import org.micromanager.utils.MMLogger;
import org.micromanager.utils.MMScriptException;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.ParseException;
import bsh.TargetError;

public class BeanshellEngine implements ScriptingEngine {
   Interpreter interp_;
   boolean running_ = false;
   boolean error_ = false;
   EvalThread evalThd_;
   boolean stop_ = false;
   private static MMLogger logger_;
   private ScriptingGUI gui_;

   public class EvalThread extends Thread {
      String script_;
      String errorText_;

      public EvalThread(String script) {
         script_ = script;
         errorText_ = new String();
      }

      public void run() {
         stop_ = false;
         running_ = true;
         errorText_ = new String();
         try {
            interp_.eval(script_);
         } catch (TargetError e){
            int lineNo = e.getErrorLineNumber(); 
            gui_.displayError(formatBeanshellError(e, lineNo), lineNo);
         } catch (ParseException e) {
            // special handling of the parse errors beacuse beanshell error object
            // has bugs and does not return line numbers
            String msg = e.getMessage();
            String lineNumberTxt = msg.substring(20, msg.indexOf(','));
            MMLogger.getLogger().info(msg);
            gui_.displayError("Parse error: " + msg, Integer.parseInt(lineNumberTxt));
         } catch (EvalError e) {
            int lineNo = e.getErrorLineNumber(); 
            gui_.displayError(formatBeanshellError(e, lineNo), lineNo);
         } finally {
            running_ = false;
         }
      }

      public String getError() {
         return errorText_;
      }
   }

   public BeanshellEngine(ScriptingGUI gui) {
      interp_ = new Interpreter();
      running_ = false;
      evalThd_ = new EvalThread("");
      logger_ = new MMLogger();
      gui_ = gui;
   }

   public void evaluate(String script) throws MMScriptException {
      try {
         interp_.eval(script);
      } catch (EvalError e) {
         throw new MMScriptException(formatBeanshellError(e, e.getErrorLineNumber()));
      }
   }
   public void evaluateAsync(String script) throws MMScriptException {
      if (evalThd_.isAlive())
         throw new MMScriptException("Another script execution in progress!");

      evalThd_ = new EvalThread(script);
      evalThd_.start();
   }

   public void insertGlobalObject(String name, Object obj) throws MMScriptException {
      try {
         interp_.set(name, obj);
      } catch (EvalError e) {
         throw new MMScriptException(e);
      }
   }

   public void stopRequest() {
      if (evalThd_.isAlive())
         evalThd_.interrupt();
      stop_ = true;
   }

   public boolean stopRequestPending() {
      return stop_;
   }
   
   private String formatBeanshellError(EvalError e, int line) {
      if (e instanceof TargetError) {
         Throwable t = ((TargetError)e).getTarget();
         return new String("Line " + line + ": run-time error : " + (t != null ? t.getMessage() : e.getErrorText()));       
      } else if (e instanceof ParseException) {
         return new String("Line " + line + ": syntax error : " + e.getErrorText());         
      } else {
         Throwable t = e.getCause();
         return new String("Line " + line + ": general error : " + (t != null ? t.getMessage() : e.getErrorText()));
      }
      
   }

   public void sleep(long ms) throws MMScriptException {
      try {
         Thread.sleep(ms);
      } catch (InterruptedException e) {
         throw new MMScriptException("Execution interrupted by the user");
      }
   }
}

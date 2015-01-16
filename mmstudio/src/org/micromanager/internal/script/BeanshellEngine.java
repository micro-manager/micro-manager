package org.micromanager.internal.script;

import org.micromanager.internal.utils.MMScriptException;

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
   private ScriptingGUI gui_;
   private Interpreter interp_old_;

   public class EvalThread extends Thread {
      String script_;
      String errorText_;

      public EvalThread(String script) {
         script_ = script;
         errorText_ = new String();
      }

      @Override
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

   
   @Override
   public void setInterpreter(Interpreter interp) {
	   interp_old_ = interp_;
	   interp_ = interp;
   }
   
   @Override
   public void resetInterpreter() {
	   interp_ = interp_old_;
   }
   
   public BeanshellEngine(ScriptingGUI gui) {
      //interp_ = new Interpreter();
      running_ = false;
      evalThd_ = new EvalThread("");
      gui_ = gui;
   }

   @Override
   public void evaluate(String script) throws MMScriptException {
      try {
         interp_.eval(script);
    	 // interp_.set("micro_manager_script",script);
      } catch (EvalError e) {
         throw new MMScriptException(formatBeanshellError(e, e.getErrorLineNumber()));
      }
   }

   @Override
   public void joinEvalThread() throws InterruptedException {
      if (evalThd_.isAlive()) {
         evalThd_.join();
      }
   }
   
   @Override
   public void evaluateAsync(String script) throws MMScriptException {
      if (evalThd_.isAlive())
         throw new MMScriptException("Another script execution in progress!");

      evalThd_ = new EvalThread(script);
      evalThd_.start();
   }

   @Override
   public void insertGlobalObject(String name, Object obj) throws MMScriptException {
      try {
         interp_.set(name, obj);
      } catch (EvalError e) {
         throw new MMScriptException(e);
      }
   }

   @SuppressWarnings("deprecation")
   @Override
   public void stopRequest(boolean shouldInterrupt) {
      if (evalThd_.isAlive()) {
         if (shouldInterrupt) {
            evalThd_.interrupt();
         }
         else {
            // HACK: kill the thread.
            evalThd_.stop();
            stop_ = true;
         }
      }
   }

   @Override
   public boolean stopRequestPending() {
      if (evalThd_.isAlive() && stop_)
         return stop_;
      stop_ = false;
      return stop_;
   }
   
   private String formatBeanshellError(EvalError e, int line) {
      if (e instanceof TargetError) {
         Throwable t = ((TargetError)e).getTarget();
         return "Line " + line + ": run-time error : " + (t != null ? t.getMessage() : e.getErrorText());       
      } else if (e instanceof ParseException) {
         return "Line " + line + ": syntax error : " + e.getErrorText();         
      } else {
         Throwable t = e.getCause();
         return "Line " + line + ": general error : " + (t != null ? t.getMessage() : e.getErrorText());
      }
      
   }

   @Override
   public void sleep(long ms) throws MMScriptException {
      try {
         Thread.sleep(ms);
      } catch (InterruptedException e) {
         throw new MMScriptException	("Execution interrupted by the user");
      }
   }
}

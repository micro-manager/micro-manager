package org.micromanager.internal.script;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.ParseException;
import bsh.TargetError;
import org.micromanager.internal.utils.MMScriptException;

/**
 * Executes Beanshell on user-supplied scripts.
 */
public final class BeanshellEngine implements ScriptingEngine {
   Interpreter interp_;
   boolean running_ = false;
   EvalThread evalThd_;
   boolean stop_ = false;
   private final ScriptPanel panel_;
   private Interpreter interpOld_;

   /**
    * Thread interpreting a script.
    */
   public final class EvalThread extends Thread {
      String script_;
      String errorText_;

      public EvalThread(String script) {
         script_ = script;
      }

      @Override
      public void run() {
         stop_ = false;
         running_ = true;
         try {
            interp_.eval(script_);
         } catch (TargetError e) {
            int lineNo = e.getErrorLineNumber();
            panel_.displayError(formatBeanshellError(e, lineNo), lineNo);
         } catch (ParseException e) {
            // special handling of the parse errors beacuse beanshell error object
            // has bugs and does not return line numbers
            String msg = e.getMessage();
            String lineNumberTxt = msg.substring(0, msg.lastIndexOf(','));
            lineNumberTxt = lineNumberTxt.substring(lineNumberTxt.lastIndexOf(' ') + 1);
            try {
               panel_.displayError("Parse error: " + msg, Integer.parseInt(lineNumberTxt));
            } catch (NumberFormatException nfe) {
               panel_.displayError("Parse error: " + msg);
            }
         } catch (EvalError e) {
            int lineNo = e.getErrorLineNumber();
            panel_.displayError(formatBeanshellError(e, lineNo), lineNo);
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
      interpOld_ = interp_;
      interp_ = interp;
   }

   @Override
   public void resetInterpreter() {
      interp_ = interpOld_;
   }

   /**
    * Creates a BeanshellEngine for the given ScriptPanel.
    *
    * @param panel ScriptPanel to attach this engine to.
    */
   public BeanshellEngine(ScriptPanel panel) {
      //interp_ = new Interpreter();
      running_ = false;
      evalThd_ = new EvalThread("");
      panel_ = panel;
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
      if (evalThd_.isAlive()) {
         throw new MMScriptException("Another script execution in progress!");
      }

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
         } else {
            // HACK: kill the thread.
            evalThd_.stop();
            stop_ = true;
         }
      }
   }

   @Override
   public boolean stopRequestPending() {
      if (evalThd_.isAlive() && stop_) {
         return stop_;
      }
      stop_ = false;
      return stop_;
   }

   private String formatBeanshellError(EvalError e, int line) {
      if (e instanceof TargetError) {
         Throwable t = ((TargetError) e).getTarget();
         if (t instanceof NullPointerException) {
            // Null Pointer Exceptions do not seem to have much more information
            // However, do make clear to the user that this is a npe
            return "Line " + line + ": Null Pointer Exception";
         }
         return "Line " + line + ": run-time error : " + (t != null ? t.getMessage()
               : e.getErrorText());
      } else if (e instanceof ParseException) {
         return "Line " + line + ": syntax error : " + e.getErrorText();
      } else if (e != null) {
         return "Line " + line + ": evaluation error : " + e.getMessage();
      } else {
         Throwable t = e.getCause();
         return "Line " + line + ": general error : "
               + (t != null ? t.getMessage() : e.getErrorText());
      }
   }

}
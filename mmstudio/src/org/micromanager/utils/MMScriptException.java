package org.micromanager.utils;

public class MMScriptException extends Exception {
   private static final long serialVersionUID = -8472385639461107823L;
   private Throwable cause;
   private static final String MSG_PREFIX = "MMScript error: ";

   public MMScriptException(String message) {
       super(MSG_PREFIX + message);
   }

   public MMScriptException(Throwable t) {
       super(MSG_PREFIX + t.getMessage());
       this.cause = t;
   }

   @Override
   public Throwable getCause() {
       return this.cause;
   }

}

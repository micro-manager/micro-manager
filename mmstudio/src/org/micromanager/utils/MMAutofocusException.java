package org.micromanager.utils;

public class MMAutofocusException extends Exception {
   private static final long serialVersionUID = 1L;
   private Throwable cause;
   private static final String MSG_PREFIX = "MMAutofocus error: ";

   /**
    * Constructs a MMAcqDataException with an explanatory message.
    * @param message Detail about the reason for the exception.
    */
   public MMAutofocusException(String message) {
       super(MSG_PREFIX + message);
   }

   public MMAutofocusException(Throwable t) {
       super(MSG_PREFIX + t.getMessage());
       this.cause = t;
   }

   @Override
   public Throwable getCause() {
       return this.cause;
   }
}

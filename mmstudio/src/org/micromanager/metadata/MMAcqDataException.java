package org.micromanager.metadata;

public class MMAcqDataException extends Exception {
   private Throwable cause;

   /**
    * Constructs a MMAcqDataException with an explanatory message.
    * @param message Detail about the reason for the exception.
    */
   public MMAcqDataException(String message) {
       super(message);
   }

   public MMAcqDataException(Throwable t) {
       super(t.getMessage());
       this.cause = t;
   }

   public Throwable getCause() {
       return this.cause;
   }

}

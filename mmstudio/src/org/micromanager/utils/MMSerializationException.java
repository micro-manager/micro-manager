package org.micromanager.utils;

public class MMSerializationException extends Exception {
   private static final long serialVersionUID = -4802471513416825131L;
   private Throwable cause;
   private static final String MSG_PREFIX = "MMSerialization error: ";

   /**
    * Constructs a MMAcqDataException with an explanatory message.
    * @param message Detail about the reason for the exception.
    */
   public MMSerializationException(String message) {
       super(MSG_PREFIX + message);
   }

   public MMSerializationException(Throwable t) {
       super(MSG_PREFIX + t.getMessage());
       this.cause = t;
   }

   @Override
   public Throwable getCause() {
       return this.cause;
   }
}

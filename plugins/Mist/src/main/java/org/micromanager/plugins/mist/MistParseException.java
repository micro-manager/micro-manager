package org.micromanager.plugins.mist;

/**
 * Exception thrown when there is an error parsing a MIST file.
 *
 */
public class MistParseException extends Exception {
   public MistParseException(String message) {
      super(message);
   }
}

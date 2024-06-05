
package org.micromanager.sharpest;

/**
 * So that we can throw our own Exception.
 *
 * @author nico
 */
public class SharpestException extends Exception {
   
   public SharpestException(String text) {
      super(text);
   }
}


package org.micromanager.zprojector;

/**
 * So that we can throw our own Exception.
 *
 * @author nico
 */
public class ZProjectorException extends Exception {
   
   public ZProjectorException(String text) {
      super(text);
   }
}

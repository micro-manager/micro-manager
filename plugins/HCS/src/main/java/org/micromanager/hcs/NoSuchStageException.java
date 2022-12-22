
package org.micromanager.hcs;

/**
 * Exception used when the designated stage is not found in this microscope.
 *
 * @author nico
 */
class NoSuchStageException extends Exception {
   
   public NoSuchStageException(String msg) {
      super(msg);
   }
}

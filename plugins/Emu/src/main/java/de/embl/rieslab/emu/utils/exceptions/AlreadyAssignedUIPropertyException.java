package de.embl.rieslab.emu.utils.exceptions;

/**
 * Exception thrown when a process attempts to assign a UIProperty that has already been assigned.
 *
 * @author Joran Deschamps
 */
public class AlreadyAssignedUIPropertyException extends Exception {

   private static final long serialVersionUID = 1L;

   public AlreadyAssignedUIPropertyException(String propertyName) {
      super("The property \"" + propertyName + "\" has already been assigned.");
   }
}

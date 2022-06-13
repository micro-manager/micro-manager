package de.embl.rieslab.emu.utils.exceptions;

public class IncompatibleMMProperty extends Exception {

   private static final long serialVersionUID = 1L;

   public IncompatibleMMProperty(String mmproperty, String uiproperty) {
      super("The Micro-manager property \"" + mmproperty
            + "\" is not compatible with the UI property \"" + uiproperty + "\".");
   }
}

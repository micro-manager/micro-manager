package de.embl.rieslab.emu.utils.exceptions;

public class IncorrectInternalPropertyTypeException extends Exception {

   private static final long serialVersionUID = 1L;

   public IncorrectInternalPropertyTypeException(String expectedType, String observedType) {
      super("Expected an InternalProperty of type [" + expectedType +
            "], but received a UIProperty of type [" + observedType + "].");
   }
}

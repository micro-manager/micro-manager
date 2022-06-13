package de.embl.rieslab.emu.utils.exceptions;

public class IncorrectUIPropertyTypeException extends Exception {

   private static final long serialVersionUID = 1L;

   public IncorrectUIPropertyTypeException(String expectedType, String observedType) {
      super("Expected a UIProperty of type [" + expectedType +
            "], but received a UIProperty of type [" + observedType + "].");
   }
}

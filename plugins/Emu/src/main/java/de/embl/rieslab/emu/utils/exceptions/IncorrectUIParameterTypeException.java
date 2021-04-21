package de.embl.rieslab.emu.utils.exceptions;

public class IncorrectUIParameterTypeException extends Exception {

  private static final long serialVersionUID = 1L;

  public IncorrectUIParameterTypeException(
      String parameterName, String expectedType, String observedType) {
    super(
        "The UIParameter ["
            + parameterName
            + "] is of type ["
            + observedType
            + "], instead of the expected ["
            + expectedType
            + "].");
  }
}

package de.embl.rieslab.emu.utils.exceptions;

public class UnknownInternalPropertyException extends Exception {
  private static final long serialVersionUID = 1L;

  public UnknownInternalPropertyException(String label) {
    super("The InternalProperty [" + label + "] is unknown.");
  }
}

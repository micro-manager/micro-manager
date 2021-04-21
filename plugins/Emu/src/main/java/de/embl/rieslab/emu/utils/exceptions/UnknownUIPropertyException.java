package de.embl.rieslab.emu.utils.exceptions;

public class UnknownUIPropertyException extends Exception {

  private static final long serialVersionUID = 1L;

  public UnknownUIPropertyException(String label) {
    super("The UIProperty [" + label + "] is unknown.");
  }
}

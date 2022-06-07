package de.embl.rieslab.emu.utils.exceptions;

public class UnknownUIParameterException extends Exception {

    private static final long serialVersionUID = 1L;

    public UnknownUIParameterException(String label) {
        super("The UIProperty [" + label + "] is unknown.");
    }
}

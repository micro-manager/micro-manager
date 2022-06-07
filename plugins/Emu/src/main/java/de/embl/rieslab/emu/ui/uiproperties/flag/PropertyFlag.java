package de.embl.rieslab.emu.ui.uiproperties.flag;

/**
 * @author Joran Deschamps
 */
public abstract class PropertyFlag implements Comparable<PropertyFlag> {
    private final String value;

    public PropertyFlag(String value) {
        this.value = value;
    }

    private String getPropertyFlag() {
        return value;
    }

    @Override
    public int compareTo(PropertyFlag arg0) {
        return getPropertyFlag().compareTo(arg0.getPropertyFlag());
    }
}; 

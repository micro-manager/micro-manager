package org.micromanager.plugins.FluidControl;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

public class Config {
    private final PropertyChangeSupport support;

    public ArrayList<String> pressurePumpSelected;
    public ArrayList<String> volumePumpSelected;

    public Config() {
        support = new PropertyChangeSupport(this);
        pressurePumpSelected = new ArrayList<>();
        volumePumpSelected = new ArrayList<>();
    }

    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }

    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        support.removePropertyChangeListener(pcl);
    }

    public void setProperty(String property, Object value) {
        support.firePropertyChange(property, false, true);
    }
}

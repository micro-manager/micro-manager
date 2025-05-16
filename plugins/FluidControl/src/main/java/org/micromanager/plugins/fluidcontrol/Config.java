package org.micromanager.plugins.fluidcontrol;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

/**
 *  Class to transfer data between frames using propertyChangeListeners.
 */
public class Config {
   private final PropertyChangeSupport support;

   public ArrayList<String> pressurePumpSelected;
   public ArrayList<String> volumePumpSelected;

   /**
    *  Class to transfer data between frames using propertyChangeListeners.
    *  This class keeps a list of active pressure and volume pumps.
    */
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

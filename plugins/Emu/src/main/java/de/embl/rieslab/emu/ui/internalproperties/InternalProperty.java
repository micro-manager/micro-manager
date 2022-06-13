package de.embl.rieslab.emu.ui.internalproperties;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import java.util.ArrayList;

/**
 * An InternalProperty is aimed at passing information between
 * {@link de.embl.rieslab.emu.ui.ConfigurablePanel}s using an atomic variable of type T. In the
 * ConfigurablePanel, the value can then be retrieved as a wrapper for one of the corresponding
 * primitive type. An InternalProperty is instantiated with a {@code name}. InternalProperties
 * with the same name will be shared between the different ConfigurablePanels that created them,
 * as long as they are of the same InternalPropertyType.
 *
 * <p>Upon modification of its value, through a call to
 * {@link #setInternalPropertyValue(Object, ConfigurablePanel)}, the value of the atomic
 * variable is updated and a call to all other listeners (ConfigurablePanel) except the source
 * of the call notifies them of the change.
 *
 * @param <T> Should be an Atomic type to keep the synchronization between threads.
 * @param <V> Wrapper for a primitive type the Atomic type can be easily converted to.
 * @author Joran Deschamps
 */
public abstract class InternalProperty<T, V> {
   private final String label_;
   private final T value_;
   private final ArrayList<ConfigurablePanel> listeners_;

   /**
    * Constructor, initializes the atomic value with a default one and adds the {@code owner}
    * to the list of listeners.
    *
    * @param owner        ConfigurablePanel that created this InternalProperty
    * @param label        Name of the InternalProperty
    * @param defaultvalue Default value
    */
   public InternalProperty(ConfigurablePanel owner, String label, V defaultvalue) {
      this.label_ = label;

      value_ = initializeDefault(defaultvalue);
      listeners_ = new ArrayList<ConfigurablePanel>();
      listeners_.add(owner);
   }

   /**
    * Returns the label of the InternalProperty.
    *
    * @return Label of the InternalProperty
    */
   public String getLabel() {
      return label_;
   }

   /**
    * Returns the value of the InternalPropery.
    *
    * @return Value
    */
   public V getInternalPropertyValue() {
      return convertValue(value_);
   }

   /**
    * Returns the atomic value of the InternalProperty.
    *
    * @return Atomic value
    */
   protected T getAtomicValue() {
      return value_;
   }

   /**
    * Sets the value of the atomic variable.
    *
    * @param val Value to set the atomic variable to.
    */
   protected abstract void setAtomicValue(V val);

   /**
    * Sets the value of the InternalProperty to {@code val} and notifies all listeners but the
    * {@code source}.
    *
    * @param val    New value
    * @param source ConfigurablePanel at the origin of the update
    */
   public void setInternalPropertyValue(V val, ConfigurablePanel source) {
      if (val == null) {
         throw new NullPointerException();
      }
      setAtomicValue(val);
      notifyListeners(source);
   }

   /**
    * Registers {@code listener}. If {@code listener} has an InternalProperty with same name
    * and type, then the InternalProperty is replaced by this one in the ConfigurablePanel. If no
    * such InternalProperty exists, nothing happens.
    *
    * @param listener ConfigurablePanel with an InternalProperty with same name and type
    */
   public void registerListener(ConfigurablePanel listener) {
      if (listener.getInternalPropertyType(label_).compareTo(this.getType()) == 0) {
         listeners_.add(listener);
         listener.substituteInternalProperty(this);
      }
   }

   private void notifyListeners(ConfigurablePanel source) {
      for (int i = 0; i < listeners_.size(); i++) {
         if (listeners_.get(i) != source) {
            listeners_.get(i).internalpropertyhasChanged(label_);
         }
      }
   }

   /**
    * Returns the type of InternalProperty.
    *
    * @return InternalProperty type.
    */
   public abstract InternalPropertyType getType();

   /**
    * Initialize the default value of the internal atomic value member to the value
    * of the primitive wrapper type {@code defaultval}.
    *
    * @param defaultval Default value
    * @return An atomic value set to {@code defaultval}
    */
   protected abstract T initializeDefault(V defaultval);

   /**
    * Convert the atomic value to the primitive wrapper type.
    *
    * @param val Value to convert
    * @return Converted value
    */
   protected abstract V convertValue(T val);

   /**
    * Types of InternalProperties.
    *
    * @author Joran Deschamps
    */
   public enum InternalPropertyType {
      INTEGER("Integer"), DOUBLE("Double"), BOOLEAN("Boolean"), NONE("None");

      private final String value;

      InternalPropertyType(String value) {
         this.value = value;
      }

      public String getTypeValue() {
         return value;
      }
   }
}
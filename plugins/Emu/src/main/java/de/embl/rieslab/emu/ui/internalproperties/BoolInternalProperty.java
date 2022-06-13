package de.embl.rieslab.emu.ui.internalproperties;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import java.util.concurrent.atomic.AtomicBoolean;

public class BoolInternalProperty extends InternalProperty<AtomicBoolean, Boolean> {

   public BoolInternalProperty(ConfigurablePanel owner, String name, Boolean defaultvalue) {
      super(owner, name, defaultvalue);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public InternalPropertyType getType() {
      return InternalPropertyType.BOOLEAN;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Boolean convertValue(AtomicBoolean val) {
      return val.get();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void setAtomicValue(Boolean val) {
      getAtomicValue().set(val);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected AtomicBoolean initializeDefault(Boolean defaultval) {
      return new AtomicBoolean(defaultval);
   }

}

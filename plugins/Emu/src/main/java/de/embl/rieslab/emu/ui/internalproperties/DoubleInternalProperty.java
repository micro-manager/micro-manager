package de.embl.rieslab.emu.ui.internalproperties;

import java.util.concurrent.atomic.AtomicLong;

import de.embl.rieslab.emu.ui.ConfigurablePanel;

public class DoubleInternalProperty extends InternalProperty<AtomicLong, Double> {

  public DoubleInternalProperty(ConfigurablePanel owner, String name, Double defaultvalue) {
    super(owner, name, defaultvalue);
  }

  /** {@inheritDoc} */
  @Override
  public InternalPropertyType getType() {
    return InternalPropertyType.DOUBLE;
  }

  /** {@inheritDoc} */
  @Override
  protected Double convertValue(AtomicLong val) {
    return Double.longBitsToDouble(val.get());
  }

  /** {@inheritDoc} */
  @Override
  protected void setAtomicValue(Double val) {
    getAtomicValue().set(Double.doubleToLongBits(val));
  }

  /** {@inheritDoc} */
  @Override
  protected AtomicLong initializeDefault(Double defaultval) {
    return new AtomicLong(Double.doubleToLongBits(defaultval));
  }
}

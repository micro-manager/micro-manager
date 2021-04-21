package de.embl.rieslab.emu.ui.uiparameters;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;
import de.embl.rieslab.emu.utils.EmuUtils;

/**
 * UIParameter holding a double value.
 *
 * @author Joran Deschamps
 */
public class DoubleUIParameter extends UIParameter<Double> {

  public DoubleUIParameter(ConfigurablePanel owner, String label, String description, double val) {
    super(owner, label, description);

    setValue(val);
  }

  /** {@inheritDoc} */
  @Override
  public UIParameterType getType() {
    return UIParameterType.DOUBLE;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSuitable(String val) {
    if (EmuUtils.isNumeric(val)) {
      return true;
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  protected Double convertValue(String val) {
    return Double.parseDouble(val);
  }

  /** {@inheritDoc} */
  @Override
  public String getStringValue() {
    return String.valueOf(getValue());
  }
}

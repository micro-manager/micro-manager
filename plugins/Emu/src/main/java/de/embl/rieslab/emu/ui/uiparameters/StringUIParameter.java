package de.embl.rieslab.emu.ui.uiparameters;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;

/**
 * UIParameter with String type. It can be used for instance to define the displayed text of
 * JButtons or of titled borders.
 *
 * @author Joran Deschamps
 */
public class StringUIParameter extends UIParameter<String> {

  public StringUIParameter(
      ConfigurablePanel owner, String label, String description, String value) {
    super(owner, label, description);

    if (value == null) {
      throw new NullPointerException("The UIParameter value cannot be set to null.");
    }

    setValue(value);
  }

  /** {@inheritDoc} */
  @Override
  public UIParameterType getType() {
    return UIParameterType.STRING;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSuitable(String val) {
    if (val == null) {
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public String convertValue(String val) {
    return val;
  }

  /** {@inheritDoc} */
  @Override
  public String getStringValue() {
    return getValue();
  }
}

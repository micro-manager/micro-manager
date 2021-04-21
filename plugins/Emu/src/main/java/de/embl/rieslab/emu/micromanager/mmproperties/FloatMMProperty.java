package de.embl.rieslab.emu.micromanager.mmproperties;

import de.embl.rieslab.emu.controller.log.Logger;
import de.embl.rieslab.emu.controller.utils.GlobalSettings;
import de.embl.rieslab.emu.utils.EmuUtils;
import mmcorej.CMMCore;

/**
 * Wrapper for a Micro-manager device property with float value. The property can be read-only or
 * not, with limits or with a set of allowed values.
 *
 * @author Joran Deschamps
 */
public class FloatMMProperty extends MMProperty<Float> {

  /**
   * Builds a float MMProperty without limits or allowed values. The property can be read-only.
   *
   * @param core Micro-manager core.
   * @param logger Log manager.
   * @param deviceLabel Label of the parent device as defined in Micro-manager.
   * @param propertyLabel Label of the device property as defined in Micro-manager.
   * @param readOnly True if the device property is read-only, false otherwise.
   */
  FloatMMProperty(
      CMMCore core, Logger logger, String deviceLabel, String propertyLabel, boolean readOnly) {
    super(core, logger, MMProperty.MMPropertyType.FLOAT, deviceLabel, propertyLabel, readOnly);
  }

  /**
   * Builds a float MMProperty with limits.
   *
   * @param core Micro-manager core.
   * @param logger Log manager.
   * @param deviceLabel Label of the parent device as defined in Micro-manager.
   * @param propertyLabel Label of the device property as defined in Micro-manager.
   * @param upperLimit Upper limit of the device property value.
   * @param lowerLimit Lower limit of the device property value.
   */
  FloatMMProperty(
      CMMCore core,
      Logger logger,
      String deviceLabel,
      String propertyLabel,
      double upLimit,
      double downLimit) {
    super(
        core,
        logger,
        MMProperty.MMPropertyType.FLOAT,
        deviceLabel,
        propertyLabel,
        upLimit,
        downLimit);
  }

  /**
   * Builds a float MMProperty with allowed values.
   *
   * @param core Micro-manager core.
   * @param deviceLabel Label of the parent device as defined in Micro-manager.
   * @param propertyLabel Label of the device property as defined in Micro-manager.
   * @param allowedValues Array of allowed values.
   */
  FloatMMProperty(
      CMMCore core,
      Logger logger,
      String deviceLabel,
      String propertyLabel,
      String[] allowedValues) {
    super(core, logger, MMProperty.MMPropertyType.FLOAT, deviceLabel, propertyLabel, allowedValues);
  }

  /** {@inheritDoc} */
  @Override
  public Float convertToValue(String s) {
    if (EmuUtils.isNumeric(s) && !s.contains(",")) {
      return Float.parseFloat(s);
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Float convertToValue(int s) {
    return new Float(s);
  }

  /** {@inheritDoc} */
  @Override
  public Float convertToValue(double s) {
    return new Float(s);
  }

  /** {@inheritDoc} */
  @Override
  public Float[] arrayFromStrings(String[] s) {
    Float[] allowedVal = new Float[s.length];
    for (int i = 0; i < s.length; i++) {
      allowedVal[i] = convertToValue(s[i]);
    }
    return allowedVal;
  }

  /** {@inheritDoc} */
  @Override
  public String convertToString(Float val) {
    return val.toString();
  }

  private boolean isInRange(Float val) {
    if (val.compareTo(getMax()) <= 0 && val.compareTo(getMin()) >= 0) {
      return true;
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAllowed(Float val) {
    if (val == null) {
      return false;
    }

    if (isReadOnly()) {
      return false;
    } else if (hasAllowedValues()) {
      for (int i = 0; i < getAllowedValues().length; i++) {
        if (areEquals(val, getAllowedValues()[i])) {
          return true;
        }
      }
      return false;
    } else if (hasLimits()) {
      return isInRange(val);
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean areEquals(Float val1, Float val2) {
    return Math.abs(val1 - val2) < GlobalSettings.EPSILON;
  }
}

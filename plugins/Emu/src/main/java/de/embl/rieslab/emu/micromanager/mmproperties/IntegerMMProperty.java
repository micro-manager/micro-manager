package de.embl.rieslab.emu.micromanager.mmproperties;

import de.embl.rieslab.emu.controller.log.Logger;
import de.embl.rieslab.emu.utils.EmuUtils;
import mmcorej.CMMCore;

/**
 * Wrapper for a Micro-manager device property with integer value. The property can be
 * read-only or not, with limits or with a set of allowed values.
 *
 * @author Joran Deschamps
 */
public class IntegerMMProperty extends MMProperty<Integer> {

    /**
     * Builds an integer MMProperty without limits or allowed values. The property can be read-only.
     *
     * @param core          Micro-manager core.
     * @param logger        Log manager.
     * @param deviceLabel   Label of the parent device as defined in Micro-manager.
     * @param propertyLabel Label of the device property as defined in Micro-manager.
     * @param readOnly      True if the device property is read-only, false otherwise.
     */
    IntegerMMProperty(CMMCore core, Logger logger, String deviceLabel, String propertyLabel, boolean readOnly) {
        super(core, logger, MMProperty.MMPropertyType.INTEGER, deviceLabel, propertyLabel, readOnly);
    }

    /**
     * Builds an integer MMProperty with limits.
     *
     * @param core          Micro-manager core.
     * @param logger        Log manager.
     * @param deviceLabel   Label of the parent device as defined in Micro-manager.
     * @param propertyLabel Label of the device property as defined in Micro-manager.
     * @param upperLimit    Upper limit of the device property value.
     * @param lowerLimit    Lower limit of the device property value.
     */
    IntegerMMProperty(CMMCore core, Logger logger, String deviceLabel, String propertyLabel, double upLimit, double downLimit) {
        super(core, logger, MMProperty.MMPropertyType.INTEGER, deviceLabel, propertyLabel, upLimit, downLimit);
    }

    /**
     * Builds an integer MMProperty with allowed values.
     *
     * @param core          Micro-manager core.
     * @param deviceLabel   Label of the parent device as defined in Micro-manager.
     * @param propertyLabel Label of the device property as defined in Micro-manager.
     * @param allowedValues Array of allowed values.
     */
    IntegerMMProperty(CMMCore core, Logger logger, String deviceLabel, String propertyLabel, String[] allowedValues) {
        super(core, logger, MMProperty.MMPropertyType.INTEGER, deviceLabel, propertyLabel, allowedValues);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer convertToValue(String s) {
        if (EmuUtils.isInteger(s)) {
            return Integer.valueOf(s);
        } else if (EmuUtils.isNumeric(s)) {
            return Double.valueOf(s).intValue();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer convertToValue(int s) {
        return s;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer convertToValue(double s) {
        Double val = new Double(s);
        return val.intValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer[] arrayFromStrings(String[] s) {
        Integer[] allowedVal = new Integer[s.length];
        for (int i = 0; i < s.length; i++) {
            allowedVal[i] = convertToValue(s[i]);
        }
        return allowedVal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String convertToString(Integer val) {
        return val.toString();
    }

    /**
     * {@inheritDoc}
     */
    private boolean isInRange(Integer val) {
        if (hasLimits()) {
            if (val <= getMax() && val >= getMin()) {
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(Integer val) {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean areEquals(Integer val1, Integer val2) {
        return val1.equals(val2);
    }

}

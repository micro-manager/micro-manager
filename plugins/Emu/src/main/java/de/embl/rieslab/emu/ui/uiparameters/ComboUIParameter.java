package de.embl.rieslab.emu.ui.uiparameters;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;

/**
 * UIParameter represented by a String value that can only take a finite number of values.
 * The possible values are passed to the constructor and only values contained in the array
 * will be accepted, otherwise the first value will be chosen.
 * <p>
 * This UIParameter can be used to define a set of possible values.
 *
 * @author Joran Deschamps
 */
public class ComboUIParameter extends UIParameter<String> {

    private String[] comboValues_;

    /**
     * Constructor, a String array of allowed values must be passed as well as the index of the default
     * value in the array. If the index is not valid, then the first entry of the array is used as
     * default.
     *
     * @param owner         ConfigurablePanel that instantiated the UIParameter
     * @param label         Label of the UIParameter
     * @param description   Description of the UIParameter
     * @param allowedValues Array of allowed values the UIParameter can take
     * @param ind           Index of the UIParameter default value
     */
    public ComboUIParameter(ConfigurablePanel owner, String label, String description, String[] allowedValues, int ind) {
        super(owner, label, description);

        if (allowedValues == null) {
            throw new NullPointerException("The allowed values array cannot be null.");
        }
        for (String s : allowedValues) {
            if (s == null) {
                throw new NullPointerException("The allowed values cannot be null.");
            }
        }

        comboValues_ = allowedValues;
        if (ind >= 0 && ind < comboValues_.length) {
            setValue(comboValues_[ind]);
        } else {
            throw new IllegalArgumentException("[" + ind + "] is not a valid index, it should obey: 0 <= ind < " + comboValues_.length + ".");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UIParameterType getType() {
        return UIParameterType.COMBO;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSuitable(String val) {
        if (val == null) {
            return false;
        }

        for (int i = 0; i < comboValues_.length; i++) {
            if (comboValues_[i].equals(val)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String convertValue(String val) {
        return val;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStringValue() {
        return getValue();
    }

    /**
     * Returns an array of the ComboUIProperty values.
     *
     * @return Array of the values.
     */
    public String[] getComboValues() {
        return comboValues_;
    }

}

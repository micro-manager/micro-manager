package de.embl.rieslab.emu.ui.uiparameters;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;

/**
 * UIParameter of boolean nature. Can be used for instance to enable or disable some
 * aspects of a ConfigurablePanel.
 *
 * @author Joran Deschamps
 */
public class BoolUIParameter extends UIParameter<Boolean> {

    public BoolUIParameter(ConfigurablePanel owner, String label, String description, boolean value) {
        super(owner, label, description);

        setValue(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UIParameterType getType() {
        return UIParameterType.BOOL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSuitable(String val) {
        if (val == null) {
            return false;
        }

        if (val.equals("true") || val.equals("false")) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Boolean convertValue(String val) {
        if (val.equals("true")) {
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStringValue() {
        return String.valueOf(getValue());
    }


}

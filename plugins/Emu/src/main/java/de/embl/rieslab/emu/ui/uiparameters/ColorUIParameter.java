package de.embl.rieslab.emu.ui.uiparameters;

import java.awt.Color;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;
import de.embl.rieslab.emu.utils.ColorRepository;

/**
 * UIParameter representing a {@link java.awt.Color}. It can be used to change
 * the colors of JLabels or JButtons for instance.
 *
 * @author Joran Deschamps
 */
public class ColorUIParameter extends UIParameter<Color> {

    /**
     * Constructor, a default color value must be passed.
     *
     * @param owner       ConfigurablePanel that instantiated the UIParameter.
     * @param label       Name of the UIParameter
     * @param description Description of the UIParameter
     * @param value       Default Color value of the UIParameter
     */
    public ColorUIParameter(ConfigurablePanel owner, String label, String description, Color value) {
        super(owner, label, description);

        if (value == null) {
            throw new NullPointerException("The default value cannot be null.");
        }

        setValue(value);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public UIParameterType getType() {
        return UIParameterType.COLOR;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSuitable(String val) {
        if (val == null) {
            return false;
        }
        return ColorRepository.isColor(val);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Color convertValue(String val) {
        return ColorRepository.getColor(val);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStringValue() {
        return ColorRepository.getStringColor(getValue());
    }

}

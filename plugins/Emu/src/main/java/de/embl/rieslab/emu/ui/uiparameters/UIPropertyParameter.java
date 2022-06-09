package de.embl.rieslab.emu.ui.uiparameters;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;
import de.embl.rieslab.emu.ui.uiproperties.flag.PropertyFlag;

/**
 * UIParameter corresponding to a UIProperty of the UIPlugin. This is useful when
 * other ConfigurablePanels need to access a UIProperty. Since at compilation time
 * it is not known which UIProperty will be used to map the MMProperty of interest,
 * then the user can set it in the configuration wizard. This is an alternative to
 * creating several identical UIProperties from different ConfigurablePanels. In
 * particular when the UIProperty is to be used in a SwingWorker or a another Thread
 * without explicit interactions between the ConfigurablePanel's JComponents and the
 * UIProperty.
 * <p>
 * A PropertyFlag can be given to select the relevant UIProperties from the list
 * of possible values in the configuration wizard.
 *
 * @author Joran Deschamps
 */
public class UIPropertyParameter extends UIParameter<String> {

    public static String NO_PROPERTY = "None";

    private PropertyFlag propertyFlag_;

    /**
     * Constructor with a PropertyFlag to select the relevant UIProperties.
     *
     * @param owner        ConfigurablePanel that instantiated the UIParameter
     * @param name         Name of the UIParameter
     * @param description  Description of the UIParameter
     * @param propertyflag PropertyFlag used to select UIProperties
     */
    public UIPropertyParameter(ConfigurablePanel owner, String name, String description, PropertyFlag propertyflag) {
        super(owner, name, description);

        if (propertyflag == null) {
            throw new NullPointerException("The UIPropertyParameter's property flag cannot be null.");
        }

        propertyFlag_ = propertyflag;

        setValue(NO_PROPERTY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UIParameterType getType() {
        return UIParameterType.UIPROPERTY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSuitable(String val) {
        if (val == null) {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String convertValue(String val) {
        return val;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStringValue() {
        return getValue();
    }

    public PropertyFlag getFlag() {
        return propertyFlag_;
    }
}

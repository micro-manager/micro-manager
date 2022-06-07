package de.embl.rieslab.emu.ui.uiparameters;

import de.embl.rieslab.emu.ui.ConfigurablePanel;

/**
 * UIParameters are used to let the user specify aspects of the ConfigurablePanels through
 * the configuration wizard.
 *
 * @param <T> Type of the parameter
 * @author Joran Deschamps
 */
public abstract class UIParameter<T> {

    private String label_;
    private String description_;
    private T value_;
    private String ownername_;

    /**
     * Constructor.
     *
     * @param owner       ConfigurablePanel that instantiated the UIParameter.
     * @param label       Label of the UIParameter.
     * @param description Description of the UIParameters, used in the configuration wizard help.
     */
    public UIParameter(ConfigurablePanel owner, String label, String description) {
        if (owner == null) {
            throw new NullPointerException("The ConfigurablePanel cannot be null.");
        }
        if (label == null) {
            throw new NullPointerException("The UIParameter label cannot be null.");
        }
        if (description == null) {
            throw new NullPointerException("The UIParameter description cannot be null.");
        }

        ownername_ = owner.getPanelLabel();
        label_ = label;
        description_ = description;
    }

    /**
     * Methods to generate the hash used in EMU for the UIParameter of a ConfigurablePanel.
     *
     * @param owner         ConfigurablePanel declaring the UIParameter
     * @param parameterName Label of the UIParameter
     * @return The UIParameter hash
     */
    public static String getHash(ConfigurablePanel owner, String parameterName) {
        return owner.getPanelLabel() + " - " + parameterName;
    }

    /**
     * Returns the UIParameter's hash: {ConfigurablePanel's label}-{UIParameter's label}.
     *
     * @return UIParameter's hash.
     */
    public String getHash() {
        return ownername_ + " - " + label_;
    }

    /**
     * Returns the label of the UIParameter.
     *
     * @return UIParameter's label
     */
    public String getLabel() {
        return label_;
    }

    /**
     * Returns the description of the UIParameter.
     *
     * @return UIparameter's description
     */
    public String getDescription() {
        return description_;
    }

    /**
     * Returns the value of the UIParameter.
     *
     * @return UIParameter's value
     */
    public T getValue() {
        return value_;
    }

    /**
     * Sets the value of the UIParameter.
     *
     * @param val New value
     */
    protected void setValue(T val) {
        value_ = val;
    }

    /**
     * Returns the type of the UIParameter. This method is used to check the compatibility of UIParameters.
     *
     * @return The UIParameter's type
     */
    public abstract UIParameterType getType();

    /**
     * Checks if the String {@code val} is suitable for this UIParameter.
     *
     * @param val String value.
     * @return True if {@code val} is suitable, false otherwise.
     */
    public abstract boolean isSuitable(String val);

    /**
     * Converts the String {@code val} to the UIParameter's type.
     *
     * @param val String to convert
     * @return {@code val} in the type of the UIParameter
     */
    protected abstract T convertValue(String val);

    /**
     * Returns the value of the UIParameter as a String.
     *
     * @return String value.
     */
    public abstract String getStringValue();

    /**
     * Sets the value of the UIParameter from a String.
     *
     * @param val New value as a String
     */
    public void setStringValue(String val) {
        if (isSuitable(val)) {
            value_ = convertValue(val);
        }
    }

    /**
     * UIParameter type.
     *
     * @author Joran Deschamps
     */
    public enum UIParameterType {
        INTEGER("Integer"), DOUBLE("Double"), STRING("String"), COLOR("Color"), BOOL("Boolean"), COMBO("Combo"), UIPROPERTY("UIProperty"), NONE("None");

        private String value;

        private UIParameterType(String value) {
            this.value = value;
        }

        public String getTypeValue() {
            return value;
        }
    }

    ;

}

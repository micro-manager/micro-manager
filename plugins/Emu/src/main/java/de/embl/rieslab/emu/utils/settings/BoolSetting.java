package de.embl.rieslab.emu.utils.settings;

import de.embl.rieslab.emu.utils.EmuUtils;

/**
 * A {@link Setting} with boolean value.
 *
 * @author Joran Deschamps
 * @see Setting
 */
public class BoolSetting extends Setting<Boolean> {

    /**
     * Constructor.
     *
     * @param name        Short name of the setting.
     * @param description Description as it will appear in the help.
     * @param default_val Default value for the setting.
     */
    public BoolSetting(String name, String description, Boolean default_val) {
        super(name, description, Setting.SettingType.BOOL, default_val);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getStringValue(Boolean val) {
        return String.valueOf(val);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValueCompatible(String val) {
        return EmuUtils.isBool(val);
    }

    @Override
    protected Boolean getTypedValue(String val) {
        // The superclass already checks for compatibility
        return EmuUtils.convertStringToBool(val);
    }

}

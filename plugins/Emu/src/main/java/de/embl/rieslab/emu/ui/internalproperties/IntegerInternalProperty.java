package de.embl.rieslab.emu.ui.internalproperties;

import java.util.concurrent.atomic.AtomicInteger;

import de.embl.rieslab.emu.ui.ConfigurablePanel;

public class IntegerInternalProperty extends InternalProperty<AtomicInteger, Integer> {

    public IntegerInternalProperty(ConfigurablePanel owner, String name, Integer defaultvalue) {
        super(owner, name, defaultvalue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InternalPropertyType getType() {
        return InternalPropertyType.INTEGER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Integer convertValue(AtomicInteger val) {
        return val.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setAtomicValue(Integer val) {
        getAtomicValue().set(val);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AtomicInteger initializeDefault(Integer defaultval) {
        return new AtomicInteger(defaultval);
    }

}

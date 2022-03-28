/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.devices.crisp;

/**
 * The type of controller that CRISP is connected to.
 *
 */
public enum ControllerType {
    TIGER("TIGER"),
    MS2000("MS2000"),
    NONE("NONE");
    
    private final String name;
    
    ControllerType(final String name) {
        this.name = name;
    }
    
    @Override
    public String toString() {
        return name;
    }
}

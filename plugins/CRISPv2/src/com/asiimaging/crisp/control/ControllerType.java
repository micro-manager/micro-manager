///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package com.asiimaging.crisp.control;


/**
 * The type of controller that CRISP is connected to.
 *
 */
public enum ControllerType {
    TIGER("TIGER"),
    MS2000("MS2000"),
    NONE("NONE");
    
    private final String name;
    
    private ControllerType(final String name) {
        this.name = name;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
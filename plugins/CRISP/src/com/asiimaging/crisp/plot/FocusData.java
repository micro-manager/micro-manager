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

package com.asiimaging.crisp.plot;

/**
 * An individual data point in a FocusDataSet.
 *
 */
public class FocusData {

    private final double time;
    private final double position;
    private final double error;
    
    public FocusData(final double time, final double position, final double error) {
        this.time = time;
        this.position = position;
        this.error = error;
    }
    
    @Override
    public String toString() {
        return String.format("%s(%s, %s, %s)", 
            getClass().getSimpleName(),
            time, position, error);
    }
    
    // FIXME: spaces or no spaces?
    public String toStringCSV() {
        return time + "," + position + "," + error;
    }
    
    public double getTime() {
        return time;
    }
    
    public double getPosition() {
        return position;
    }
    
    public double getError() {
        return error;
    }
}

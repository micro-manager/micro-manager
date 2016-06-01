///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
//

package org.micromanager.plugins.magellan.surfacesandregions;

import org.micromanager.plugins.magellan.coordinates.XYStagePosition;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Henry
 */
public interface XYFootprint {
   
   /**
    * @param tileOverlapPercent
    * @return read only list of XY positions after updating them to reflect potential changes in overlap
    */
    public List<XYStagePosition> getXYPositions(double tileOverlapPercent) throws InterruptedException;

    public List<XYStagePosition> getXYPositionsNoUpdate() throws InterruptedException;
    
   public String getXYDevice();
    
}

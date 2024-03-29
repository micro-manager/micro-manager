///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2016
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

package org.micromanager.magellan.internal.surfacesandregions;


public interface SurfaceGridListener {
   
   //Data changed or name changed
   void surfaceOrGridChanged(XYFootprint f);
   
   void surfaceOrGridDeleted(XYFootprint f);
   
   void surfaceOrGridCreated(XYFootprint f);

   void surfaceOrGridRenamed(XYFootprint f);
   
   void surfaceInterpolationUpdated(SurfaceInterpolator s);
   
}

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

import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;

public interface SurfaceGridListener {

  // Data changed or name changed
  public void SurfaceOrGridChanged(XYFootprint f);

  public void SurfaceOrGridDeleted(XYFootprint f);

  public void SurfaceOrGridCreated(XYFootprint f);

  public void SurfaceOrGridRenamed(XYFootprint f);

  public void SurfaceInterpolationUpdated(SurfaceInterpolator s);
}

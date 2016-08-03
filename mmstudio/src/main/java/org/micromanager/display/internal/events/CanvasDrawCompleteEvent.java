///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
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

package org.micromanager.display.internal.events;

import java.awt.Graphics;

/**
 * This class is used to signify when the canvas has completed performing a
 * paint event. It provides access to the Graphics object used to perform the
 * draw, primarily so that the ExportMovieDlg can recognize when the draw
 * actions it requested have been completed.
 */
public final class CanvasDrawCompleteEvent {
   private Graphics g_;
   public CanvasDrawCompleteEvent(Graphics g) {
      g_ = g;
   }

   public Graphics getGraphics() {
      return g_;
   }
}

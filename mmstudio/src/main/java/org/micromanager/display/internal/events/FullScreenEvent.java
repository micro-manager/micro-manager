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

import java.awt.GraphicsConfiguration;


/**
 * Indicates that full-screen mode has been turned on/off for the given
 * monitor.
 */
public class FullScreenEvent {
   private GraphicsConfiguration displayConfig_;
   private boolean isFullScreen_;

   public FullScreenEvent(GraphicsConfiguration config,
         boolean isFullScreen) {
      displayConfig_ = config;
      isFullScreen_ = isFullScreen;
   }

   public GraphicsConfiguration getConfig() {
      return displayConfig_;
   }

   public boolean getIsFullScreen() {
      return isFullScreen_;
   }
}

///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display API
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

package org.micromanager.display;

/**
 * The Inspector is the floating window that provides access to various
 * information and controls for image display windows, such as the histograms,
 * metadata, and overlay controls. This interface provides access to some
 * useful methods of the Inspector frame that classes implementing the
 * InspectorPanel interface may want access to.
 */
public interface Inspector {
   /**
    * Cause the Inspector frame to re-pack itself. This is useful if your
    * panel has changed shape, for example due to showing or hiding part of
    * its contents.
    */
   public void relayout();
}

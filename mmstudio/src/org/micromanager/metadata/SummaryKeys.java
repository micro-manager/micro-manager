///////////////////////////////////////////////////////////////////////////////
//FILE:          SummaryKeys.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, April 21, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
//               100X Imaging Inc, www.100ximaging.com, 2008
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
// CVS:          $Id$
//
package org.micromanager.metadata;

/**
 * List of keywords for the "acquisition run" level information. 
 *
 * Version History:
 * 6: Added Summary Keys 'frameinterval_ms' and 'z-step_ms'
 */
public class SummaryKeys {
   public static final String SUMMARY = "Summary";
   public static final String SYSTEM_STATE = "SystemState";
   public static final String GUID = "GUID";
   public static final String NUM_FRAMES="Frames";
   public static final String NUM_CHANNELS="Channels";
   public static final String NUM_SLICES="Slices";
   public static final String TIME="Time";
   public static final String POSITION="Position";
   public static final String IMAGE_WIDTH="Width";
   public static final String IMAGE_HEIGHT="Height";
   public static final String IMAGE_DEPTH="Depth";
   public static final String IJ_IMAGE_TYPE="IJType";
   public static final String CHANNEL_COLORS="ChColors";
   public static final String CHANNEL_CONTRAST_MIN="ChContrastMin";
   public static final String CHANNEL_CONTRAST_MAX="ChContrastMax";
   public static final String CHANNEL_NAMES="ChNames";
   public static final String METADATA_VERSION="MetadataVersion";
   public static final String METADATA_SOURCE="Source";
   public static final String IMAGE_PIXEL_SIZE_UM = "PixelSize_um";
   public static final String IMAGE_PIXEL_ASPECT = "PixelAspect";
   public static final String IMAGE_INTERVAL_MS = "Interval_ms";
   public static final String IMAGE_Z_STEP_UM = "z-step_um";
   public static final int VERSION = 8;
   public static final String SOURCE = "Micro-Manager";
   public static final String COMMENT = "Comment";
   public static final String GRID_ROW = "GridRow";
   public static final String GRID_COLUMN = "GridColumn";
   public static final String POSITION_PROPERTIES = "PositionProperties";
}

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
 * Keywords with OBJ suffix refer to JSON objects.
 * Keywords with ARRAY suffix refer to JSONArray data.
 * Other keywords refer to simple values.
 *
 * Version History:
 * 6: Added Summary Keys 'frameinterval_ms' and 'z-step_ms'
 */
public class SummaryKeys {
   // top level keys referring to main sections of the metadata
   public static final String SUMMARY_OBJ = "Summary";
   public static final String POSITION_PROPERTIES_OBJ = "PositionProperties";
   public static final String SYSTEM_STATE_OBJ = "SystemState";
   // top level data objects are also all JSON objects corresponding to individual
   // images. Keys for image metadata are generated on the fly, based on the rules
   // specified in ImageKey class.
   
   // keys contained in the Summary section
   public static final String GUID = "GUID";
   public static final String NUM_FRAMES="Frames";
   public static final String NUM_CHANNELS="Channels";
   public static final String NUM_SLICES="Slices";
   public static final String TIME="Time";
   public static final String DATE="Date";
   public static final String POSITION="Position";
   public static final String IMAGE_WIDTH="Width";
   public static final String IMAGE_HEIGHT="Height";
   public static final String IMAGE_DEPTH="Depth";
   public static final String IJ_IMAGE_TYPE="IJType";
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
   public static final String USER_NAME = "UserName";
   public static final String COMPUTER_NAME = "ComputerName";
   
   // keys pointing to array data
   public static final String CHANNEL_COLORS_ARRAY="ChColors";
   public static final String CHANNEL_CONTRAST_MIN_ARRAY="ChContrastMin";
   public static final String CHANNEL_CONTRAST_MAX_ARRAY="ChContrastMax";
   public static final String CHANNEL_NAMES_ARRAY="ChNames";
}

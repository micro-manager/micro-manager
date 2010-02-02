///////////////////////////////////////////////////////////////////////////////
//FILE:          ImagePropertyKeys.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, May 2, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
 * List of image properties (keywords).
 * These keys are associated with each individual image,
 * but not guaranteed to be available.
 */
public class ImagePropertyKeys {
   public static final String FILE="FileName";
   public static final String FRAME="Frame";
   public static final String SLICE="Slice";
   public static final String CHANNEL="Channel";
   public static final String TIME="Time";
   public static final String DATE="Date";
   public static final String ELAPSED_TIME_US="ElapsedTime-us";
   public static final String ELAPSED_TIME_MS="ElapsedTime-ms";
   public static final String ELAPSED_TIME="ElapsedTime";
   public static final String OBJECTIVE="Objective";
   public static final String Z_UM="Z-um";
   public static final String X_UM="X-um";
   public static final String Y_UM="Y-um";
   public static final String EXPOSURE_MS = "Exposure-ms";
}

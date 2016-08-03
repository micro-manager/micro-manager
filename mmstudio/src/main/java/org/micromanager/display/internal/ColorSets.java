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

package org.micromanager.display.internal;

import java.awt.Color;

/**
 * This class is a storage for two sets of colors that we provide as sane
 * defaults to the user.
 */
public final class ColorSets {
   public static final Color[] RGB_COLORS = new Color[] {
         Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA,
         Color.YELLOW, Color.WHITE};
   // Colors adapted from table at
   // http://www.nature.com/nmeth/journal/v8/n6/full/nmeth.1618.html
   // Selection of the first three colors based on recommendations from
   // Ankur Jain at the Vale lab.
   public static final Color[] COLORBLIND_COLORS = new Color[] {
         new Color(0, 114, 178), new Color(213, 94, 0),
         new Color(0, 158, 115), Color.RED, Color.CYAN, Color.YELLOW,
         Color.WHITE};
}

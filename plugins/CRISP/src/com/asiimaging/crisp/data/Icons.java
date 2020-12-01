///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
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

package com.asiimaging.crisp.data;

import javax.swing.ImageIcon;

import org.micromanager.MMStudio;

import com.swtdesigner.SwingResourceManager;

public final class Icons {
    private static final String MICROSCOPE_ICON_PATH = "/org/micromanager/icons/microscope.gif";
    public static final ImageIcon MICROSCOPE_ICON = SwingResourceManager.getIcon(MMStudio.class, MICROSCOPE_ICON_PATH);
}

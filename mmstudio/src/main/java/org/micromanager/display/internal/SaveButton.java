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

import com.bulenkov.iconloader.IconLoader;
import java.awt.event.MouseEvent;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.event.MouseInputAdapter;
import org.micromanager.Studio;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;

/**
 * This class provides a button for saving the current datastore to TIFF.
 */
public final class SaveButton extends JButton {
   public SaveButton(final Studio studio, final Datastore store, final DisplayWindow display) {
      setToolTipText("Save data as a Micro-Manager dataset.");

      addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            try {
               store.save(display.getWindow());
            } catch (IOException ex) {
               studio.logs().showError(ex, "Failed to save data to " + store.getSavePath() );
            }
         }
      });

      // This icon is a slight modification of the public-domain icon at
      // https://openclipart.org/detail/34579/tango-media-floppy
      setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/disk.png"));
   }
}

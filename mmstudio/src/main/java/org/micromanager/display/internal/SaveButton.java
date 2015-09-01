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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.micromanager.data.Datastore;

import org.micromanager.display.DisplayWindow;

/**
 * This class provides a button for saving the current datastore to TIFF.
 */
public class SaveButton extends JButton {
   public SaveButton(final Datastore store, final DisplayWindow display) {
      setToolTipText("Save data as a Micro-Manager dataset.");

      final JButton staticThis = this;
      addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            store.save(display.getAsWindow());
         }
      });

      // This icon is a slight modification of the public-domain icon at
      // https://openclipart.org/detail/34579/tango-media-floppy
      setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/disk.png"));
   }
}

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
import java.awt.Window;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.micromanager.data.Datastore;

/**
 * This class provides a button for saving the current datastore to TIFF.
 */
public class SaveButton extends JButton {
   private JPopupMenu menu_;

   public SaveButton(final Datastore store, final Window window) {
      setToolTipText("Save data as a Micro-Manager dataset.");
      menu_ = new JPopupMenu();
      JMenuItem separateImages = new JMenuItem("Save to Separate Image Files");
      separateImages.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            store.save(Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES, window);
         }
      });
      menu_.add(separateImages);
      JMenuItem multistack = new JMenuItem("Save to Single Multistack Image");
      multistack.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            store.save(Datastore.SaveMode.MULTIPAGE_TIFF, window);
         }
      });
      menu_.add(multistack);

      final JButton staticThis = this;
      addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            menu_.show(staticThis, e.getX(), e.getY());
         }
      });

      // This icon is a slight modification of the public-domain icon at
      // https://openclipart.org/detail/34579/tango-media-floppy
      setIcon(IconLoader.getIcon(
            "/org/micromanager/internal/icons/disk.png"));
   }
}

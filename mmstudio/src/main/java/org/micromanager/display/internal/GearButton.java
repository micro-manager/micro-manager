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

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.inspector.InspectorFrame;
import org.micromanager.internal.LineProfile;

/**
 * This class provides access to various rarely-used functions (like save or
 * duplicate) via a dropdown menu.
 */
public class GearButton extends JButton {
   private JPopupMenu menu_;

   public GearButton(final DisplayWindow display) {
      setToolTipText("Access additional commands");
      menu_ = new JPopupMenu();
      JMenuItem openInspector = new JMenuItem("Open New Inspector Window");
      openInspector.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new InspectorFrame(display);
         }
      });
      menu_.add(openInspector);

      JMenuItem duplicate = new JMenuItem("Duplicate This Window");
      duplicate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            display.duplicate();
         }
      });
      menu_.add(duplicate);

      menu_.addSeparator();

      JMenuItem lineProfile = new JMenuItem("Show Line Profile");
      lineProfile.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new LineProfile(display);
         }
      });
      menu_.add(lineProfile);

      menu_.addSeparator();

      JMenuItem movieExport = new JMenuItem("Export Images As Displayed");
      movieExport.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new ExportMovieDlg(display);
         }
      });
      menu_.add(movieExport);

      final JButton staticThis = this;
      addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            menu_.show(staticThis, e.getX(), e.getY());
         }
      });

      // This icon adapted from the public domain icon at
      // https://openclipart.org/detail/35533/tango-emblem-system
      setIcon(IconLoader.getIcon(
               "/org/micromanager/icons/gear.png"));
   }
}

///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
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

package org.micromanager.quickaccess.internal.controls;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.events.AcquisitionEndedEvent;
import org.micromanager.events.AcquisitionStartedEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "MDA" buttons (one to open the dialog, another to just run an
 * acquisition with current settings).
 */
@Plugin(type = WidgetPlugin.class)
public final class MDAButtons extends WidgetPlugin implements SciJavaPlugin {
   private MMStudio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = (MMStudio) studio;
   }

   @Override
   public String getName() {
      return "MDA";
   }

   @Override
   public String getHelpText() {
      return "Access the MDA dialog and run acquisitions.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright (c) 2015 Open Imaging, Inc.";
   }

   @Override
   public ImageIcon getIcon() {
      return new ImageIcon(IconLoader.loadFromResource(
               "/org/micromanager/icons/film@2x.png"));
   }

   // We are not actually configurable.
   @Override
   public JComponent createControl(PropertyMap config) {
      JPanel result = new JPanel(new MigLayout("flowx, insets 0, gap 0"));
      JButton dialogButton = new JButton("<html>Multi-D<br>Acq.</html>",
            IconLoader.getIcon("/org/micromanager/icons/film.png")) {
         @Override
         public Dimension getPreferredSize() {
            // Take up half a cell.
            Dimension result = QuickAccessPlugin.getPaddedCellSize();
            result.width = (int) (result.width * .4);
            return result;
         }
      };
      dialogButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.openAcqControlDialog();
         }
      });
      dialogButton.setFont(GUIUtils.buttonFont);
      dialogButton.setMargin(new Insets(0, 0, 0, 0));
      result.add(dialogButton);

      // This runs or stops acquisitions.
      // HACK: the empty icon is a requirement for the button to be small
      // enough to fit into our layout; with no icon, the button is too wide.
      final JButton runButton = new JButton("Acquire!",
            IconLoader.getIcon("/org/micromanager/icons/empty.png")) {
         @Override
         public Dimension getPreferredSize() {
            // Take up under half a cell.
            Dimension result = QuickAccessPlugin.getPaddedCellSize();
            result.width = (int) (result.width * .4);
            return result;
         }
         @Override
         public Dimension getMaximumSize() {
            return getPreferredSize();
         }
         @Subscribe
         public void onAcquisitionStart(AcquisitionStartedEvent event) {
            if (studio_.acquisitions().isOurAcquisition(event.getSource())) {
               setIcon(IconLoader.getIcon(
                        "/org/micromanager/icons/cancel.png"));
               setText("Stop!");
            }
         }
         @Subscribe
         public void onAcquisitionEnded(AcquisitionEndedEvent event) {
            if (studio_.acquisitions().isOurAcquisition(event.getSource())) {
               setIcon(null);
               setText("Acquire!");
            }
         }
      };
      studio_.events().registerForEvents(runButton);
      runButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            if (runButton.getIcon() == null) {
               // Move this off the EDT.
               new Thread(new Runnable() {
                  @Override
                  public void run() {
                     try {
                        studio_.acquisitions().runAcquisition();
                     }
                     catch (Exception e) {
                        studio_.logs().showError(e,
                           "Error running acquisition.");
                     }
                  }
               }).start();
            }
            else {
               studio_.acquisitions().haltAcquisition();
            }
         }
      });
      runButton.setFont(GUIUtils.buttonFont);
      runButton.setMargin(new Insets(0, 0, 0, 0));
      result.add(runButton);

      return result;
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      return PropertyMaps.builder().build();
   }
}

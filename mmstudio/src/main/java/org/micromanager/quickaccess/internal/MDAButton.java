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

package org.micromanager.quickaccess.internal;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Insets;
import java.awt.Frame;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.events.AcquisitionEndedEvent;
import org.micromanager.events.AcquisitionStartedEvent;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "MDA" button logic.
 */
@Plugin(type = WidgetPlugin.class)
public class MDAButton implements WidgetPlugin, SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
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
      JPanel result = new JPanel(new MigLayout("flowy, insets 0, gap 0"));
      JButton dialogButton = new JButton("Multi-D Acq",
            IconLoader.getIcon("/org/micromanager/icons/film.png"));
      dialogButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            ((MMStudio) studio_).openAcqControlDialog();
         }
      });
      dialogButton.setFont(GUIUtils.buttonFont);
      dialogButton.setMargin(new Insets(0, 0, 0, 0));
      result.add(dialogButton);

      // This runs or stops acquisitions.
      final JButton runButton = new JButton("Acquire!") {
         @Subscribe
         public void onAcquisitionStart(AcquisitionStartedEvent event) {
            setIcon(IconLoader.getIcon(
                     "/org/micromanager/icons/cancel.png"));
         }
         @Subscribe
         public void onAcquisitionEnded(AcquisitionEndedEvent event) {
            setIcon(null);
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
                        studio_.compat().runAcquisition();
                     }
                     catch (Exception e) {
                        studio_.logs().showError(e,
                           "Error running acquisition.");
                     }
                  }
               }).start();
            }
            else {
               studio_.compat().haltAcquisition();
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
      return studio_.data().getPropertyMapBuilder().build();
   }

   @Override
   public Dimension getSize() {
      return new Dimension(1, 2);
   }
}

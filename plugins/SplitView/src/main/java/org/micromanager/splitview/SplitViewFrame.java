///////////////////////////////////////////////////////////////////////////////
//FILE:          SplitViewFrame.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2012
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


/**

 * Created on Aug 28, 2011, 9:41:57 PM
 */
package org.micromanager.splitview;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import net.miginfocom.swing.MigLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.MMStudio;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.display.RequestToCloseEvent;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.MMTags;

/**
 * Micro-Manager plugin that can split the acquired image top-down or left-right
 * and arrange the split images along the channel axis.
 *
 * @author nico, modified by Chris Weisiger
 */
public class SplitViewFrame extends MMFrame implements ProcessorConfigurator {
   private static final int DEFAULT_WIN_X = 100;
   private static final int DEFAULT_WIN_Y = 100;
   private static final String ORIENTATION = "Orientation";
   private static final String NUM_SPLITS = "numSplits";
   private static final String[] SPLIT_OPTIONS = new String[] {"Two", "Three",
      "Four", "Five"};
   public static final String LR = "lr";
   public static final String TB = "tb";

   private final Studio studio_;
   private final CMMCore core_;
   private String orientation_;
   private int numSplits_;
   private JRadioButton lrRadio_;
   private JRadioButton tbRadio_;

   public SplitViewFrame(PropertyMap settings, Studio studio) {
      studio_ = studio;
      core_ = studio_.getCMMCore();

      orientation_ = settings.getString("orientation",
            studio_.profile().getString(getClass(), ORIENTATION, LR));
      numSplits_ = settings.getInt("splits",
            studio_.profile().getInt(getClass(), NUM_SPLITS, 2));

      initComponents();

      loadAndRestorePosition(DEFAULT_WIN_X, DEFAULT_WIN_Y);

      lrRadio_.setSelected(orientation_.equals(LR));
      tbRadio_.setSelected(orientation_.equals(TB));
   }

   @Override
   public PropertyMap getSettings() {
      PropertyMap.PropertyMapBuilder builder = studio_.data().getPropertyMapBuilder();
      builder.putString("orientation", orientation_);
      builder.putInt("splits", numSplits_);
      return builder.build();
   }

   @Override
   public void showGUI() {
      setVisible(true);
   }

   @Override
   public void cleanup() {
      dispose();
   }

   /** This method is called from within the constructor to
    * initialize the form.
    */
   @SuppressWarnings("unchecked")
   private void initComponents() {
      setTitle("SplitView");
      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

      ButtonGroup group = new ButtonGroup();
      lrRadio_ = new JRadioButton("Left-Right Split");
      tbRadio_ = new JRadioButton("Top-Bottom Split");
      group.add(lrRadio_);
      group.add(tbRadio_);

      lrRadio_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            updateSettings(LR, numSplits_);
         }
      });

      tbRadio_.setText("Top-Bottom Split");
      tbRadio_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            updateSettings(TB, numSplits_);
         }
      });

      setLayout(new MigLayout("flowx"));

      add(new Preview(true), "align center");
      add(new Preview(false), "align center, wrap");
      add(lrRadio_);
      add(tbRadio_, "wrap");

      final JComboBox splitSelector = new JComboBox(SPLIT_OPTIONS);
      splitSelector.setSelectedIndex(numSplits_ - 2);
      splitSelector.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // Because the first option at index 0 is 2 splits, etc.
            updateSettings(orientation_,
                  splitSelector.getSelectedIndex() + 2);
         }
      });
      add(new JLabel("Number of Splits:"), "alignx right");
      add(splitSelector, "wrap");
      add(new JLabel(
               "<html>Note: if the image size does not evenly divide<br> " +
               "the number of splits, then some rows or columns<br>" +
               "from the source image will be discarded.</html>"),
            "span, wrap");
      pack();
   }

   private void updateSettings(String orientation, int numSplits) {
      orientation_ = orientation;
      numSplits_ = numSplits;
      studio_.profile().setString(getClass(), ORIENTATION, orientation);
      studio_.profile().setInt(getClass(), NUM_SPLITS, numSplits_);
      studio_.data().notifyPipelineChanged();
      repaint();
   }

   private class Preview extends JPanel {
      private boolean isLeftRight_;
      public Preview(boolean isLeftRight) {
         isLeftRight_ = isLeftRight;
      }

      @Override
      public Dimension getMinimumSize() {
         return new Dimension(50, 50);
      }

      @Override
      public void paint(Graphics graphics) {
         Graphics2D g = (Graphics2D) graphics;
         g.setColor(Color.WHITE);
         g.fillRect(0, 0, 50, 50);
         g.setColor(Color.BLACK);
         // Draw a box around the outside
         int[] xPoints = new int[] {0, 50, 50, 0};
         int[] yPoints = new int[] {0, 0, 50, 50};
         g.drawPolygon(xPoints, yPoints, 4);
         // Draw dividers.
         int splitSize = 50 / numSplits_;
         for (int i = 0; i < numSplits_; ++i) {
            if (isLeftRight_) {
               g.drawLine(i * splitSize, 0, i * splitSize, 50);
            }
            else {
               g.drawLine(0, i * splitSize, 50, i * splitSize);
            }
         }
      }
   }
}

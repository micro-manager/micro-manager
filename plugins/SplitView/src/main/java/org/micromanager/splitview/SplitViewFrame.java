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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JLabel;

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
 * and display the split image as a two channel image.
 * Note that this class used to use a form editor, but was modified to use
 * MigLayout instead; some remnants of the old system remain.
 *
 * @author nico, modified by Chris Weisiger
 */
public class SplitViewFrame extends MMFrame implements ProcessorConfigurator {

   private final Studio studio_;
   private final CMMCore core_;
   private long imgDepth_;
   private int width_;
   private int height_;
   private int newWidth_;
   private int newHeight_;
   private String orientation_;
   private final int frameXPos_ = 100;
   private final int frameYPos_ = 100;
   private double interval_ = 30;
   private static final String ACQNAME = "Split View";
   public static final String LR = "lr";
   public static final String TB = "tb";
   private static final String TOPLEFTCOLOR = "TopLeftColor";
   private static final String BOTTOMRIGHTCOLOR = "BottomRightColor";
   private static final String ORIENTATION = "Orientation";
   private boolean autoShutterOrg_;
   private String shutterLabel_;
   private boolean shutterOrg_;

   public SplitViewFrame(Studio studio) {
      studio_ = studio;
      core_ = studio_.getCMMCore();

      orientation_ = studio_.profile().getString(this.getClass(), ORIENTATION, LR);

      Font buttonFont = new Font("Arial", Font.BOLD, 10);

      initComponents();

      loadAndRestorePosition(frameXPos_, frameYPos_);

      Dimension buttonSize = new Dimension(120, 20);

      lrRadioButton.setSelected(orientation_.equals(LR));
      tbRadioButton.setSelected(orientation_.equals(TB));
   }

   @Override
   public PropertyMap getSettings() {
      PropertyMap.PropertyMapBuilder builder = studio_.data().getPropertyMapBuilder();
      builder.putString("orientation", orientation_);
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

   private void calculateSize() {
      imgDepth_ = core_.getBytesPerPixel();
      
      width_ = (int) core_.getImageWidth();
      height_ = (int) core_.getImageHeight();

      newWidth_ = SplitViewProcessor.calculateWidth(width_, orientation_);
      newHeight_ = SplitViewProcessor.calculateHeight(height_, orientation_);
   }
  
   /** This method is called from within the constructor to
    * initialize the form.
    */
   @SuppressWarnings("unchecked")
   private void initComponents() {

      buttonGroup1 = new javax.swing.ButtonGroup();
      lrRadioButton = new javax.swing.JRadioButton();
      tbRadioButton = new javax.swing.JRadioButton();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("SplitView");
      addWindowListener(new java.awt.event.WindowAdapter() {
         public void windowClosed(java.awt.event.WindowEvent evt) {
            formWindowClosed(evt);
         }
      });

      buttonGroup1.add(lrRadioButton);
      lrRadioButton.setText("Left-Right Split");
      lrRadioButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            lrRadioButtonActionPerformed(evt);
         }
      });

      buttonGroup1.add(tbRadioButton);
      tbRadioButton.setText("Top-Bottom Split");
      tbRadioButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            tbRadioButtonActionPerformed(evt);
         }
      });

      setLayout(new MigLayout("flowx"));

      add(new JLabel(IconLoader.getIcon("/org/micromanager/icons/lr.png")),
            "align center");
      add(new JLabel(IconLoader.getIcon("/org/micromanager/icons/tb.png")),
            "align center, wrap");
      add(lrRadioButton);
      add(tbRadioButton);
      pack();

   }

    private void lrRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {
       orientation_ = LR;
       studio_.profile().setString(this.getClass(),ORIENTATION, LR);
       studio_.data().notifyPipelineChanged();
    }

    private void tbRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {
       orientation_ = TB;
       studio_.profile().setString(this.getClass(),ORIENTATION, TB);
       studio_.data().notifyPipelineChanged();
    }

    private void formWindowClosed(java.awt.event.WindowEvent evt) {
     
    }

   private javax.swing.ButtonGroup buttonGroup1;
   private javax.swing.JRadioButton lrRadioButton;
   private javax.swing.JRadioButton tbRadioButton;
}

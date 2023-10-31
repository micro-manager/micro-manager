///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//

package org.micromanager.magellan.internal.explore.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.acqj.internal.ZAxis;
import org.micromanager.magellan.internal.explore.ExploreAcquisition;

/**
 * This class holds the controls for a single Z axis.
 */
public class ExploreZSliders extends JPanel {

   private static final int DEFAULT_FPS = 7;


   private JPanel controlsPanel_;
   private ZAxisLimitControlPanel zAxisLimitControlPanel;
   private ZAxisLimitControlPanel zAxisLimitControlPanel_;

   public ExploreZSliders(ExploreAcquisition acquisition, ZAxis zAxis) throws Exception {
      super(new FlowLayout(FlowLayout.LEADING));
      // Same z step for all axes
      zAxisLimitControlPanel_ =  new ZAxisLimitControlPanel(acquisition, zAxis);

      try {
         initComponents();
      } catch (Exception e) {
         throw new RuntimeException("Problem initializing subimage controls");
      }
   }



   @Override
   public Dimension getPreferredSize() {
      return new Dimension(this.getParent().getSize().width, super.getPreferredSize().height);
   }

   public void onDisplayClose() {
      controlsPanel_.removeAll();
      this.remove(controlsPanel_);
      zAxisLimitControlPanel_.onDisplayClose();
      controlsPanel_ = null;
   }



   private void initComponents() {
      controlsPanel_ = new JPanel(new MigLayout("insets 0, fillx, align center", "", "[]0[]0[]"));
      try {

         //add z axis controls
         controlsPanel_.add(zAxisLimitControlPanel_, "span, growx, align center, wrap");


         this.setLayout(new BorderLayout());
         this.add(controlsPanel_, BorderLayout.CENTER);
      } catch (Exception ex) {
         ex.printStackTrace();
         throw new RuntimeException(ex);
      }
   }

   public void updateZDrivelocation(Integer zIndex) {
      zAxisLimitControlPanel_.updateZDrivelocation(zIndex);
   }
}

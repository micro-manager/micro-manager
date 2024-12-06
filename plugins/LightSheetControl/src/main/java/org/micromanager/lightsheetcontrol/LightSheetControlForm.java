/**
 *
 * @author kthorn
 */

package org.micromanager.lightsheetcontrol;

import java.awt.event.ActionEvent;
import java.util.Iterator;
import javax.swing.AbstractListModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.LayoutStyle;
import javax.swing.WindowConstants;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.micromanager.Studio;


public class LightSheetControlForm extends JFrame {

   private final Studio studio_;
   private String stage1_ = "";
   private String stage2_ = "";
   private String multiStageName_ = "";
   private final RPModel rpm_;

   private JButton addRPButton;
   private JButton delAllButton;
   private JButton delRPButton;
   private JButton goToPosButton;
   private JList<ReferencePoint> rpList;
   private JButton updRPButton;
   private JFrame jFrame1;
   private JLabel jLabel1;
   private JScrollPane jScrollPane1;

   /**
    * Creates new form LightSheetControlForm.
    *
    * @param studio - what do we do without it?
    */
   public LightSheetControlForm(Studio studio) {
      studio_ = studio;
      rpm_ = new RPModel();
      Iterator<String> stageIter;

      initComponents();
      this.setTitle("Light Sheet Control");
      //Find multi stage device
      StrVector stages = studio_.core().getLoadedDevicesOfType(DeviceType.StageDevice);
      stageIter = stages.iterator();
      while (stageIter.hasNext()) {
         String devName = "";
         String devLabel = stageIter.next();
         try {
            devName = studio_.core().getDeviceName(devLabel);
         } catch (Exception ex) {
            studio_.logs().logError(ex, "Error when requesting stage name");
         }
         if (devName.equals("Multi Stage")) {
            multiStageName_ = devLabel;
         }
      }
      if (multiStageName_.equals("")) {
         studio_.logs().logError("Cannot find multi stage device");
      }
      try {
         stage1_ = studio_.core().getProperty(multiStageName_, "PhysicalStage-1");
      } catch (Exception ex) {
         studio_.logs().logError(ex, "Error when requesting Stage1 name");
      }
      try {
         stage2_ = studio_.core().getProperty(multiStageName_, "PhysicalStage-2");
      } catch (Exception ex) {
         studio_.logs().logError(ex, "Error when requesting Stage2 name");
      }
   }


   private static class RPModel extends AbstractListModel<ReferencePoint> {

      public ReferencePointList rpList;

      private RPModel() {
         this.rpList = new ReferencePointList();
      }

      @Override
      public int getSize() {
         return rpList.getNumberOfPoints();
      }

      @Override
      public ReferencePoint getElementAt(int index) {
         return rpList.getPoint(index);
      }

      public void addPoint(ReferencePoint r) {
         rpList.addPoint(r);
         fireIntervalAdded(this, rpList.getNumberOfPoints(), rpList.getNumberOfPoints());
      }

      public void deletePoint(int index) {
         rpList.removePoint(index);
         fireIntervalRemoved(this, index, index);
      }

      public ReferencePoint getReferencePoint(int index) {
         ReferencePoint r = rpList.getPoint(index);
         return r;
      }

      public void updatePoint(int index, ReferencePoint r) {
         rpList.replacePoint(index, r);
         fireContentsChanged(this, index, index);
      }

      public void clearRegions() {
         this.rpList = new ReferencePointList();
         fireIntervalAdded(this, 0, 0);
      }
   }
    
   private void updateStageRelation() {
      //updates slope and offset parameters for the two stages
      if (rpm_.getSize() > 1) { // need at least two points for linear fit
         // Least Squares regression
         SimpleRegression sr = new SimpleRegression();
         int np = rpm_.getSize();
         for (int n = 0; n < np; n++) {
            ReferencePoint rp = rpm_.getReferencePoint(n);
            sr.addData(rp.stage1Position, rp.stage2Position);
         }
         try {
            studio_.core().setProperty(multiStageName_, "Scaling-2", sr.getSlope());
            studio_.core().setProperty(multiStageName_, "TranslationUm-2", sr.getIntercept());
         } catch (Exception ex) {
            studio_.logs().logError(ex,
                  "LightSheetControl: Error when setting scaling and translation");
         }
      }
   }


   /**
    * This method is called from within the constructor to initialize the form.
    */

   private void initComponents() {
      jFrame1 = new JFrame();
      jScrollPane1 = new JScrollPane();
      rpList = new JList<ReferencePoint>();
      goToPosButton = new JButton();
      delAllButton = new JButton();
      delRPButton = new JButton();
      updRPButton = new JButton();
      addRPButton = new JButton();
      jLabel1 = new JLabel();

      GroupLayout jFrame1Layout = new GroupLayout(jFrame1.getContentPane());
      jFrame1.getContentPane().setLayout(jFrame1Layout);
      jFrame1Layout.setHorizontalGroup(
            jFrame1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
      );
      jFrame1Layout.setVerticalGroup(
             jFrame1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
             .addGap(0, 300, Short.MAX_VALUE)
      );

      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

      rpList.setModel(rpm_);
      jScrollPane1.setViewportView(rpList);
      rpList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

      goToPosButton.setText("Go to Position");
      goToPosButton.addActionListener(evt -> goToPosButtonActionPerformed(evt));

      delAllButton.setText("Delete All");
      delAllButton.addActionListener(evt -> delAllButtonActionPerformed(evt));

      delRPButton.setText("Delete Reference Point");
      delRPButton.addActionListener(evt -> delRPButtonActionPerformed(evt));

      updRPButton.setText("Update Reference Point");
      updRPButton.addActionListener(evt -> updRPButtonActionPerformed(evt));

      addRPButton.setText("Add Reference Point");
      addRPButton.addActionListener(evt -> addRPButtonActionPerformed(evt));

      jLabel1.setText("Reference Points (1st Stage / 2nd Stage)");

      GroupLayout layout = new GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
            .addContainerGap(23, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(goToPosButton)
                .addComponent(updRPButton)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                          .addComponent(addRPButton)
                          .addGap(12, 12, 12))
                      .addComponent(delRPButton, GroupLayout.Alignment.TRAILING))
                  .addComponent(delAllButton))
              .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
              .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addComponent(jLabel1)
                  .addComponent(jScrollPane1, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
              .addContainerGap())
      );
      layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addGap(4, 4, 4)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, GroupLayout.PREFERRED_SIZE,
                          227, GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addRPButton)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(delRPButton)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(updRPButton)
                        .addGap(7, 7, 7)
                        .addComponent(delAllButton)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(goToPosButton)))
                .addContainerGap(25, Short.MAX_VALUE))
      );

      pack();
   }

   private void addRPButtonActionPerformed(java.awt.event.ActionEvent evt) {
      ReferencePoint rp = null;
      try {
         rp = new ReferencePoint(studio_.core().getPosition(stage1_),
               studio_.core().getPosition(stage2_));
      } catch (Exception ex) {
         studio_.logs().logError(ex,
               "Error when requesting stage positionsStage 1 / 2 = " + stage1_ + stage2_);
      }
      rpm_.addPoint(rp);
      updateStageRelation();
   }

   private void delRPButtonActionPerformed(ActionEvent evt) {
      rpm_.deletePoint(rpList.getSelectedIndex());
      updateStageRelation();
   }

   private void updRPButtonActionPerformed(java.awt.event.ActionEvent evt) {
      ReferencePoint rp = null;
      try {
         rp = new ReferencePoint(studio_.core().getPosition(stage1_),
               studio_.core().getPosition(stage2_));
      } catch (Exception ex) {
         studio_.logs().logError(ex,
               "Error when requesting stage positions. Stage 1 / 2 = " + stage1_ + stage2_);
      }
      rpm_.updatePoint(rpList.getSelectedIndex(), rp);
      updateStageRelation();
   }

   private void delAllButtonActionPerformed(java.awt.event.ActionEvent evt) {
      rpm_.clearRegions();
   }

   private void goToPosButtonActionPerformed(java.awt.event.ActionEvent evt) {
      ReferencePoint rp = rpm_.getReferencePoint(rpList.getSelectedIndex());
      try {
         studio_.core().setPosition(stage1_, rp.stage1Position);
         studio_.core().setPosition(stage2_, rp.stage2Position);
      } catch (Exception ex) {
         studio_.logs().logError(ex, "Error when setting stage positions");
      }
   }

   // Variables declaration - do not modify//GEN-BEGIN:variables
   // End of variables declaration//GEN-END:variables
}

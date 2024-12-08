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
import org.micromanager.internal.utils.WindowPositioning;


public class LightSheetControlFrame extends JFrame {

   private final Studio studio_;
   private String stage1_ = "";
   private String stage2_ = "";
   private String multiStageName_ = "";
   private final RPModel rpm_;
   private JList<ReferencePoint> rpList_;

   /**
    * Creates new form LightSheetControlForm.
    *
    * @param studio - what do we do without it?
    */
   public LightSheetControlFrame(Studio studio) {
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
      if (multiStageName_.isEmpty()) {
         studio_.logs().showError("Cannot find multi stage device. "
               + "This plugin does not work without one. <br>"
               + "Use the Hardware Configuration wizard and select Utilities > MultiStage");
         return;
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
      rpList_ = new JList<>();
      JScrollPane jScrollPane1 = new JScrollPane();
      final JButton goToPosButton = new JButton();
      final JButton delAllButton = new JButton();
      final JButton delRPButton = new JButton();
      final JButton updRPButton = new JButton();
      final JButton addRPButton = new JButton();
      final JLabel jLabel1 = new JLabel();

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      rpList_.setModel(rpm_);
      jScrollPane1.setViewportView(rpList_);
      rpList_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

      //setLayout(new MigLayout("flowy, filly, insets 8", "[grow][]", "[top]"));
      //setMinimumSize(new Dimension(275, 365));
      //super.setIconImage(Toolkit.getDefaultToolkit().getImage(
      //      getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setBounds(100, 100, 362, 595);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);

      goToPosButton.setText("Go to Position");
      goToPosButton.addActionListener(this::goToPosButtonActionPerformed);

      delAllButton.setText("Delete All");
      delAllButton.addActionListener(this::delAllButtonActionPerformed);

      delRPButton.setText("Delete Reference Point");
      delRPButton.addActionListener(this::delRPButtonActionPerformed);

      updRPButton.setText("Update Reference Point");
      updRPButton.addActionListener(this::updRPButtonActionPerformed);

      addRPButton.setText("Add Reference Point");
      addRPButton.addActionListener(this::addRPButtonActionPerformed);

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
      rpm_.deletePoint(rpList_.getSelectedIndex());
      updateStageRelation();
   }

   private void updRPButtonActionPerformed(ActionEvent evt) {
      ReferencePoint rp = null;
      try {
         rp = new ReferencePoint(studio_.core().getPosition(stage1_),
               studio_.core().getPosition(stage2_));
      } catch (Exception ex) {
         studio_.logs().logError(ex,
               "Error when requesting stage positions. Stage 1 / 2 = " + stage1_ + stage2_);
      }
      rpm_.updatePoint(rpList_.getSelectedIndex(), rp);
      updateStageRelation();
   }

   private void delAllButtonActionPerformed(ActionEvent evt) {
      rpm_.clearRegions();
   }

   private void goToPosButtonActionPerformed(ActionEvent evt) {
      ReferencePoint rp = rpm_.getReferencePoint(rpList_.getSelectedIndex());
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

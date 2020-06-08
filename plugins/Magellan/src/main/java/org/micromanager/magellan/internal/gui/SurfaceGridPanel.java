/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.gui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.micromanager.magellan.internal.coordinates.NoPositionsDefinedYetException;
import org.micromanager.magellan.internal.gui.DisplayWindowSurfaceGridTableModel;
import org.micromanager.magellan.internal.magellanacq.MagellanDataManager;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridListener;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;
import org.micromanager.ndviewer.api.ControlsPanelInterface;

/**
 *
 * @author henrypinkard
 */
public class SurfaceGridPanel extends javax.swing.JPanel implements
        SurfaceGridListener, ControlsPanelInterface {

   private MagellanViewer display_;
   private ListSelectionListener surfaceTableListSelectionListener_;
   private volatile int selectedSurfaceGridIndex_ = -1;
   private MagellanDataManager manager_;
//   MagellanChannelSpec channels_;

   /**
    * Creates new form SurfaceGridPanel
    */
   public SurfaceGridPanel(MagellanDataManager manager, MagellanViewer disp) {
      manager_ = manager;
      display_ = disp;
      initComponents();
      showStagePositionsCheckBox_.setSelected(false);

      //exactly one surface or grid selected at all times
      surfaceGridTable_.setSelectionModel(new ExactlyOneRowSelectionModel());
      surfaceTableListSelectionListener_ = new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
               return;
               //action occurs second time this method is called, after the table gains focus
            }
            updateSurfaceGridSelection();
         }
      };
      surfaceGridTable_.getSelectionModel().addListSelectionListener(surfaceTableListSelectionListener_);
      //Table column widths
      surfaceGridTable_.getColumnModel().getColumn(0).setMaxWidth(40); //show column
      surfaceGridTable_.getColumnModel().getColumn(1).setMaxWidth(120); //type column
      //So it is initialized correctly when surfaces are already present
      updateSurfaceGridSelection();

      //knitially disable surfaces and grids
      for (Component j : this.getComponents()) {
         j.setEnabled(false);
      }
   }

   public void enable() {
      for (Component j : this.getComponents()) {
         j.setEnabled(true);
      }
   }

   @Override
   public void selected() {
      manager_.setSurfaceGridMode(true);
      manager_.update();
   }

   @Override
   public void deselected() {
      manager_.setSurfaceGridMode(false);
      manager_.update();
   }

   @Override
   public String getTitle() {
      return "Grids and Surfaces";
   }

   @Override
   public void close() {
      surfaceGridTable_.getSelectionModel().removeListSelectionListener(surfaceTableListSelectionListener_);

   }

   @Override
   public void SurfaceOrGridChanged(XYFootprint f) {

   }

   @Override
   public void SurfaceOrGridDeleted(XYFootprint f) {
      updateSurfaceGridSelection();
   }

   @Override
   public void SurfaceOrGridCreated(XYFootprint f) {
      updateSurfaceGridSelection();
   }

   @Override
   public void SurfaceOrGridRenamed(XYFootprint f) {

   }

   @Override
   public void SurfaceInterpolationUpdated(SurfaceInterpolator s) {

   }

   private void updateSurfaceGridSelection() {
      selectedSurfaceGridIndex_ = surfaceGridTable_.getSelectedRow();
      //if last in list is removed, update the selected index
      if (selectedSurfaceGridIndex_ == surfaceGridTable_.getModel().getRowCount()) {
         surfaceGridTable_.getSelectionModel().setSelectionInterval(selectedSurfaceGridIndex_ - 1, selectedSurfaceGridIndex_ - 1);
      }
      XYFootprint current = getCurrentSurfaceOrGrid();
      if (current != null) {

         CardLayout card1 = (CardLayout) surfaceGridSpecificControlsPanel_1.getLayout();
         if (current instanceof SurfaceInterpolator) {
            card1.show(surfaceGridSpecificControlsPanel_1, "surface");
         } else {
            card1.show(surfaceGridSpecificControlsPanel_1, "grid");
            int numRows = ((MultiPosGrid) current).numRows();
            int numCols = ((MultiPosGrid) current).numCols();
            gridRowsSpinner_1.setValue(numRows);
            gridColsSpinner_1.setValue(numCols);
         }
      }
      display_.redrawOverlay();
   }
   
   public XYFootprint getCurrentSurfaceOrGrid() {
      if (selectedSurfaceGridIndex_ == -1) {
         return null;
      }
      return SurfaceGridManager.getInstance().getSurfaceOrGrid(selectedSurfaceGridIndex_);
   
   }

   public ArrayList<XYFootprint> getSurfacesAndGridsForDisplay() {
      ArrayList<XYFootprint> list = new ArrayList<XYFootprint>();
      for (int i = 0; i < SurfaceGridManager.getInstance().getNumberOfGrids() + SurfaceGridManager.getInstance().getNumberOfSurfaces(); i++) {
         try {
            if (((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel()).isSurfaceOrGridVisible(i)) {
               list.add(SurfaceGridManager.getInstance().getSurfaceOrGrid(i));
            }
         } catch (NullPointerException e) {
            //this comes up when making a bunch of surfaces then making a grid, unclear why vut it seems to be debnign
         }
      }
      return list;
   }

   public boolean isCurrentlyEditableSurfaceGridVisible() {
      if (selectedSurfaceGridIndex_ == -1) {
         return false;
      }
      return (Boolean) surfaceGridTable_.getValueAt(selectedSurfaceGridIndex_, 0);
   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      newGridButton_ = new javax.swing.JButton();
      newSurfaceButton_ = new javax.swing.JButton();
      jScrollPane1 = new javax.swing.JScrollPane();
      surfaceGridTable_ = new javax.swing.JTable();
      surfaceGridSpecificControlsPanel_1 = new javax.swing.JPanel();
      gridControlPanel_1 = new javax.swing.JPanel();
      gridRowsLabel_1 = new javax.swing.JLabel();
      gridRowsSpinner_1 = new javax.swing.JSpinner();
      gridColsLabel_1 = new javax.swing.JLabel();
      gridColsSpinner_1 = new javax.swing.JSpinner();
      jLabel2 = new javax.swing.JLabel();
      surfaceControlPanel_ = new javax.swing.JPanel();
      showStagePositionsCheckBox_ = new javax.swing.JCheckBox();
      showInterpCheckBox_ = new javax.swing.JCheckBox();
      jLabel3 = new javax.swing.JLabel();

      newGridButton_.setText("New Grid");
      newGridButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            newGridButton_ActionPerformed(evt);
         }
      });

      newSurfaceButton_.setText("New Surface");
      newSurfaceButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            newSurfaceButton_ActionPerformed(evt);
         }
      });

      surfaceGridTable_.setModel(new DisplayWindowSurfaceGridTableModel(display_));
      jScrollPane1.setViewportView(surfaceGridTable_);

      surfaceGridSpecificControlsPanel_1.setLayout(new java.awt.CardLayout());

      gridRowsLabel_1.setText("Rows:");

      gridRowsSpinner_1.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
      gridRowsSpinner_1.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            gridRowsSpinner_1StateChanged(evt);
         }
      });

      gridColsLabel_1.setText("Columns:");

      gridColsSpinner_1.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
      gridColsSpinner_1.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            gridColsSpinner_1StateChanged(evt);
         }
      });

      jLabel2.setText("Current Grid: ");

      javax.swing.GroupLayout gridControlPanel_1Layout = new javax.swing.GroupLayout(gridControlPanel_1);
      gridControlPanel_1.setLayout(gridControlPanel_1Layout);
      gridControlPanel_1Layout.setHorizontalGroup(
         gridControlPanel_1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, gridControlPanel_1Layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(jLabel2)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(gridRowsLabel_1)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(gridRowsSpinner_1, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(gridColsLabel_1)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(gridColsSpinner_1, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(69, Short.MAX_VALUE))
      );
      gridControlPanel_1Layout.setVerticalGroup(
         gridControlPanel_1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(gridControlPanel_1Layout.createSequentialGroup()
            .addGroup(gridControlPanel_1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(gridRowsLabel_1)
               .addComponent(gridRowsSpinner_1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(gridColsLabel_1)
               .addComponent(gridColsSpinner_1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(jLabel2))
            .addGap(0, 9, Short.MAX_VALUE))
      );

      surfaceGridSpecificControlsPanel_1.add(gridControlPanel_1, "grid");

      showStagePositionsCheckBox_.setSelected(true);
      showStagePositionsCheckBox_.setText("XY Footprint postions");
      showStagePositionsCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showStagePositionsCheckBox_ActionPerformed(evt);
         }
      });

      showInterpCheckBox_.setSelected(true);
      showInterpCheckBox_.setText("Interpolation");
      showInterpCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showInterpCheckBox_ActionPerformed(evt);
         }
      });

      jLabel3.setText("Show:");

      javax.swing.GroupLayout surfaceControlPanel_Layout = new javax.swing.GroupLayout(surfaceControlPanel_);
      surfaceControlPanel_.setLayout(surfaceControlPanel_Layout);
      surfaceControlPanel_Layout.setHorizontalGroup(
         surfaceControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(surfaceControlPanel_Layout.createSequentialGroup()
            .addGap(3, 3, 3)
            .addComponent(jLabel3)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(showInterpCheckBox_)
            .addGap(12, 12, 12)
            .addComponent(showStagePositionsCheckBox_)
            .addContainerGap(67, Short.MAX_VALUE))
      );
      surfaceControlPanel_Layout.setVerticalGroup(
         surfaceControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(surfaceControlPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(surfaceControlPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(jLabel3)
               .addComponent(showInterpCheckBox_)
               .addComponent(showStagePositionsCheckBox_))
            .addContainerGap(9, Short.MAX_VALUE))
      );

      surfaceGridSpecificControlsPanel_1.add(surfaceControlPanel_, "surface");

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
      this.setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(newGridButton_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(newSurfaceButton_)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
         .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 668, Short.MAX_VALUE)
         .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
               .addContainerGap(252, Short.MAX_VALUE)
               .addComponent(surfaceGridSpecificControlsPanel_1, javax.swing.GroupLayout.PREFERRED_SIZE, 397, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addGap(19, 19, 19)))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(newGridButton_)
               .addComponent(newSurfaceButton_))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE))
         .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
               .addContainerGap()
               .addComponent(surfaceGridSpecificControlsPanel_1, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addContainerGap(259, Short.MAX_VALUE)))
      );
   }// </editor-fold>//GEN-END:initComponents

   private void newGridButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newGridButton_ActionPerformed
      try {
         Point2D.Double coord = manager_.getStageCoordinateOfViewCenter();
         MultiPosGrid r = ((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel()).newGrid(
                 (Integer) gridRowsSpinner_1.getValue(), (Integer) gridColsSpinner_1.getValue(), coord);
         selectedSurfaceGridIndex_ = SurfaceGridManager.getInstance().getIndex(r);
         surfaceGridTable_.getSelectionModel().setSelectionInterval(selectedSurfaceGridIndex_, selectedSurfaceGridIndex_);
      } catch (NoPositionsDefinedYetException e) {
         JOptionPane.showMessageDialog(this, "Explore a tile first before adding a position");
         return;
      }
   }//GEN-LAST:event_newGridButton_ActionPerformed

   private void newSurfaceButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newSurfaceButton_ActionPerformed
      SurfaceInterpolator s = ((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel()).addNewSurface();
      selectedSurfaceGridIndex_ = SurfaceGridManager.getInstance().getIndex(s);
      surfaceGridTable_.getSelectionModel().setSelectionInterval(selectedSurfaceGridIndex_, selectedSurfaceGridIndex_);
   }//GEN-LAST:event_newSurfaceButton_ActionPerformed

   private void gridRowsSpinner_1StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_gridRowsSpinner_1StateChanged
      if (getCurrentSurfaceOrGrid() != null && getCurrentSurfaceOrGrid() instanceof MultiPosGrid) {
         ((MultiPosGrid) getCurrentSurfaceOrGrid()).updateParams((Integer) gridRowsSpinner_1.getValue(), (Integer) gridColsSpinner_1.getValue());
      }
      display_.redrawOverlay();
   }//GEN-LAST:event_gridRowsSpinner_1StateChanged

   private void gridColsSpinner_1StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_gridColsSpinner_1StateChanged
      if (getCurrentSurfaceOrGrid() != null && getCurrentSurfaceOrGrid() instanceof MultiPosGrid) {
         ((MultiPosGrid) getCurrentSurfaceOrGrid()).updateParams((Integer) gridRowsSpinner_1.getValue(), (Integer) gridColsSpinner_1.getValue());
      }
      display_.redrawOverlay();
   }//GEN-LAST:event_gridColsSpinner_1StateChanged

   private void showStagePositionsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showStagePositionsCheckBox_ActionPerformed
      manager_.setSurfaceDisplaySettings(showInterpCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected());
      display_.redrawOverlay();
   }//GEN-LAST:event_showStagePositionsCheckBox_ActionPerformed

   private void showInterpCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showInterpCheckBox_ActionPerformed
      manager_.setSurfaceDisplaySettings(showInterpCheckBox_.isSelected(), showStagePositionsCheckBox_.isSelected());
      display_.redrawOverlay();
   }//GEN-LAST:event_showInterpCheckBox_ActionPerformed


   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JLabel gridColsLabel_1;
   private javax.swing.JSpinner gridColsSpinner_1;
   private javax.swing.JPanel gridControlPanel_1;
   private javax.swing.JLabel gridRowsLabel_1;
   private javax.swing.JSpinner gridRowsSpinner_1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JScrollPane jScrollPane1;
   private javax.swing.JButton newGridButton_;
   private javax.swing.JButton newSurfaceButton_;
   private javax.swing.JCheckBox showInterpCheckBox_;
   private javax.swing.JCheckBox showStagePositionsCheckBox_;
   private javax.swing.JPanel surfaceControlPanel_;
   private javax.swing.JPanel surfaceGridSpecificControlsPanel_1;
   private javax.swing.JTable surfaceGridTable_;
   // End of variables declaration//GEN-END:variables

}

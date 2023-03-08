
package org.micromanager.magellan.internal.gui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.micromanager.magellan.internal.coordinates.NoPositionsDefinedYetException;
import org.micromanager.magellan.internal.magellanacq.MagellanAcqUIAndStorage;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridListener;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;
import org.micromanager.ndviewer.api.ControlsPanelInterface;
import org.micromanager.ndviewer.main.NDViewer;

/**
 *
 * @author henrypinkard
 */
public class SurfaceGridPanel extends javax.swing.JPanel implements
        SurfaceGridListener, ControlsPanelInterface {

   private NDViewer display_;
   private ListSelectionListener surfaceTableListSelectionListener_;
   private volatile int selectedSurfaceGridIndex_ = -1;
   private MagellanAcqUIAndStorage manager_;
   private boolean active_ = true;

   /**
    * Creates new form SurfaceGridPanel.
    */
   public SurfaceGridPanel(MagellanAcqUIAndStorage manager, NDViewer disp) {
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
      surfaceGridTable_.getSelectionModel()
            .addListSelectionListener(surfaceTableListSelectionListener_);
      //Table column widths
      surfaceGridTable_.getColumnModel().getColumn(0).setMaxWidth(40); //show column
      surfaceGridTable_.getColumnModel().getColumn(1).setMaxWidth(120); //type column
      //So it is initialized correctly when surfaces are already present
      updateSurfaceGridSelection();

      //knitially disable surfaces and grids
//      for (Component j : this.getComponents()) {
//         j.setEnabled(false);
//      }
   }

   public boolean isActive() {
      return active_;
   }

   public void enable() {
      for (Component j : this.getComponents()) {
         j.setEnabled(true);
      }
   }

   @Override
   public void selected() {
      active_ = true;
      manager_.update();
   }

   @Override
   public void deselected() {
      active_ = false;
      manager_.update();
   }

   @Override
   public String getTitle() {
      return "Grids and Surfaces";
   }

   @Override
   public void close() {
      surfaceGridTable_.getSelectionModel()
            .removeListSelectionListener(surfaceTableListSelectionListener_);
   }

   @Override
   public void surfaceOrGridChanged(XYFootprint f) {

   }

   @Override
   public void surfaceOrGridDeleted(XYFootprint f) {
      updateSurfaceGridSelection();
   }

   @Override
   public void surfaceOrGridCreated(XYFootprint f) {
      updateSurfaceGridSelection();
   }

   @Override
   public void surfaceOrGridRenamed(XYFootprint f) {

   }

   @Override
   public void surfaceInterpolationUpdated(SurfaceInterpolator s) {

   }

   private void updateSurfaceGridSelection() {
      selectedSurfaceGridIndex_ = surfaceGridTable_.getSelectedRow();
      //if last in list is removed, update the selected index
      if (selectedSurfaceGridIndex_ == surfaceGridTable_.getModel().getRowCount()) {
         surfaceGridTable_.getSelectionModel().setSelectionInterval(
               selectedSurfaceGridIndex_ - 1, selectedSurfaceGridIndex_ - 1);
      }
      XYFootprint current = getCurrentSurfaceOrGrid();
      if (current != null) {

         CardLayout card1 = (CardLayout) surfaceGridSpecificControlsPanel1.getLayout();
         if (current instanceof SurfaceInterpolator) {
            card1.show(surfaceGridSpecificControlsPanel1, "surface");
         } else {
            card1.show(surfaceGridSpecificControlsPanel1, "grid");
            int numRows = ((MultiPosGrid) current).numRows();
            int numCols = ((MultiPosGrid) current).numCols();
            gridRowsSpinner1.setValue(numRows);
            gridColsSpinner1.setValue(numCols);
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
      for (int i = 0; i < SurfaceGridManager.getInstance().getNumberOfGrids()
            + SurfaceGridManager.getInstance().getNumberOfSurfaces(); i++) {
         try {
            if (((DisplayWindowSurfaceGridTableModel) surfaceGridTable_.getModel())
                  .isSurfaceOrGridVisible(i)) {
               list.add(SurfaceGridManager.getInstance().getSurfaceOrGrid(i));
            }
         } catch (NullPointerException e) {
            //this comes up when making a bunch of surfaces then making a grid,
            // unclear why vut it seems to be debnign
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
      surfaceGridSpecificControlsPanel1 = new javax.swing.JPanel();
      gridControlPanel1 = new javax.swing.JPanel();
      gridRowsLabel1 = new javax.swing.JLabel();
      gridRowsSpinner1 = new javax.swing.JSpinner();
      gridColsLabel1 = new javax.swing.JLabel();
      gridColsSpinner1 = new javax.swing.JSpinner();
      jLabel2 = new javax.swing.JLabel();
      surfaceControlPanel_ = new javax.swing.JPanel();
      showStagePositionsCheckBox_ = new javax.swing.JCheckBox();
      showInterpCheckBox_ = new javax.swing.JCheckBox();
      jLabel3 = new javax.swing.JLabel();

      newGridButton_.setText("New Grid");
      newGridButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            newGridButtonActionPerformed(evt);
         }
      });

      newSurfaceButton_.setText("New Surface");
      newSurfaceButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            newSurfaceButtonActionPerformed(evt);
         }
      });

      surfaceGridTable_.setModel(new DisplayWindowSurfaceGridTableModel(display_));
      jScrollPane1.setViewportView(surfaceGridTable_);

      surfaceGridSpecificControlsPanel1.setLayout(new java.awt.CardLayout());

      gridRowsLabel1.setText("Rows:");

      gridRowsSpinner1.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
      gridRowsSpinner1.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            gridRowsSpinner1StateChanged(evt);
         }
      });

      gridColsLabel1.setText("Columns:");

      gridColsSpinner1.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
      gridColsSpinner1.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            gridColsSpinner1StateChanged(evt);
         }
      });

      jLabel2.setText("Current Grid: ");

      javax.swing.GroupLayout gridControlPanel1Layout =
            new javax.swing.GroupLayout(gridControlPanel1);
      gridControlPanel1.setLayout(gridControlPanel1Layout);
      gridControlPanel1Layout.setHorizontalGroup(
            gridControlPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
                  gridControlPanel1Layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(jLabel2)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(gridRowsLabel1)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(gridRowsSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE, 56,
                  javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(gridColsLabel1)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(gridColsSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE, 54,
                  javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(69, Short.MAX_VALUE))
      );
      gridControlPanel1Layout.setVerticalGroup(
            gridControlPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(gridControlPanel1Layout.createSequentialGroup()
            .addGroup(gridControlPanel1Layout.createParallelGroup(
                  javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(gridRowsLabel1)
               .addComponent(gridRowsSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE,
                     javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(gridColsLabel1)
               .addComponent(gridColsSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE,
                     javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(jLabel2))
            .addGap(0, 9, Short.MAX_VALUE))
      );

      surfaceGridSpecificControlsPanel1.add(gridControlPanel1, "grid");

      showStagePositionsCheckBox_.setSelected(true);
      showStagePositionsCheckBox_.setText("XY Footprint postions");
      showStagePositionsCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showStagePositionsCheckBoxActionPerformed(evt);
         }
      });

      showInterpCheckBox_.setSelected(true);
      showInterpCheckBox_.setText("Interpolation");
      showInterpCheckBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showInterpCheckBoxActionPerformed(evt);
         }
      });

      jLabel3.setText("Show:");

      javax.swing.GroupLayout surfaceControlPanelLayout =
            new javax.swing.GroupLayout(surfaceControlPanel_);
      surfaceControlPanel_.setLayout(surfaceControlPanelLayout);
      surfaceControlPanelLayout.setHorizontalGroup(
            surfaceControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(surfaceControlPanelLayout.createSequentialGroup()
            .addGap(3, 3, 3)
            .addComponent(jLabel3)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(showInterpCheckBox_)
            .addGap(12, 12, 12)
            .addComponent(showStagePositionsCheckBox_)
            .addContainerGap(67, Short.MAX_VALUE))
      );
      surfaceControlPanelLayout.setVerticalGroup(
            surfaceControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(surfaceControlPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addGroup(surfaceControlPanelLayout.createParallelGroup(
                  javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(jLabel3)
               .addComponent(showInterpCheckBox_)
               .addComponent(showStagePositionsCheckBox_))
            .addContainerGap(9, Short.MAX_VALUE))
      );

      surfaceGridSpecificControlsPanel1.add(surfaceControlPanel_, "surface");

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
               .addComponent(surfaceGridSpecificControlsPanel1,
                     javax.swing.GroupLayout.PREFERRED_SIZE, 397,
                     javax.swing.GroupLayout.PREFERRED_SIZE)
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
               .addComponent(surfaceGridSpecificControlsPanel1,
                     javax.swing.GroupLayout.PREFERRED_SIZE, 35,
                     javax.swing.GroupLayout.PREFERRED_SIZE)
               .addContainerGap(259, Short.MAX_VALUE)))
      );
   }

   private void newGridButtonActionPerformed(java.awt.event.ActionEvent evt) {
      try {
         Point2D.Double coord = manager_.getStageCoordinateOfViewCenter();
         MultiPosGrid r = ((DisplayWindowSurfaceGridTableModel)
               surfaceGridTable_.getModel()).newGrid(
                 (Integer) gridRowsSpinner1.getValue(),
               (Integer) gridColsSpinner1.getValue(), coord);
         selectedSurfaceGridIndex_ = SurfaceGridManager.getInstance().getIndex(r);
         surfaceGridTable_.getSelectionModel().setSelectionInterval(
               selectedSurfaceGridIndex_, selectedSurfaceGridIndex_);
      } catch (NoPositionsDefinedYetException e) {
         JOptionPane.showMessageDialog(this,
               "Explore a tile first before adding a position");
         return;
      }
   } //GEN-LAST:event_newGridButton_ActionPerformed

   private void newSurfaceButtonActionPerformed(java.awt.event.ActionEvent evt) {
      SurfaceInterpolator s = ((DisplayWindowSurfaceGridTableModel) surfaceGridTable_
            .getModel()).addNewSurface();
      selectedSurfaceGridIndex_ = SurfaceGridManager.getInstance().getIndex(s);
      surfaceGridTable_.getSelectionModel().setSelectionInterval(selectedSurfaceGridIndex_,
            selectedSurfaceGridIndex_);
   }

   private void gridRowsSpinner1StateChanged(javax.swing.event.ChangeEvent evt) {
      if (getCurrentSurfaceOrGrid() != null && getCurrentSurfaceOrGrid() instanceof MultiPosGrid) {
         ((MultiPosGrid) getCurrentSurfaceOrGrid()).updateParams((Integer) gridRowsSpinner1
               .getValue(), (Integer) gridColsSpinner1.getValue());
      }
      display_.redrawOverlay();
   }

   private void gridColsSpinner1StateChanged(javax.swing.event.ChangeEvent evt) {
      if (getCurrentSurfaceOrGrid() != null && getCurrentSurfaceOrGrid() instanceof MultiPosGrid) {
         ((MultiPosGrid) getCurrentSurfaceOrGrid()).updateParams((Integer) gridRowsSpinner1
               .getValue(), (Integer) gridColsSpinner1.getValue());
      }
      display_.redrawOverlay();
   }

   private void showStagePositionsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {
      manager_.setSurfaceDisplaySettings(showInterpCheckBox_.isSelected(),
            showStagePositionsCheckBox_.isSelected());
      display_.redrawOverlay();
   }

   private void showInterpCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {
      manager_.setSurfaceDisplaySettings(showInterpCheckBox_.isSelected(),
            showStagePositionsCheckBox_.isSelected());
      display_.redrawOverlay();
   }


   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JLabel gridColsLabel1;
   private javax.swing.JSpinner gridColsSpinner1;
   private javax.swing.JPanel gridControlPanel1;
   private javax.swing.JLabel gridRowsLabel1;
   private javax.swing.JSpinner gridRowsSpinner1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JScrollPane jScrollPane1;
   private javax.swing.JButton newGridButton_;
   private javax.swing.JButton newSurfaceButton_;
   private javax.swing.JCheckBox showInterpCheckBox_;
   private javax.swing.JCheckBox showStagePositionsCheckBox_;
   private javax.swing.JPanel surfaceControlPanel_;
   private javax.swing.JPanel surfaceGridSpecificControlsPanel1;
   private javax.swing.JTable surfaceGridTable_;
   // End of variables declaration//GEN-END:variables

}

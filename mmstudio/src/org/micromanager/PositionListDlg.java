///////////////////////////////////////////////////////////////////////////////
//FILE:          XYPositionListDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 8, 2007
//
// COPYRIGHT:    University of California, San Francisco, 2007
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
// CVS:          $Id$
//
package org.micromanager;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.table.AbstractTableModel;

import mmcorej.CMMCore;
import mmcorej.DeviceType;

import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.MMDialog;

import com.swtdesigner.SwingResourceManager;

public class PositionListDlg extends MMDialog {

   class PosTableModel extends AbstractTableModel {
      public final String[] COLUMN_NAMES = new String[] {
            "Label",
            "Position [um]"
      };
      private PositionList posList_;
      
      public void setData(PositionList pl) {
         posList_ = pl;
      }
      
      public int getRowCount() {
         return posList_.getNumberOfPositions();
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         // TODO: broken!!!
         if (rowIndex == 0) {
            MultiStagePosition msp = posList_.getPosition(rowIndex);
            return msp.getLabel();
         } else if (rowIndex == 1) {
            MultiStagePosition msp = posList_.getPosition(rowIndex);
            StringBuffer sb = new StringBuffer();
            for (int i=0; i<msp.size(); i++) {
               StagePosition sp = msp.get(i);
               if (i!=0)
                  sb.append(";");
               sb.append(sp.getVerbose());
            }
            return null; //new String(pos.x + "," + pos.y);
         } else
            return null;
      }
   }

   private JTable posTable_;
   private SpringLayout springLayout;
   private CMMCore core_;
   private JLabel yLabel_;
   private JLabel xLabel_;

   /**
    * Create the dialog
    */
   public PositionListDlg(CMMCore core, PositionList posList) {
      super();
      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent arg0) {
            savePosition();
         }
      });
      core_ = core;
      setTitle("XY-position List");
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setBounds(100, 100, 397, 455);

      Preferences root = Preferences.userNodeForPackage(this.getClass());
      setPrefsNode(root.node(root.absolutePath() + "/XYPositionListDlg"));
      
      Rectangle r = getBounds();
      loadPosition(r.x, r.y, r.width, r.height);
      
      final JScrollPane scrollPane = new JScrollPane();
      getContentPane().add(scrollPane);
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane, -16, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane, 15, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, scrollPane, -124, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane, 10, SpringLayout.WEST, getContentPane());

      posTable_ = new JTable();
      PosTableModel model = new PosTableModel();
      model.setData(posList);
      posTable_.setModel(model);
      posTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scrollPane.setViewportView(posTable_);

      final JButton markButton = new JButton();
      markButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            markPosition();
         }
      });
      markButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/flag_green.png"));
      markButton.setText("Mark");
      getContentPane().add(markButton);
      springLayout.putConstraint(SpringLayout.SOUTH, markButton, 40, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, markButton, 17, SpringLayout.NORTH, getContentPane());

      final JButton removeButton = new JButton();
      removeButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/cross.png"));
      removeButton.setText("Remove");
      getContentPane().add(removeButton);
      springLayout.putConstraint(SpringLayout.EAST, markButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, markButton, 0, SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, removeButton, 65, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, removeButton, 42, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, removeButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, removeButton, -109, SpringLayout.EAST, getContentPane());

      final JButton closeButton = new JButton();
      closeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            savePosition();
            dispose();
         }
      });
      closeButton.setText("Close");
      getContentPane().add(closeButton);
      springLayout.putConstraint(SpringLayout.EAST, closeButton, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, closeButton, 0, SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, closeButton, 395, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, closeButton, 372, SpringLayout.NORTH, getContentPane());

      final JButton gotoButton = new JButton();
      gotoButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/resultset_next.png"));
      gotoButton.setText("Go to");
      getContentPane().add(gotoButton);
      springLayout.putConstraint(SpringLayout.SOUTH, gotoButton, 140, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, gotoButton, 117, SpringLayout.NORTH, getContentPane());

      final JButton refreshButton = new JButton();
      refreshButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            refreshCurrentPosition();
         }
      });
      refreshButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/arrow_refresh.png"));
      refreshButton.setText("Refresh");
      getContentPane().add(refreshButton);
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 165, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, 142, SpringLayout.NORTH, getContentPane());

      final JButton removeAllButton = new JButton();
      removeAllButton.setText("Remove all");
      getContentPane().add(removeAllButton);
      springLayout.putConstraint(SpringLayout.SOUTH, removeAllButton, 90, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, removeAllButton, 0, SpringLayout.SOUTH, removeButton);
      springLayout.putConstraint(SpringLayout.EAST, removeAllButton, 100, SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.WEST, removeAllButton, 0, SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, 0, SpringLayout.EAST, removeAllButton);
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, -100, SpringLayout.EAST, removeAllButton);
      springLayout.putConstraint(SpringLayout.EAST, gotoButton, 0, SpringLayout.EAST, removeAllButton);
      springLayout.putConstraint(SpringLayout.WEST, gotoButton, -100, SpringLayout.EAST, removeAllButton);

      xLabel_ = new JLabel();
      xLabel_.setText("X=");
      getContentPane().add(xLabel_);
      springLayout.putConstraint(SpringLayout.EAST, xLabel_, 0, SpringLayout.EAST, refreshButton);
      springLayout.putConstraint(SpringLayout.WEST, xLabel_, 0, SpringLayout.WEST, refreshButton);
      springLayout.putConstraint(SpringLayout.SOUTH, xLabel_, 195, SpringLayout.NORTH, getContentPane());

      yLabel_ = new JLabel();
      yLabel_.setText("Y=");
      getContentPane().add(yLabel_);
      springLayout.putConstraint(SpringLayout.SOUTH, yLabel_, 219, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, yLabel_, 205, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, yLabel_, 100, SpringLayout.WEST, xLabel_);
      springLayout.putConstraint(SpringLayout.WEST, yLabel_, 0, SpringLayout.WEST, xLabel_);
      //
      
      refreshCurrentPosition();
   }

   /**
    * Store current xyPosition.
    */
   private void markPosition() {
      String stage = core_.getXYStageDevice();
      if (stage.length() == 0) {
         handleError("Default XYStage device not defined.");
         return;
      }
      refreshCurrentPosition();
      
      // TODO: read all stage positions 
      
      double x[] = new double[1];
      double y[] = new double[1];
      try {
         core_.getXYPosition(stage, x, y);
         DeviceType type = core_.getDeviceType(stage);
      } catch (Exception e) {
         handleError(e.getMessage());
      }
      
      
   }

   /**
    * Update display of the current xy position.
    */
   private void refreshCurrentPosition() {
      String stage = core_.getXYStageDevice();
      if (stage.length() == 0) {
         xLabel_.setText("X=");
         yLabel_.setText("Y=");
         return;
      }
      double x[] = new double[1];
      double y[] = new double[1];
      try {
         core_.getXYPosition(stage, x, y);
      } catch (Exception e) {
         handleError(e.getMessage());
      }
      xLabel_.setText("X=" + Double.toString(x[0]) + "um");
      yLabel_.setText("Y=" + Double.toString(y[0]) + "um");
   }

   private void handleError(String txt) {
      JOptionPane.showMessageDialog(this, txt);      
   }

}

///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 8, 2007
//               Nico Stuurman, nico@cmp.ucsf.edu June 23, 2009
//
// COPYRIGHT:    University of California, San Francisco, 2007, 2008, 2009, 2014
//
// LICENSE:      This file is distributed under the BSD license.
// License text is included with the source distribution.
//
// This file is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.internal.positionlist;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.TableCellRenderer;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.XYStagePositionChangedEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.FileDialogs.FileType;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * The PositionListDlg class provides a convenient UI to generate, edit, load, and save PositionList objects.
 */
public class PositionListDlg extends MMFrame implements MouseListener, ChangeListener {
   protected static final long serialVersionUID = 1L;
   protected String posListDir_;
   protected File curFile_;
   protected static final String POS = "pos";
   protected static final String POS_COL0_WIDTH = "posCol0WIDTH";
   protected static final String AXIS_COL0_WIDTH = "axisCol0WIDTH";
   protected static final FileType POSITION_LIST_FILE =
           new FileType("POSITION_LIST_FILE","Position list file",
                        System.getProperty("user.home") + "/PositionList.pos",
                        true, POS);

   protected Font arialSmallFont_;
   protected JTable posTable_;
   protected final JTable axisTable_;
   private final AxisTableModel axisModel_;
   protected CMMCore core_;
   protected Studio studio_;
   private AxisList axisList_;
   protected final JButton tileButton_;

   protected MultiStagePosition curMsp_;
   protected JButton markButton_;
   private final PositionTableModel positionModel_;

   protected EventBus bus_;

   @Subscribe
   public void onTileUpdate(MoversChangedEvent event) {
      setTileButtonEnabled(); //This handler is executed when the axis checkboxes are changed.
   }

   private void setTileButtonEnabled() {
      int n2DStages = 0;
      for (int i = 0; i < axisList_.getNumberOfPositions(); i++) {
         AxisData ad = axisList_.get(i);
         if (ad.getUse()) {
            if (ad.getType() == AxisData.AxisType.twoD) {
               n2DStages++;
            }
         }
      }
      if (n2DStages == 1) {
         tileButton_.setEnabled(true);
      } else {
         tileButton_.setEnabled(false);
      }
   }

   /**
    * Create the dialog
    * @param studio - Studio
    * @param posList - Position list to be displayed in this dialog
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public PositionListDlg(Studio studio, PositionList posList) {
      super("position list");
      final UserProfile profile = studio.profile();

      core_ = studio.core();
      studio_ = studio;
      bus_ = new EventBus(EventBusExceptionLogger.getInstance());
      bus_.register(this);

      setTitle("Stage Position List");
      setLayout(new MigLayout("flowy, filly, insets 8", "[grow][]", 
              "[top]"));
      setMinimumSize(new Dimension(275, 365));
      super.loadAndRestorePosition(100, 100, 362, 595);

      arialSmallFont_ = new Font("Arial", Font.PLAIN, 10);

      final JScrollPane scrollPane = new JScrollPane();
      add(scrollPane, "split, spany, growy, growx, bottom 1:push");

      final JScrollPane axisPane = new JScrollPane();
      // axisPanel should always be visible
      axisPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
      add(axisPane, "growx, wrap");

      final TableCellRenderer firstRowRenderer = new FirstRowRenderer(studio_, arialSmallFont_);
      posTable_ = new DaytimeNighttime.Table() {
         private static final long serialVersionUID = -3873504142761785021L;

         @Override
         public TableCellRenderer getCellRenderer(int row, int column) {
            if (row == 0) {
               return firstRowRenderer;
            }
            return super.getCellRenderer(row, column);
         }
      };
      posTable_.setFont(arialSmallFont_);
      positionModel_ = new PositionTableModel(studio_);
      positionModel_.setData(posList);
      posTable_.setModel(positionModel_);
      scrollPane.setViewportView(posTable_);
      CellEditor cellEditor_ = new CellEditor(arialSmallFont_);
      cellEditor_.addListener();
      posTable_.setDefaultEditor(Object.class, cellEditor_);
      posTable_.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      // set column divider location
      int posCol0Width = profile.getSettings(PositionListDlg.class).getInteger(
            POS_COL0_WIDTH, 75);
      posTable_.getColumnModel().getColumn(0).setWidth(posCol0Width);
      posTable_.getColumnModel().getColumn(0).setPreferredWidth(posCol0Width);
      posTable_.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
      
      axisTable_ = new DaytimeNighttime.Table();
      axisTable_.setFont(arialSmallFont_);
      axisList_ = new AxisList(core_);
      axisModel_ = new AxisTableModel(axisList_, axisTable_, bus_);
      axisTable_.setModel(axisModel_);
      axisPane.setViewportView(axisTable_);
      // make sure that the complete axis Table will always be visible
      int tableHeight = axisList_.getNumberOfPositions() * axisTable_.getRowHeight();
      axisPane.setMaximumSize(new Dimension(32767, 30 + tableHeight));
      axisPane.setMinimumSize(new Dimension(50, 30 + tableHeight));
      // set divider location
      int axisCol0Width = profile.getSettings(PositionListDlg.class).getInteger(
            AXIS_COL0_WIDTH, 75);
      axisTable_.getColumnModel().getColumn(0).setWidth(axisCol0Width);
      axisTable_.getColumnModel().getColumn(0).setPreferredWidth(axisCol0Width);
      axisTable_.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

      
      // Create buttons on the right side of the window    
      Dimension buttonSize = new Dimension(88, 21);
      
      // mark / replace button:
      markButton_ =  posListButton(buttonSize, arialSmallFont_);
      markButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            markPosition(true);
            posTable_.clearSelection();
            updateMarkButtonText();
         }
      });
      markButton_.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/flag_green.png")));
      markButton_.setText("Mark");
      markButton_.setToolTipText("Adds point with coordinates of current stage position");
      // Separate the layout of the buttons from the layout of the panels to 
      // their left.
      add(markButton_, "split, spany, align left");
      
      posTable_.addFocusListener(
              new java.awt.event.FocusAdapter() {
                 @Override
                 public void focusLost(java.awt.event.FocusEvent evt) {
                    updateMarkButtonText();
                 }

                 @Override
                 public void focusGained(java.awt.event.FocusEvent evt) {
                    updateMarkButtonText();
                 }
              });

      // the re-ordering buttons:
      Dimension arrowSize = new Dimension(40, buttonSize.height);
      // There should be a way to do this without a secondary panel, but I
      // couldn't figure one out, so whatever.
      JPanel arrowPanel = new JPanel(new MigLayout("insets 0, fillx"));
      // move selected row up one row
      final JButton upButton = posListButton(arrowSize, arialSmallFont_);
      upButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            incrementOrderOfSelectedPosition(-1);
         }
      });
      upButton.setIcon(new ImageIcon(MMStudio.class.getResource(
                           "/org/micromanager/icons/arrow_up.png")));
      upButton.setText(""); // "Up"
      upButton.setToolTipText("Move currently selected position up list (positions higher on list are acquired earlier)");
      arrowPanel.add(upButton, "dock west");
  
      // move selected row down one row
      final JButton downButton = posListButton(arrowSize, arialSmallFont_);
      downButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            incrementOrderOfSelectedPosition(1);
         }
      });
      downButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/arrow_down.png")));
      downButton.setText(""); // "Down"
      downButton.setToolTipText("Move currently selected position down list (lower positions on list are acquired later)");
      arrowPanel.add(downButton, "dock east");
      add(arrowPanel, "growx");

      // from this point on, the top right button's positions are computed
      final JButton mergeButton = posListButton(buttonSize, arialSmallFont_);
      // We need to use addMouseListener instead of addActionListener because
      // we'll need the mouse's position for generating a popup menu. 
      mergeButton.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent event) {
            mergePositions(event);
         }
      });
      mergeButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/asterisk_orange.png")));
      mergeButton.setText("Merge");
      mergeButton.setToolTipText("Select an axis, and set the selected positions' value along that axis to the current stage position.");
      add(mergeButton);
      
      // the Go To button:
      final JButton gotoButton = posListButton(buttonSize, arialSmallFont_);
      gotoButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            goToCurrentPosition();
         }
      });
      gotoButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/resultset_next.png")));
      gotoButton.setText("Go to");
      gotoButton.setToolTipText("Moves stage to currently selected position");
      add(gotoButton);

      final JButton refreshButton = posListButton(buttonSize, arialSmallFont_);
      refreshButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            refreshCurrentPosition();
         }
      });
      refreshButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/arrow_refresh.png")));
      refreshButton.setText("Refresh");
      add(refreshButton);

      final JButton removeButton = posListButton(buttonSize, arialSmallFont_);
      removeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            removeSelectedPositions();
         }
      });
      removeButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/cross.png")));
      removeButton.setText("Remove");
      removeButton.setToolTipText("Removes currently selected position from list");
      add(removeButton);

      final JButton setOriginButton = posListButton(buttonSize, arialSmallFont_);
      setOriginButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            calibrate(); //setOrigin();
         }
      });
       
      setOriginButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/empty.png")));
      setOriginButton.setText("Set Origin");
      setOriginButton.setToolTipText("Drives X and Y stages back to their original positions and zeros their position values");
      add(setOriginButton);

      final JButton offsetButton = posListButton(buttonSize, arialSmallFont_);
      offsetButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            offsetPositions();
         }
      });
       
      offsetButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/empty.png")));
      offsetButton.setText("Add Offset");
      offsetButton.setToolTipText("Add an offset to the selected positions.");
      add(offsetButton);
      
      final JButton removeAllButton = posListButton(buttonSize, arialSmallFont_);
      removeAllButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            int ret = JOptionPane.showConfirmDialog(null, "Are you sure you want to erase\nall positions from the position list?", "Clear all positions?", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.YES_OPTION) {
               clearAllPositions();
            }
         }
      });
      removeAllButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/delete.png")));
      removeAllButton.setText("Clear All");
      removeAllButton.setToolTipText("Removes all positions from list");
      add(removeAllButton);

      final JButton loadButton = posListButton(buttonSize, arialSmallFont_);
      loadButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            loadPositionList();
         }
      });
      loadButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/empty.png")));
      loadButton.setText("Load...");
      loadButton.setToolTipText("Load position list");
      add(loadButton, "gaptop 4:push");

      final JButton saveAsButton = posListButton(buttonSize, arialSmallFont_);
      saveAsButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            savePositionListAs();
         }
      });
      saveAsButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/empty.png")));
      saveAsButton.setText("Save As...");
      saveAsButton.setToolTipText("Save position list as");
      add(saveAsButton);

      tileButton_ = posListButton(buttonSize, arialSmallFont_);
      tileButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            showCreateTileDlg();
         }
      });
      tileButton_.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/empty.png")));
      tileButton_.setText("Create Grid");
      tileButton_.setToolTipText("Open new window to create grid of equally spaced positions");
      add(tileButton_);
      setTileButtonEnabled();

      final JButton closeButton = posListButton(buttonSize, arialSmallFont_);
      closeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            dispose();
         }
      });
      closeButton.setIcon(new ImageIcon(MMStudio.class.getResource(
              "/org/micromanager/icons/empty.png")));
      closeButton.setText("Close");
      add(closeButton, "wrap");

      // Register to be informed when the current stage position changes.
      studio_.events().registerForEvents(this);
      refreshCurrentPosition();
   }
   
   private JButton posListButton(Dimension buttonSize, Font font) {
      JButton button = new JButton();
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setFont(font);
      button.setMargin(new Insets(0, 0, 0, 0));
      
      return button;
   }
   
   public void addListeners() { 
       // This method should be called after the constructor finishes to fully register all needed listeners.
      axisTable_.addMouseListener(this);
      posTable_.addMouseListener(this);      
      getPositionList().addChangeListener(this);    
   }
   
   
   @Override
   public void stateChanged(ChangeEvent e) {
      GUIUtils.invokeLater(new Runnable() {
         @Override
         public void run() {
            posTable_.revalidate();
            axisTable_.revalidate();
         }
      });
   }

   protected void updateMarkButtonText() {
      MultiStagePosition msp = getPositionList().
              getPosition(posTable_.getSelectedRow() - 1);
      if (markButton_ != null) {
         if (msp == null) {
            markButton_.setText("Mark");
         } else {
            markButton_.setText("Replace");
         }
      }
   }
   

   public void addPosition(MultiStagePosition msp, String label) {
      msp.setLabel(label);
      getPositionList().addPosition(msp);
      updatePositionData();
   }

   public void addPosition(MultiStagePosition msp) {
      msp.setLabel(getPositionList().generateLabel());
      getPositionList().addPosition(msp);
      updatePositionData();
   }

   protected boolean savePositionListAs() {
      File f = FileDialogs.save(this, "Save the position list", POSITION_LIST_FILE);
      if (f != null) {
         curFile_ = f;
         
         String fileName = curFile_.getAbsolutePath();
         int i = fileName.lastIndexOf('.');
         int j = fileName.lastIndexOf(File.separatorChar);
         if (i <= 0 || i < j) {
            i = fileName.length();
         }
         fileName = fileName.substring(0, i);
         fileName += "." + POS;

         try {
            getPositionList().save(fileName);
            posListDir_ = curFile_.getParent();
         } catch (Exception e) {
            ReportingUtils.showError(e);
            return false;
         }
         return true;
      }
      
      return false;
   }

   protected void loadPositionList() {
      File f = FileDialogs.openFile(this, "Load a position list", POSITION_LIST_FILE);
      if (f != null) {
         curFile_ = f;
         try {
            getPositionList().load(curFile_.getAbsolutePath());
            posListDir_ = curFile_.getParent();
         } catch (Exception e) {
            ReportingUtils.showError(e);
         } finally {
            updatePositionData();            
         }
      }
   }

   protected void updatePositionData() {
      positionModel_.fireTableDataChanged();
   }
   
   public void rebuildAxisList() {
      axisList_ = new AxisList(core_);
      axisModel_.fireTableDataChanged();
   }

   public void activateAxisTable(boolean state) {
      axisModel_.setEditable(state);
      axisTable_.setEnabled(state);
   }
   
   public void setPositionList(PositionList pl) {
      positionModel_.setData(pl);
      updatePositionData();
   }

   protected void goToCurrentPosition() {
      MultiStagePosition msp = getPositionList().getPosition(posTable_.getSelectedRow() - 1);
      if (msp == null)
         return;

      try {
         MultiStagePosition.goToPosition(msp, core_);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      refreshCurrentPosition();
   }

   protected void clearAllPositions() {
      getPositionList().clearAllPositions();
      updatePositionData();
   }

   
   protected void incrementOrderOfSelectedPosition(int direction) {
      int currentRow = posTable_.getSelectedRow() - 1;
      int newEdittingRow = -1;

      if (0 <= currentRow) {
         int destinationRow = currentRow + direction;
         {
            if (0 <= destinationRow) {
               if (destinationRow < posTable_.getRowCount()) {
                  PositionList pl = getPositionList();

                  MultiStagePosition[] mspos = pl.getPositions();

                  MultiStagePosition tmp = mspos[currentRow];
                  pl.replacePosition(currentRow, mspos[destinationRow]);//
                  pl.replacePosition(destinationRow, tmp);
                  positionModel_.setData(pl);
                  if (destinationRow + 1 < positionModel_.getRowCount()) {
                     newEdittingRow = destinationRow + 1;
                  }
               } else {
                  newEdittingRow = posTable_.getRowCount() - 1;
               }
            } else {
               newEdittingRow = 1;
            }
         }
      }
      updatePositionData();

      if (-1 < newEdittingRow) {
         posTable_.changeSelection(newEdittingRow, newEdittingRow, false, false);
         posTable_.requestFocusInWindow();
      }
      updateMarkButtonText();

   }
   
   
   protected void removeSelectedPositions() {
      int[] selectedRows = posTable_.getSelectedRows();
      // Reverse the rows so that we delete from the end; if we delete from
      // the front then the position list gets re-ordered as we go and we 
      // delete the wrong positions!
      for (int i = selectedRows.length - 1; i >= 0; --i) {
         getPositionList().removePosition(selectedRows[i] - 1);
      }
      updatePositionData();
   }

   /**
    * Store current xyPosition.
    * Use data collected in refreshCurrentPosition()
    * @param shouldOverwrite if true, overwrite the selected marked position.
    */
   public void markPosition(boolean shouldOverwrite) {
      refreshCurrentPosition();
      MultiStagePosition msp = curMsp_;

      MultiStagePosition selMsp = null;
      if (shouldOverwrite) {
         selMsp = getPositionList().getPosition(posTable_.getSelectedRow() -1);
      }

      if (selMsp == null) {
         msp.setLabel(getPositionList().generateLabel());
         getPositionList().addPosition(msp);
         updatePositionData();
      }
      else { // replace instead of add
         msp.setLabel(getPositionList().getPosition(
                 posTable_.getSelectedRow() - 1).getLabel() );
         int selectedRow = posTable_.getSelectedRow();
         getPositionList().replacePosition(
                 posTable_.getSelectedRow() -1, msp);
         updatePositionData();
         // Not sure why this is here as we undo the selection after
         // this functions exits...
         posTable_.setRowSelectionInterval(selectedRow, selectedRow);
      }
   }
   
   /**
    * Displays a popup menu to let the user select an axis, which will then
    * invoke mergePositionsAlongAxis, below.
    * @param event - Mouse Event that gives X and Y coordinates
    */
   public void mergePositions(MouseEvent event) {
      MergeStageDevicePopupMenu menu = new MergeStageDevicePopupMenu(this, core_);
      menu.show(event.getComponent(), event.getX(), event.getY());
   }

   /**
    * Given a device name, change all currently-selected positions so that
    * their positions for that device match the current stage position for
    * that device.
    * @param deviceName name of device name 
    */
   public void mergePositionsWithDevice(String deviceName) {
      int[] selectedRows = posTable_.getSelectedRows();
      double x = 0, y = 0, z = 0;
      // Find the current position for that device in curMsp_
      for (int posIndex = 0; posIndex < curMsp_.size(); ++posIndex) {
         StagePosition subPos = curMsp_.get(posIndex);
         if (subPos.getStageDeviceLabel().equals(deviceName)) {
            if (subPos.is1DStagePosition()) {
               z = subPos.get1DPosition();
            }
            else if (subPos.is2DStagePosition()) {
               x = subPos.get2DPositionX();
               y = subPos.get2DPositionY();
            }
         }
      }
      for (int row : selectedRows) {
         // Find the appropriate StagePosition in this MultiStagePosition and
         // update its values.
         MultiStagePosition listPos = getPositionList().getPosition(row - 1);
         boolean foundPos = false;
         for (int posIndex = 0; posIndex < listPos.size(); ++posIndex) {
            StagePosition subPos = listPos.get(posIndex);
            if (subPos.getStageDeviceLabel().equals(deviceName)) {
               if (subPos.is1DStagePosition()) {
                  subPos.set1DPosition(subPos.getStageDeviceLabel(), z);
               } else if (subPos.is2DStagePosition()) {
                  subPos.set2DPosition(subPos.getStageDeviceLabel(), x, y);
               }
               foundPos = true;
            }
         }
         if (!foundPos) {
            // No existing StagePosition for this location; add a new one.
            StagePosition subPos;
            //subPos.stageName = deviceName;
            DeviceType type;
            try {
               type = core_.getDeviceType(deviceName);
            }
            catch (Exception e) {
               ReportingUtils.logError(e, "Unable to determine stage device type");
               continue;
            }
            if (type == DeviceType.StageDevice) {
               subPos = StagePosition.create1D(deviceName, z);
            }
            else if (type == DeviceType.XYStageDevice) {
               subPos = StagePosition.create2D(deviceName, x, y);
            }
            else {
               throw new IllegalArgumentException("Unrecognized stage device type " + type + " for stage " + deviceName);
            }
            listPos.add(subPos);
         }
      }
      updatePositionData();
   }
 
   // The stage position changed; update curMsp_.
   @Subscribe
   public void onStagePositionChanged(StagePositionChangedEvent event) {
      // Do the update on the EDT (1) to prevent data races and (2) to prevent
      // deadlock by calling back into the stage device adapter.
      SwingUtilities.invokeLater(new Runnable() {
         @Override public void run() {
            refreshCurrentPosition();
         }
      });
   }

   // The stage position changed; update curMsp_.
   @Subscribe
   public void onXYStagePositionChanged(XYStagePositionChangedEvent event) {
      // Do the update on the EDT (1) to prevent data races and (2) to prevent
      // deadlock by calling back into the stage device adapter.
      SwingUtilities.invokeLater(new Runnable() {
         @Override public void run() {
            refreshCurrentPosition();
         }
      });
   }

   /**
    * Update display of the current stage position.
    */
   private void refreshCurrentPosition() {
      StringBuilder sb = new StringBuilder();
      MultiStagePosition msp = new MultiStagePosition();
      msp.setDefaultXYStage(core_.getXYStageDevice());
      msp.setDefaultZStage(core_.getFocusDevice());

      // read 1-axis stages
      try {
         StrVector stages = core_.getLoadedDevicesOfType(DeviceType.StageDevice);
         for (int i=0; i<stages.size(); i++) {
            if (axisList_.use(stages.get(i))) {
               StagePosition sp = StagePosition.create1D(stages.get(i),
                       core_.getPosition(stages.get(i)));
               msp.add(sp);
               sb.append(sp.getVerbose()).append("\n");
            }
         }

         // read 2-axis stages
         StrVector stages2D = core_.getLoadedDevicesOfType(DeviceType.XYStageDevice);
         for (int i=0; i<stages2D.size(); i++) {
            if (axisList_.use(stages2D.get(i))) {
               String stageName = stages2D.get(i);
               StagePosition sp = StagePosition.create2D(stageName,
                       core_.getXPosition(stageName),
                       core_.getYPosition(stageName));
               msp.add(sp);
               sb.append(sp.getVerbose()).append("\n");
            }
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

      curMsp_ = msp;
      curMsp_.setLabel("Current");
      positionModel_.setCurrentMSP(curMsp_);

      positionModel_.fireTableCellUpdated(0, 1);
      positionModel_.fireTableCellUpdated(0, 0);
      posTable_.revalidate();
      axisTable_.revalidate();
   }

   public boolean useDrive(String drive) {
      return axisList_.use(drive);
   }
   
   /**
    * Returns the first selected drive of the specified type
    * @param type
    * @return 
    */
   private String getAxis(AxisData.AxisType type) {
      for (int i = 0; i < axisList_.getNumberOfPositions(); i++) {
         AxisData axis = axisList_.get(i);
         if (axis.getUse() && axis.getType() == type) {
            return axis.getAxisName();
         }
      }
      return null;
   }
   
   /**
    * Returns the first selected XYDrive or null when none is selected
    * @return 
    */
   public String get2DAxis() {
      return getAxis(AxisData.AxisType.twoD);
   }
   
    /**
    * Returns the first selected Drive or null when none is selected
    * @return 
    */
   public StrVector get1DAxes() {
        AxisData.AxisType axType = AxisData.AxisType.oneD;
        StrVector result = new StrVector();
        for (int i = 0; i < axisList_.getNumberOfPositions(); i++) {
            AxisData axis = axisList_.get(i);
            if (axis.getUse() && axis.getType() == axType) {
                result.add(axis.getAxisName());
            }
        }
        return result;
   }

   protected void showCreateTileDlg() {
      TileCreatorDlg tileCreatorDlg = new TileCreatorDlg(core_, studio_, this);
      tileCreatorDlg.setVisible(true);
   }

   public PositionList getPositionList() {
      return positionModel_.getPositionList();

   }

   /**
    * Calibrate the XY stage.
    */
   private void calibrate() {

      JOptionPane.showMessageDialog(this, "ALERT! Please REMOVE objectives! It may damage lens!", 
            "Calibrate the XY stage", JOptionPane.WARNING_MESSAGE);

      Object[] options = { "Yes", "No"};
      if (JOptionPane.YES_OPTION != JOptionPane.showOptionDialog(this, "Really calibrate your XY stage?", "Are you sure?", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]))
         return ;

      // calibrate xy-axis stages
      try {

         // read 2-axis stages
         StrVector stages2D = core_.getLoadedDevicesOfType(DeviceType.XYStageDevice);
         for (int i=0; i<stages2D.size(); i++) {

            String deviceName = stages2D.get(i);

            double [] x1 = new double[1];
            double [] y1 = new double[1];

            core_.getXYPosition(deviceName,x1,y1);

            StopCalThread stopThread = new StopCalThread(); 
            CalThread calThread = new CalThread();

            stopThread.setPara(calThread, this, deviceName, x1, y1);
            calThread.setPara(stopThread, this, deviceName, x1, y1);

            stopThread.start();
            Thread.sleep(100);
            calThread.start();
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

   }

   class StopCalThread extends Thread {
      double [] x1;
      double [] y1;
      String deviceName;
      MMFrame d;
      Thread otherThread;

      public void setPara(Thread calThread, MMFrame d, String deviceName, double [] x1, double [] y1) {
         this.otherThread = calThread;
         this.d = d;
         this.deviceName = deviceName;
         this.x1 = x1;
         this.y1 = y1;
      }
      @Override
      public void run() {

         try {

            // popup a dialog that says stop the calibration
            Object[] options = { "Stop" };
            int option = JOptionPane.showOptionDialog(d, "Stop calibration?", "Calibration", 
                  JOptionPane.CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                  null, options, options[0]);

            if (option==0) {//stop the calibration 

               otherThread.interrupt();
               otherThread = null;

               if (isInterrupted())return;
               Thread.sleep(50);
               core_.stop(deviceName);
               if (isInterrupted())return;
               boolean busy = core_.deviceBusy(deviceName);
               while (busy){
                  if (isInterrupted())return;
                  core_.stop(deviceName);
                  if (isInterrupted())return;
                  busy = core_.deviceBusy(deviceName);
               }

               Object[] options2 = { "Yes", "No" };
               option = JOptionPane.showOptionDialog(d, "RESUME calibration?", "Calibration", 
                     JOptionPane.OK_OPTION, JOptionPane.QUESTION_MESSAGE,
                     null, options2, options2[0]);

               if (option==1) return; // not to resume, just stop

               core_.home(deviceName);


               StopCalThread sct = new StopCalThread();
               sct.setPara(this, d, deviceName, x1, y1);

               busy = core_.deviceBusy(deviceName);
               if ( busy ) sct.start();

               if (isInterrupted())return;
               busy = core_.deviceBusy(deviceName);
               while (busy){
                  if (isInterrupted())return;
                  Thread.sleep(100);
                  if (isInterrupted())return;
                  busy = core_.deviceBusy(deviceName);
               }

               sct.interrupt();

               //calibrate_(deviceName, x1, y1);

               double [] x2 = new double[1];
               double [] y2 = new double[1];

               // check if the device busy?
               busy = core_.deviceBusy(deviceName);
               int delay=500; //500 ms 
               int period=600000;//600 sec
               int elapse = 0;
               while (busy && elapse<period){
                  Thread.sleep(delay);
                  busy = core_.deviceBusy(deviceName);
                  elapse+=delay;
               }

               // now the device is not busy

               core_.getXYPosition(deviceName,x2,y2);

               // zero the coordinates
               core_.setOriginXY(deviceName);

               BackThread bt = new BackThread();
               bt.setPara(d);
               bt.start();

               core_.setXYPosition(deviceName, x1[0]-x2[0], y1[0]-y2[0]);               
               
               if (isInterrupted())
                  return;
               
               busy = core_.deviceBusy(deviceName);
               while (busy){
                  if (isInterrupted())return;
                  Thread.sleep(100);
                  if (isInterrupted())return;
                  busy = core_.deviceBusy(deviceName);
               }

               bt.interrupt();

            }           
         } catch (InterruptedException e) { ReportingUtils.logError(e);}
         catch (Exception e) {
            ReportingUtils.showError(e);
         }
      }
   } // End StopCalThread class

   class CalThread extends Thread {

      double [] x1;
      double [] y1;
      String deviceName;
      MMFrame d;
      Thread stopThread;

      public void setPara(Thread stopThread, MMFrame d, String deviceName, double [] x1, double [] y1) {
         this.stopThread = stopThread;
         this.d = d;
         this.deviceName = deviceName;
         this.x1 = x1;
         this.y1 = y1;
      }

      @Override
      public void run() {
         try {
            core_.home(deviceName);

            // check if the device busy?
            boolean busy = core_.deviceBusy(deviceName);
            int delay = 500; //500 ms 
            int period = 600000;//600 sec
            int elapse = 0;
            while (busy && elapse < period) {
               Thread.sleep(delay);
               busy = core_.deviceBusy(deviceName);
               elapse += delay;
            }

            stopThread.interrupt();
            stopThread = null;

            double[] x2 = new double[1];
            double[] y2 = new double[1];

            core_.getXYPosition(deviceName, x2, y2);

            // zero the coordinates
            core_.setOriginXY(deviceName);

            BackThread bt = new BackThread();
            bt.setPara(d);
            bt.start();

            core_.setXYPosition(deviceName, x1[0] - x2[0], y1[0] - y2[0]);

            if (isInterrupted()) {
               return;
            }
            busy = core_.deviceBusy(deviceName);
            while (busy) {
               if (isInterrupted()) {
                  return;
               }
               Thread.sleep(100);
               if (isInterrupted()) {
                  return;
               }
               busy = core_.deviceBusy(deviceName);
            }

            bt.interrupt();

         } catch (InterruptedException e) {
            ReportingUtils.logError(e);
         } catch (Exception e) {
            ReportingUtils.showError(e);
         }
      }
   } // End CalThread class

   class BackThread extends Thread {
      MMFrame d;

      public void setPara(MMFrame d) {
         this.d = d;
      }     
      @Override
      public void run() {
         JOptionPane.showMessageDialog(d, "Going back to the original position!");              
      }
   } // End BackThread class

   /*
    * Implementation of MouseListener
    * Sole purpose is to be able to unselect rows in the positionlist table
    */
   @Override
   public void mousePressed (MouseEvent e) {}
   @Override
   public void mouseReleased (MouseEvent e) {}
   @Override
   public void mouseEntered (MouseEvent e) {}
   @Override
   public void mouseExited (MouseEvent e) {}
   /*
    * This event is fired after the table sets its selection
    * Remember where was clicked previously so as to allow for toggling the selected row
    */
   private static int lastRowClicked_;
   @Override
   public void mouseClicked (MouseEvent e) {
      java.awt.Point p = e.getPoint();
      int rowIndex = posTable_.rowAtPoint(p);
      if (rowIndex >= 0) {
         if (rowIndex == posTable_.getSelectedRow() && rowIndex == lastRowClicked_) {
               posTable_.clearSelection();
               lastRowClicked_ = -1;
         } else
            lastRowClicked_ = rowIndex;
      }
      updateMarkButtonText();
   }

   /** 
    * Generate a dialog that will call our offsetSelectedSites() function
    * with a set of X/Y/Z offsets to apply.
    */
   @SuppressWarnings("ResultOfObjectAllocationIgnored")
   private void offsetPositions() {
      new OffsetPositionsDialog(this, core_);
   }

   /**
    * Given a device (either a StageDevice or XYStageDevice) and a Vector
    * of floats, apply the given offsets to all selected positions for that
    * particular device. 
    * @param deviceName
    * @param offsets
    */
   public void offsetSelectedSites(String deviceName, ArrayList<Float> offsets) {
      PositionList positions = getPositionList();
      for (int rowIndex : posTable_.getSelectedRows()) {
         MultiStagePosition multiPos = positions.getPosition(rowIndex - 1);
         for (int posIndex = 0; posIndex < multiPos.size(); ++posIndex) {
            StagePosition subPos = multiPos.get(posIndex);
            if (subPos.getStageDeviceLabel().equals(deviceName)) {
               if (subPos.is1DStagePosition()) {
                  subPos.set1DPosition(subPos.getStageDeviceLabel(),
                          subPos.get1DPosition() + offsets.get(0));
               } else if (subPos.is2DStagePosition()) {
                  subPos.set2DPosition(subPos.getStageDeviceLabel(),
                          subPos.get2DPositionX() + offsets.get(0),
                          subPos.get2DPositionY() + offsets.get(1));
               }
            }
         }
      }
      updatePositionData();
   }
}

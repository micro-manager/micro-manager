///////////////////////////////////////////////////////////////////////////////
//FILE:          XYPositionListDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 8, 2007
//              Nico Stuurman, nico@cmp.ucsf.edu June 23, 2009

//COPYRIGHT:    University of California, San Francisco, 2007, 2008, 2009, 2014

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,

//CVS:          $Id$

package org.micromanager.positionlist;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.prefs.Preferences;
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
import org.micromanager.MMOptions;
import org.micromanager.MMStudio;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.StagePosition;
import org.micromanager.api.events.StagePositionChangedEvent;
import org.micromanager.api.events.XYStagePositionChangedEvent;
import org.micromanager.dialogs.AcqControlDlg;
import org.micromanager.events.EventManager;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.FileDialogs.FileType;
import org.micromanager.utils.GUIColors;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;


public class PositionListDlg extends MMDialog implements MouseListener, ChangeListener {
   private static final long serialVersionUID = 1L;
   private String posListDir_;
   private File curFile_;
   private static final String POS = "pos";
   private static final String POS_COL0_WIDTH = "posCol0WIDTH";
   private static final String AXIS_COL0_WIDTH = "axisCol0WIDTH";
   // @SuppressWarnings("unused")
   private static final FileType POSITION_LIST_FILE =
           new FileType("POSITION_LIST_FILE","Position list file",
                        System.getProperty("user.home") + "/PositionList.pos",
                        true, POS);

   private Font arialSmallFont_;
   private JTable posTable_;
   private final JTable axisTable_;
   private final AxisTableModel axisModel_;
   private CMMCore core_;
   private ScriptInterface studio_;
   private AcqControlDlg acqControlDlg_;
   private MMOptions opts_;
   private Preferences prefs_;
   private GUIColors guiColors_;
   private AxisList axisList_;
   private final JButton tileButton_;

   private MultiStagePosition curMsp_;
   public JButton markButton_;
   private final PositionTableModel positionModel_;

   private EventBus bus_;

   @Subscribe
   public void onTileUpdate(MoversChangedEvent event) {
      setTileButtonEnabled();
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
    * @param core -  MMCore
    * @param gui - ScriptInterface
    * @param posList - Position list to be displayed in this dialog
    * @param acd - MDA window
    * @param opts - MicroManager Options
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public PositionListDlg(CMMCore core, ScriptInterface gui, 
                     PositionList posList, AcqControlDlg acd, MMOptions opts) {
      super();
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            savePosition();
            int posCol0Width = posTable_.getColumnModel().getColumn(0).getWidth();
            prefs_.putInt(POS_COL0_WIDTH, posCol0Width);
            int axisCol0Width = axisTable_.getColumnModel().getColumn(0).getWidth();
            prefs_.putInt(AXIS_COL0_WIDTH, axisCol0Width);
         }
      });
      core_ = core;
      studio_ = gui;
      bus_ = new EventBus();
      bus_.register(this);
      opts_ = opts;
      acqControlDlg_ = acd;
      guiColors_ = new GUIColors();

      prefs_ = getPrefsNode();
      
      setTitle("Stage Position List");
      setLayout(new MigLayout("flowy, filly, insets 8", "[grow][]", 
              "[top]"));
      setMinimumSize(new Dimension(275, 365));
      loadPosition(100, 100, 362, 595);

      arialSmallFont_ = new Font("Arial", Font.PLAIN, 10);

      final JScrollPane scrollPane = new JScrollPane();
      add(scrollPane, "split, spany, growy, growx, bottom 1:push");

      final JScrollPane axisPane = new JScrollPane();
      // axisPanel should always be visible
      axisPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
      add(axisPane, "growx, wrap");

      final TableCellRenderer firstRowRenderer = new FirstRowRenderer(arialSmallFont_);
      posTable_ = new JTable() {
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
      positionModel_ = new PositionTableModel();
      positionModel_.setData(posList);
      posTable_.setModel(positionModel_);
      scrollPane.setViewportView(posTable_);
      CellEditor cellEditor_ = new CellEditor(arialSmallFont_);
      cellEditor_.addListener();
      posTable_.setDefaultEditor(Object.class, cellEditor_);
      posTable_.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      // set column divider location
      int posCol0Width = prefs_.getInt(POS_COL0_WIDTH, 75);
      posTable_.getColumnModel().getColumn(0).setWidth(posCol0Width);
      posTable_.getColumnModel().getColumn(0).setPreferredWidth(posCol0Width);
      posTable_.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
      
      axisTable_ = new JTable();
      axisTable_.setFont(arialSmallFont_);
      axisList_ = new AxisList(core_, prefs_);
      axisModel_ = new AxisTableModel(axisList_, axisTable_, bus_, prefs_);
      axisTable_.setModel(axisModel_);
      axisPane.setViewportView(axisTable_);
      // make sure that the complete axis Table will always be visible
      int tableHeight = axisList_.getNumberOfPositions() * axisTable_.getRowHeight();
      axisPane.setMaximumSize(new Dimension(32767, 30 + tableHeight));
      axisPane.setMinimumSize(new Dimension(50, 30 + tableHeight));
      // set divider location
      int axisCol0Width = prefs_.getInt(AXIS_COL0_WIDTH, 75);
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
            markPosition();
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
      EventManager.register(this);
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
      axisTable_.addMouseListener(this);
      posTable_.addMouseListener(this);      
      getPositionList().addChangeListener(this);    
   }
   
   
    @Override
    public void stateChanged(ChangeEvent e) {
        try {
            GUIUtils.invokeLater(new Runnable() {
               @Override
                public void run() {
                    posTable_.revalidate();
                    axisTable_.revalidate();
                }
            });
        } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
        } catch (InvocationTargetException ex) {
           ReportingUtils.logError(ex);
      }
    }

   protected void updateMarkButtonText() {
      PositionTableModel tm = (PositionTableModel) posTable_.getModel();
      MultiStagePosition msp = tm.getPositionList().
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
      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      msp.setLabel(label);
      ptm.getPositionList().addPosition(msp);
      ptm.fireTableDataChanged();
   }

   public void addPosition(MultiStagePosition msp) {
      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      msp.setLabel(ptm.getPositionList().generateLabel());
      ptm.getPositionList().addPosition(msp);
      ptm.fireTableDataChanged();
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
         } catch (MMException e) {
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
         } catch (MMException e) {
            ReportingUtils.showError(e);
         } finally {
            updatePositionData();            
         }
      }
   }

   public void updatePositionData() {
      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      ptm.fireTableDataChanged();
   }
   
   public void rebuildAxisList() {
      axisList_ = new AxisList(core_, prefs_);
      AxisTableModel axm = (AxisTableModel)axisTable_.getModel();
      axm.fireTableDataChanged();
   }

   public void activateAxisTable(boolean state) {
      axisModel_.setEditable(state);
      axisTable_.setEnabled(state);
   }
   
   public void setPositionList(PositionList pl) {
      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      ptm.setData(pl);
      ptm.fireTableDataChanged();
   }

   protected void goToCurrentPosition() {
      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      MultiStagePosition msp = ptm.getPositionList().getPosition(posTable_.getSelectedRow() - 1);
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
      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      ptm.getPositionList().clearAllPositions();
      ptm.fireTableDataChanged();
      acqControlDlg_.updateGUIContents();
   }

   
   protected void incrementOrderOfSelectedPosition(int direction) {
      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      int currentRow = posTable_.getSelectedRow() - 1;
      int newEdittingRow = -1;

      if (0 <= currentRow) {
         int destinationRow = currentRow + direction;
         {
            if (0 <= destinationRow) {
               if (destinationRow < posTable_.getRowCount()) {
                  PositionList pl = ptm.getPositionList();

                  MultiStagePosition[] mspos = pl.getPositions();

                  MultiStagePosition tmp = mspos[currentRow];
                  pl.replacePosition(currentRow, mspos[destinationRow]);//
                  pl.replacePosition(destinationRow, tmp);
                  ptm.setData(pl);
                  if (destinationRow + 1 < ptm.getRowCount()) {
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
      ptm.fireTableDataChanged();

      if (-1 < newEdittingRow) {
         posTable_.changeSelection(newEdittingRow, newEdittingRow, false, false);
         posTable_.requestFocusInWindow();
      }
      updateMarkButtonText();

   }
   
   
   protected void removeSelectedPositions() {
      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      int[] selectedRows = posTable_.getSelectedRows();
      // Reverse the rows so that we delete from the end; if we delete from
      // the front then the position list gets re-ordered as we go and we 
      // delete the wrong positions!
      for (int i = selectedRows.length - 1; i >= 0; --i) {
         ptm.getPositionList().removePosition(selectedRows[i] - 1);
      }
      ptm.fireTableDataChanged();
      acqControlDlg_.updateGUIContents();
   }

   /**
    * Store current xyPosition.
    * Use data collected in refreshCurrentPosition()
    */
   public void markPosition() {
      refreshCurrentPosition();
      MultiStagePosition msp = curMsp_;

      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      MultiStagePosition selMsp = 
              ptm.getPositionList().getPosition(posTable_.getSelectedRow() -1);

      if (selMsp == null) {
         msp.setLabel(ptm.getPositionList().generateLabel());
         ptm.getPositionList().addPosition(msp);
         ptm.fireTableDataChanged();
         acqControlDlg_.updateGUIContents();
      } else { // replace instead of add 
         msp.setLabel(ptm.getPositionList().getPosition(
                 posTable_.getSelectedRow() - 1).getLabel() );
         int selectedRow = posTable_.getSelectedRow();
         ptm.getPositionList().replacePosition(
                 posTable_.getSelectedRow() -1, msp);
         ptm.fireTableCellUpdated(selectedRow, 1);
         // Not sure why this is here as we undo the selecion after 
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
         if (!subPos.stageName.equals(deviceName)) {
            continue;
         }
         x = subPos.x;
         y = subPos.y;
         z = subPos.z;
      }
      
      for (int row : selectedRows) {
         // Find the appropriate StagePosition in this MultiStagePosition and
         // update its values.
         MultiStagePosition listPos = positionModel_.getPositionList().getPosition(row - 1);
         for (int posIndex = 0; posIndex < listPos.size(); ++posIndex) {
            StagePosition subPos = listPos.get(posIndex);
            if (!subPos.stageName.equals(deviceName)) {
               continue;
            }
            subPos.x = x;
            subPos.y = y;
            subPos.z = z;
         }
      }
      positionModel_.fireTableDataChanged();
      acqControlDlg_.updateGUIContents();
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
               StagePosition sp = new StagePosition();
               sp.stageName = stages.get(i);
               sp.numAxes = 1;
               sp.x = core_.getPosition(stages.get(i));
               msp.add(sp);
               sb.append(sp.getVerbose()).append("\n");
            }
         }

         // read 2-axis stages
         StrVector stages2D = core_.getLoadedDevicesOfType(DeviceType.XYStageDevice);
         for (int i=0; i<stages2D.size(); i++) {
            if (axisList_.use(stages2D.get(i))) {
               StagePosition sp = new StagePosition();
               sp.stageName = stages2D.get(i);
               sp.numAxes = 2;
               sp.x = core_.getXPosition(stages2D.get(i));
               sp.y = core_.getYPosition(stages2D.get(i));
               msp.add(sp);
               sb.append(sp.getVerbose()).append("\n");
            }
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

      curMsp_ = msp;
      positionModel_.setCurrentMSP(curMsp_);

      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      int selectedRow = posTable_.getSelectedRow();
      ptm.fireTableCellUpdated(0, 1);
      if (selectedRow > 0)
         posTable_.setRowSelectionInterval(selectedRow, selectedRow);
      
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
   public String get1DAxis() {
      return getAxis(AxisData.AxisType.oneD);
   }

   protected void showCreateTileDlg() {
      TileCreatorDlg tileCreatorDlg = new TileCreatorDlg(core_, opts_, this);
      studio_.addMMBackgroundListener(tileCreatorDlg);
      studio_.addMMListener(tileCreatorDlg);
      tileCreatorDlg.setBackground(guiColors_.background.get(opts_.displayBackground_));
      tileCreatorDlg.setVisible(true);
   }

   private PositionList getPositionList() {
      PositionTableModel ptm = (PositionTableModel) posTable_.getModel();
      return ptm.getPositionList();

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
      MMDialog d;
      Thread otherThread;

      public void setPara(Thread calThread, MMDialog d, String deviceName, double [] x1, double [] y1) {
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
      MMDialog d;
      Thread stopThread;

      public void setPara(Thread stopThread, MMDialog d, String deviceName, double [] x1, double [] y1) {
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
      MMDialog d;

      public void setPara(MMDialog d) {
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
      PositionList positions = positionModel_.getPositionList();
      for (int rowIndex : posTable_.getSelectedRows()) {
         MultiStagePosition multiPos = positions.getPosition(rowIndex - 1);
         for (int posIndex = 0; posIndex < multiPos.size(); ++posIndex) {
            StagePosition subPos = multiPos.get(posIndex);
            if (subPos.stageName.equals(deviceName)) {
               // This is the one to modify.
               if (subPos.numAxes >= 3) {
                  // With the current definition of StagePosition axis fields,
                  // I don't think this can ever happen, but hey, future-
                  // proofing.
                  subPos.z += offsets.get(2);
               }
               if (subPos.numAxes >= 2) {
                  subPos.y += offsets.get(1);
               }
               // Assume every stage device has at least one axis, because
               // if they don't, then oh dear...
               subPos.x += offsets.get(0);
            }
         }
      }
      positionModel_.fireTableDataChanged();
      acqControlDlg_.updateGUIContents();
   }
}

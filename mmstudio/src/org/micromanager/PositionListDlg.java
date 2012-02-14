///////////////////////////////////////////////////////////////////////////////
//FILE:          XYPositionListDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 8, 2007
//              Nico Stuurman, nico@cmp.ucsf.edu June 23, 2009

//COPYRIGHT:    University of California, San Francisco, 2007, 2008, 2009

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,

//CVS:          $Id$

package org.micromanager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.GUIColors;
import org.micromanager.utils.MMDialog;

import com.swtdesigner.SwingResourceManager;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.FileDialogs.FileType;
import org.micromanager.utils.ReportingUtils;


public class PositionListDlg extends MMDialog implements MouseListener {
   private static final long serialVersionUID = 1L;
   private String posListDir_;
   private File curFile_;
   @SuppressWarnings("unused")
   private static FileType POSITION_LIST_FILE =
           new FileType("POSITION_LIST_FILE","Position list file",
                        System.getProperty("user.home") + "/PositionList.pos",
                        true,"pos");

   private JTable posTable_;
   private JTable axisTable_;
   private SpringLayout springLayout;
   private CMMCore core_;
   private ScriptInterface gui_;
   private MMOptions opts_;
   private Preferences prefs_;
   private TileCreatorDlg tileCreatorDlg_;
   private GUIColors guiColors_;
   private AxisList axisList_;

   private MultiStagePosition curMsp_;
   public JButton markButtonRef;

   private class PosTableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;
      public final String[] COLUMN_NAMES = new String[] {
            "Label",
            "Position [um]"
      };
      private PositionList posList_;

      public void setData(PositionList pl) {
         posList_ = pl;
      }

      public PositionList getPositionList() {
         return posList_;
      }

      public int getRowCount() {
         return posList_.getNumberOfPositions() + 1;
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      @Override
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         MultiStagePosition msp;
         if (rowIndex == 0)
            msp = curMsp_;
         else
            msp = posList_.getPosition(rowIndex -1);
         if (columnIndex == 0) {
            if (rowIndex == 0)
               return ("Current");
            return msp.getLabel();
         } else if (columnIndex == 1) {
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<msp.size(); i++) {
               StagePosition sp = msp.get(i);
               if (i!=0)
                  sb.append(";");
               sb.append(sp.getVerbose());
            }
            return sb.toString();
         } else
            return null;
      }
      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
         if (rowIndex == 0)
            return false;
         if (columnIndex == 0)
            return true;
         return false;
      }
      @Override
      public void setValueAt(Object value, int rowIndex, int columnIndex) {
         if (columnIndex == 0) {
            MultiStagePosition msp = posList_.getPosition(rowIndex - 1);
            if (msp != null)
               msp.setLabel(((String) value).replaceAll("[^0-9a-zA-Z_]", "-"));
         }
      }
   }

   private class AxisData {
      public AxisData(boolean use, String axisName) {
         use_ = use;
         axisName_ = axisName;
      }
      public boolean getUse() {return use_;}
      public String getAxisName() {return axisName_;}
      public void setUse(boolean use) {use_ = use;}
      private boolean use_;
      private String axisName_;
   }

   /**
    * List with Axis data.  Currently, we use only a singel global instance of this class
    */
   private class AxisList {
      private Vector<AxisData> axisList_ = new Vector<AxisData>();
      public AxisList() {
         // Initialize the axisList.
         try {
            // add 1D stages
            StrVector stages = core_.getLoadedDevicesOfType(DeviceType.StageDevice);
            for (int i=0; i<stages.size(); i++) {
               axisList_.add(new AxisData(prefs_.getBoolean(stages.get(i),true), stages.get(i)));
            }
            // read 2-axis stages
            StrVector stages2D = core_.getLoadedDevicesOfType(DeviceType.XYStageDevice);
            for (int i=0; i<stages2D.size(); i++) {
               axisList_.add(new AxisData(prefs_.getBoolean(stages2D.get(i),true), stages2D.get(i)));
            }
         } catch (Exception e) {
            handleError(e);
         }
      }
      public AxisData get(int i) {
         if (i >=0 && i < axisList_.size())
            return ((AxisData) axisList_.get(i));
         return null;
      }
      public int getNumberOfPositions() {
         return axisList_.size();
      }
      public boolean use(String axisName) {
         for (int i=0; i< axisList_.size(); i++) {
            if (axisName.equals(get(i).getAxisName()))
                  return get(i).getUse();
         }
         // not in the list??  It might be time to refresh the list.  
         return true;
      }
         
   }

   /**
    * Model holding axis data, used to determine which axis will be recorded
    */
   private class AxisTableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;
      public final String[] COLUMN_NAMES = new String[] {
            "Use",
            "Axis"
      };

      /*
      public AxisData getAxisData(int i) {
         if (i>=0 && i < getRowCount())
            return axisList_.get(i);
         return null;
      }
      */
      
      public int getRowCount() {
         return axisList_.getNumberOfPositions();
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      @Override
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         AxisData aD = axisList_.get(rowIndex);
         if (aD != null) {
            if (columnIndex == 0) {
               return aD.getUse();
            } else if (columnIndex == 1) {
               return aD.getAxisName();
            }
         }
         return null;
      }
      @Override
      public Class<?> getColumnClass(int c) {
         return getValueAt(0, c).getClass();
      }
      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
         if (columnIndex == 0)
            return true;
         return false;
      }
      @Override
      public void setValueAt(Object value, int rowIndex, int columnIndex) {
         if (columnIndex == 0) {
            axisList_.get(rowIndex).setUse( (Boolean) value);
            prefs_.putBoolean(axisList_.get(rowIndex).getAxisName(), (Boolean) value);
         }
         fireTableCellUpdated(rowIndex, columnIndex);
         axisTable_.clearSelection();
      }
   }

   /**
    * Renders the first row of the position list table
    */
   public class FirstRowRenderer extends JLabel implements TableCellRenderer {
   private static final long serialVersionUID = 1L;

   public FirstRowRenderer() {
          setFont(new Font("Arial", Font.PLAIN, 10));
          setOpaque(true);
       }

       public Component getTableCellRendererComponent(
                            JTable table, Object text,
                            boolean isSelected, boolean hasFocus,
                            int row, int column) {

           setText((String) text);
           setBackground(Color.lightGray);
        
           return this;
        }
    }


   /**
    * Create the dialog
    */
   public PositionListDlg(CMMCore core, ScriptInterface gui, 
                           PositionList posList, MMOptions opts) {
      super();
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            savePosition();
         }
      });
      core_ = core;
      gui_ = gui;
      opts_ = opts;
      guiColors_ = new GUIColors();
      setTitle("Stage-position List");
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setBounds(100, 100, 362, 595);

      Preferences root = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root.node(root.absolutePath() + "/XYPositionListDlg");
      setPrefsNode(prefs_);

      Rectangle r = getBounds();
      loadPosition(r.x, r.y, r.width, r.height);

      setBackground(gui_.getBackgroundColor());
      gui_.addMMBackgroundListener(this);

      final JScrollPane scrollPane = new JScrollPane();
      getContentPane().add(scrollPane);

      final JScrollPane axisPane = new JScrollPane();
      getContentPane().add(axisPane);

      final TableCellRenderer firstRowRenderer = new FirstRowRenderer();
      posTable_ = new JTable() {
      private static final long serialVersionUID = -3873504142761785021L;

      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
            if (row == 0)
               return firstRowRenderer;
            return super.getCellRenderer(row, column);
         }
      };
      posTable_.setFont(new Font("Arial", Font.PLAIN, 10));
      PosTableModel model = new PosTableModel();
      model.setData(posList);
      posTable_.setModel(model);
      CellEditor cellEditor_ = new CellEditor();
      posTable_.setDefaultEditor(Object.class, cellEditor_);
      posTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scrollPane.setViewportView(posTable_);
      posTable_.addMouseListener(this);

      

      axisTable_ = new JTable();
      axisTable_.setFont(new Font("Arial", Font.PLAIN, 10));
      axisList_ = new AxisList();
      AxisTableModel axisModel = new AxisTableModel();
      axisTable_.setModel(axisModel);
      axisPane.setViewportView(axisTable_);
      axisTable_.addMouseListener(this);

      springLayout.putConstraint(SpringLayout.SOUTH, axisPane, -10, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane, -10, SpringLayout.NORTH, axisPane);
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane, 10, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, axisPane, -(23 + 23*axisModel.getRowCount()), SpringLayout.SOUTH, getContentPane());

      // mark / replace button:
      JButton markButton = new JButton();
      this.markButtonRef  = markButton;
      markButton.setFont(new Font("Arial", Font.PLAIN, 10));
      markButton.setMaximumSize(new Dimension(0,0));
      markButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            markPosition();
            posTable_.clearSelection();
         }
      });
      markButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/flag_green.png"));
      markButton.setText("Mark");
      markButton.setToolTipText("Adds point with coordinates of current stage position");
      getContentPane().add(markButton);
      
      int northConstraint = 17;
      final int buttonHeight = 27;
      
      springLayout.putConstraint(SpringLayout.NORTH, markButton, northConstraint, SpringLayout.NORTH, getContentPane());     
      springLayout.putConstraint(SpringLayout.SOUTH, markButton, northConstraint+=buttonHeight, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, markButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, markButton, -105, SpringLayout.EAST, getContentPane());

      springLayout.putConstraint(SpringLayout.EAST, scrollPane, -9, SpringLayout.WEST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, scrollPane, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, axisPane, -9, SpringLayout.WEST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, axisPane, 10, SpringLayout.WEST, getContentPane());

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
      
      // move selected row up one row
      final JButton upButton = new JButton();
      upButton.setFont(new Font("Arial", Font.PLAIN, 10));
      upButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            incrementOrderOfSelectedPosition(-1);
         }
      });
      upButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/arrow_up.png"));
      upButton.setText(""); // "Up"
      upButton.setToolTipText("Move currently selected position up list (positions higher on list are acquired earlier)");
      getContentPane().add(upButton);
      springLayout.putConstraint(SpringLayout.NORTH, upButton, northConstraint, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, upButton, northConstraint+buttonHeight, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, upButton, -48, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, upButton, 0, SpringLayout.WEST, markButton);
      
  
      // move selected row down one row
      final JButton downButton = new JButton();
      downButton.setFont(new Font("Arial", Font.PLAIN, 10));
      downButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            incrementOrderOfSelectedPosition(1);
         }
      });
      downButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/arrow_down.png"));
      downButton.setText(""); // "Down"
      downButton.setToolTipText("Move currently selected position down list (lower positions on list are acquired later)");
      getContentPane().add(downButton);
      springLayout.putConstraint(SpringLayout.NORTH, downButton, northConstraint, SpringLayout.NORTH, getContentPane());
      // increment northConstraint for next button....
      springLayout.putConstraint(SpringLayout.SOUTH, downButton, northConstraint+=buttonHeight, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, downButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, downButton, 48, SpringLayout.WEST, markButton);      
      
                
      
      // from this point on, the top right button's positions are computed
      // the Go To button:
      final JButton gotoButton = new JButton();
      gotoButton.setFont(new Font("Arial", Font.PLAIN, 10));
      gotoButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            goToCurrentPosition();
         }
      });
      gotoButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/resultset_next.png"));
      gotoButton.setText("Go to");
      gotoButton.setToolTipText("Moves stage to currently selected position");
      getContentPane().add(gotoButton);
      springLayout.putConstraint(SpringLayout.NORTH, gotoButton, northConstraint, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, gotoButton, northConstraint+=buttonHeight, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, gotoButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, gotoButton, 0, SpringLayout.WEST, markButton);

      final JButton refreshButton = new JButton();
      refreshButton.setFont(new Font("Arial", Font.PLAIN, 10));
      refreshButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            refreshCurrentPosition();
         }
      });
      refreshButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/arrow_refresh.png"));
      refreshButton.setText("Refresh");
      getContentPane().add(refreshButton);
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, northConstraint, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, northConstraint+=buttonHeight, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, 0, SpringLayout.WEST, markButton);

      refreshCurrentPosition();

      final JButton removeButton = new JButton();
      removeButton.setFont(new Font("Arial", Font.PLAIN, 10));
      removeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            removeCurrentPosition();
         }
      });
      removeButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/cross.png"));
      removeButton.setText("Remove");
      removeButton.setToolTipText("Removes currently selected position from list");
      getContentPane().add(removeButton);
      springLayout.putConstraint(SpringLayout.NORTH, removeButton, northConstraint, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, removeButton, northConstraint+=buttonHeight, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, removeButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, removeButton, 0, SpringLayout.WEST, markButton);


      final JButton setOriginButton = new JButton();
      setOriginButton.setFont(new Font("Arial", Font.PLAIN, 10));
      setOriginButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            calibrate(); //setOrigin();
         }
      });
       
      setOriginButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/empty.png"));
      setOriginButton.setText("Set Origin");
      setOriginButton.setToolTipText("Drives X and Y stages back to their original positions and zeros their position values");
      getContentPane().add(setOriginButton);
      springLayout.putConstraint(SpringLayout.NORTH, setOriginButton, northConstraint, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, setOriginButton, northConstraint+=buttonHeight, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, setOriginButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, setOriginButton, 0, SpringLayout.WEST, markButton);






      final JButton removeAllButton = new JButton();
      removeAllButton.setFont(new Font("Arial", Font.PLAIN, 10));
      removeAllButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            int ret = JOptionPane.showConfirmDialog(null, "Are you sure you want to erase\nall positions from the position list?", "Clear all positions?", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.YES_OPTION)
               clearAllPositions();
         }
      });
      removeAllButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/delete.png"));
      removeAllButton.setText("Clear All");
      removeAllButton.setToolTipText("Removes all positions from list");
      getContentPane().add(removeAllButton);
      springLayout.putConstraint(SpringLayout.NORTH, removeAllButton, northConstraint, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, removeAllButton, northConstraint+=buttonHeight, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, removeAllButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, removeAllButton, 0, SpringLayout.WEST, markButton);

  

      final JButton closeButton = new JButton();
      closeButton.setFont(new Font("Arial", Font.PLAIN, 10));
      closeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            savePosition();
            dispose();
         }
      });
      closeButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/empty.png"));
      closeButton.setText("Close");
      getContentPane().add(closeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, closeButton, 0, SpringLayout.SOUTH, axisPane);
      springLayout.putConstraint(SpringLayout.NORTH, closeButton, -27, SpringLayout.SOUTH, axisPane);
      springLayout.putConstraint(SpringLayout.EAST, closeButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, closeButton, 0, SpringLayout.WEST, markButton);

      final JButton tileButton = new JButton();
      tileButton.setFont(new Font("Arial", Font.PLAIN, 10));
      tileButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            showCreateTileDlg();
         }
      });
      tileButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/empty.png"));
      tileButton.setText("Create Grid");
      tileButton.setToolTipText("Open new window to create grid of equally spaced positions");
      getContentPane().add(tileButton);
      springLayout.putConstraint(SpringLayout.SOUTH, tileButton, -1, SpringLayout.NORTH, closeButton);
      springLayout.putConstraint(SpringLayout.NORTH, tileButton, -27, SpringLayout.NORTH, closeButton);
      springLayout.putConstraint(SpringLayout.EAST, tileButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, tileButton, 0, SpringLayout.WEST, markButton);

      final JButton saveAsButton = new JButton();
      saveAsButton.setFont(new Font("Arial", Font.PLAIN, 10));
      saveAsButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            savePositionListAs();
         }
      });
      saveAsButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/empty.png"));
      saveAsButton.setText("Save As...");
      saveAsButton.setToolTipText("Save position list as");
      getContentPane().add(saveAsButton);
      springLayout.putConstraint(SpringLayout.SOUTH, saveAsButton, -28, SpringLayout.NORTH, closeButton);
      springLayout.putConstraint(SpringLayout.NORTH, saveAsButton, -55, SpringLayout.NORTH, closeButton);
      springLayout.putConstraint(SpringLayout.EAST, saveAsButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, saveAsButton, 0, SpringLayout.WEST, markButton);

      final JButton loadButton = new JButton();
      loadButton.setFont(new Font("Arial", Font.PLAIN, 10));
      loadButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            loadPositionList();
         }
      });
      loadButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/empty.png"));
      loadButton.setText("Load...");
      loadButton.setToolTipText("Load position list");
      getContentPane().add(loadButton);
      springLayout.putConstraint(SpringLayout.SOUTH, loadButton, -56, SpringLayout.NORTH, closeButton);
      springLayout.putConstraint(SpringLayout.NORTH, loadButton, -83, SpringLayout.NORTH, closeButton);
      springLayout.putConstraint(SpringLayout.EAST, loadButton, 0, SpringLayout.EAST, markButton);
      springLayout.putConstraint(SpringLayout.WEST, loadButton, 0, SpringLayout.WEST, markButton);

   }

   protected void updateMarkButtonText() {
      PosTableModel tm = (PosTableModel) posTable_.getModel();
      MultiStagePosition msp = tm.getPositionList().getPosition(posTable_.getSelectedRow() - 1);
      if (null == msp) {
         markButtonRef.setText("Mark");
      } else {
         markButtonRef.setText("Replace");
      }

   }
   


public void addPosition(MultiStagePosition msp, String label) {
      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      msp.setLabel(label);
      ptm.getPositionList().addPosition(msp);
      ptm.fireTableDataChanged();
   }

   public void addPosition(MultiStagePosition msp) {
      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      msp.setLabel(ptm.getPositionList().generateLabel());
      ptm.getPositionList().addPosition(msp);
      ptm.fireTableDataChanged();
   }

   protected boolean savePositionListAs() {
      File f = FileDialogs.save(this, "Save the position list", POSITION_LIST_FILE);
      if (f != null) {
         curFile_ = f;
         try {
            getPositionList().save(curFile_.getAbsolutePath());
            posListDir_ = curFile_.getParent();
         } catch (Exception e) {
            handleError(e);
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
            handleError(e);
         } finally {
            updatePositionData();            
         }
      }
   }

   public void updatePositionData() {
      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      ptm.fireTableDataChanged();
   }
   
   public void rebuildAxisList() {
      axisList_ = new AxisList();
      AxisTableModel axm = (AxisTableModel)axisTable_.getModel();
      axm.fireTableDataChanged();
   }

   public void setPositionList(PositionList pl) {
      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      ptm.setData(pl);
      ptm.fireTableDataChanged();
   }

   protected void goToCurrentPosition() {
      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      MultiStagePosition msp = ptm.getPositionList().getPosition(posTable_.getSelectedRow() - 1);
      if (msp == null)
         return;

      try {
         MultiStagePosition.goToPosition(msp, core_);
      } catch (Exception e) {
         handleError(e);
      }
      refreshCurrentPosition();
   }

   protected void clearAllPositions() {
      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      ptm.getPositionList().clearAllPositions();
      ptm.fireTableDataChanged();
   }

   
   protected void incrementOrderOfSelectedPosition(int direction) {
      PosTableModel ptm = (PosTableModel) posTable_.getModel();
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
   
   
   protected void removeCurrentPosition() {
      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      ptm.getPositionList().removePosition(posTable_.getSelectedRow() - 1);
      ptm.fireTableDataChanged();
   }

   /**
    * Store current xyPosition.
    * Use data collected in refreshCurrentPosition()
    */
   public void markPosition() {
      refreshCurrentPosition();
      MultiStagePosition msp = new MultiStagePosition();
      msp = curMsp_;

      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      MultiStagePosition selMsp = ptm.getPositionList().getPosition(posTable_.getSelectedRow() -1);

      int selectedRow = 0;
      if (selMsp == null) {
         msp.setLabel(ptm.getPositionList().generateLabel());
         ptm.getPositionList().addPosition(msp);
         ptm.fireTableDataChanged();
      } else { // replace instead of add 
         msp.setLabel(ptm.getPositionList().getPosition(posTable_.getSelectedRow() - 1).getLabel() );
         selectedRow = posTable_.getSelectedRow();
         ptm.getPositionList().replacePosition(posTable_.getSelectedRow() -1, msp);
         ptm.fireTableCellUpdated(selectedRow, 1);
         // Not sure why this is here as we undo the selecion after this functions exits...
         posTable_.setRowSelectionInterval(selectedRow, selectedRow);
      }
   }

   /**
    * Editor component for the position list table
    */
   public class CellEditor extends AbstractCellEditor implements TableCellEditor, FocusListener {
      private static final long serialVersionUID = 3L;
      // This is the component that will handle editing of the cell's value
      JTextField text_ = new JTextField();
      int editingCol_;

      public CellEditor() {
         super();
         text_.addFocusListener(this);
      }

      public void focusLost(FocusEvent e) {
         fireEditingStopped();
      }

      public void focusGained(FocusEvent e) {
 
      }

      // This method is called when a cell value is edited by the user.
      public Component getTableCellEditorComponent(JTable table, Object value,
            boolean isSelected, int rowIndex, int colIndex) {

        editingCol_ = colIndex;

         // Configure the component with the specified value
         if (colIndex == 0) {
            text_.setText((String)value);
            return text_;
         }

         return null;
      }
                                                                             
      // This method is called when editing is completed.
      // It must return the new value to be stored in the cell. 
      public Object getCellEditorValue() {
         if (editingCol_ == 0) {
               return text_.getText();
         }
         return null;
      }
   }
 
   /**
    * Update display of the current xy position.
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
         handleError(e);
      }

      curMsp_ = msp;

      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      int selectedRow = posTable_.getSelectedRow();
      ptm.fireTableCellUpdated(0, 1);
      if (selectedRow > 0)
         posTable_.setRowSelectionInterval(selectedRow, selectedRow);
   }

   public boolean useDrive(String drive) {
      return axisList_.use(drive);
   }

   protected void showCreateTileDlg() {
      if (tileCreatorDlg_ == null) {
         tileCreatorDlg_ = new TileCreatorDlg(core_, opts_, this);
         gui_.addMMBackgroundListener(tileCreatorDlg_);
      }
      tileCreatorDlg_.setBackground(guiColors_.background.get(opts_.displayBackground_));
      tileCreatorDlg_.setVisible(true);
   }


   private PositionList getPositionList() {
      PosTableModel ptm = (PosTableModel)posTable_.getModel();
      return ptm.getPositionList();

   }

   private void handleError(Exception e) {
      ReportingUtils.showError(e);
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
         handleError(e);
      }

   }

   class StopCalThread extends Thread{
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
               sct=null;

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

               busy = core_.deviceBusy(deviceName);

               if (isInterrupted())return;
               busy = core_.deviceBusy(deviceName);
               while (busy){
                  if (isInterrupted())return;
                  Thread.sleep(100);
                  if (isInterrupted())return;
                  busy = core_.deviceBusy(deviceName);
               }

               bt.interrupt();
               bt=null;

            }           
         } catch (InterruptedException e) { ReportingUtils.logError(e);}
         catch (Exception e) {
            handleError(e);
         }
      }
   }

   class CalThread extends Thread{

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
            busy = core_.deviceBusy(deviceName);

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
            bt = null;

         } catch (InterruptedException e) {
            ReportingUtils.logError(e);
         } catch (Exception e) {
            handleError(e);
         }
      }
   }

   class BackThread extends Thread{

      MMDialog d;

      public void setPara(MMDialog d) {
         this.d = d;
      }     
      @Override
      public void run() {
         JOptionPane.showMessageDialog(d, "Going back to the original position!");              
      }
   }      

   /*
    * Implementation of MouseListener
    * Sole purpose is to be able to unselect rows in the positionlist table
    */
   public void mousePressed (MouseEvent e) {}
   public void mouseReleased (MouseEvent e) {}
   public void mouseEntered (MouseEvent e) {}
   public void mouseExited (MouseEvent e) {}
   /*
    * This event is fired after the table sets its selection
    * Remember where was clicked previously so as to allow for toggling the selected row
    */
   private static int lastRowClicked_;
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
}

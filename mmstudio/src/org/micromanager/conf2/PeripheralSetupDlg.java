package org.micromanager.conf2;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import org.micromanager.conf2.PeripheralDevicesPage.DeviceTable_TableModel;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.ReportingUtils;
//import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JLabel;

public class PeripheralSetupDlg extends MMDialog {

   private static final long serialVersionUID = 1L;
   private static final int HUBCOLUMN = 0;
   private static final int NAMECOLUMN = 1;
   private static final int ADAPTERCOLUMN = 2;
   private static final int DESCRIPTIONCOLUMN = 3;
   private static final int SELECTIONCOLUMN = 4;

   private class DeviceTable_TableModel extends AbstractTableModel {

      private static final long serialVersionUID = 1L;
      public final String[] COLUMN_NAMES = new String[]{
         "Hub",
         "Name",
         "Adapter/Library",
         "Description",
         "Selected"
      };
      MicroscopeModel model_;
      Device devices_[];
      Vector<Device> peripheralDevices_;
      Vector<Boolean> selected_;
      Vector<String> masterDevices_;

      public DeviceTable_TableModel(MicroscopeModel model) {
         peripheralDevices_ = new Vector<Device>();
         selected_ = new Vector<Boolean>();
         masterDevices_ = new Vector<String>();
         setMicroscopeModel(model);
      }

      public void setMicroscopeModel(MicroscopeModel mod) {
         model_ = mod;
         rebuild();
      }

      public int getRowCount() {
         return peripheralDevices_.size();
      }

      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }

      @Override
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }

      @Override
      public Class getColumnClass(int c) {
         Class ret = String.class;
         if (SELECTIONCOLUMN == c) {
            ret = Boolean.class;
         }
         return ret;
      }

      public Object getValueAt(int rowIndex, int columnIndex) {

         if (HUBCOLUMN == columnIndex) {
            return masterDevices_.get(rowIndex);
         } else if (columnIndex == NAMECOLUMN) {
            return peripheralDevices_.get(rowIndex).getName();
         } else if (columnIndex == ADAPTERCOLUMN) {
            return new String(peripheralDevices_.get(rowIndex).getAdapterName() + "/" + peripheralDevices_.get(rowIndex).getLibrary());
         } else if (columnIndex == DESCRIPTIONCOLUMN) {
            return peripheralDevices_.get(rowIndex).getDescription();
         } else if (SELECTIONCOLUMN == columnIndex) {
            return selected_.get(rowIndex);
         } else {
            return null;
         }
      }

      @Override
      public void setValueAt(Object value, int row, int col) {
         switch (col) {
            case HUBCOLUMN:
               break;
            case NAMECOLUMN: {
               String n = (String) value;
               String o = peripheralDevices_.get(row).getName();
               try {

                  //  NOT YET!  model_.changeDeviceName(o, n);
                  fireTableCellUpdated(row, col);
               } catch (Exception e) {
                  handleError(e.getMessage());
               }
            }
            break;
            case ADAPTERCOLUMN:
               break;
            case DESCRIPTIONCOLUMN:
               break;
            case SELECTIONCOLUMN:
               selected_.set(row, (Boolean) value);
               break;
         }
      }

      @Override
      public boolean isCellEditable(int nRow, int nCol) {
         boolean ret = false;
         switch (nCol) {
            case HUBCOLUMN:
               break;
            case NAMECOLUMN:
               ret = true;
               break;
            case ADAPTERCOLUMN:
               break;
            case DESCRIPTIONCOLUMN:
               break;
            case SELECTIONCOLUMN:
               ret = true;
               break;
         }
         return ret;
      }

      public void refresh() {
         devices_ = model_.getDevices();
         this.fireTableDataChanged();
      }

      Vector<Device> getPeripheralDevices() {
         return peripheralDevices_;
      }

      ;

      Vector<Boolean> getSelected() {
         return selected_;
      }

      ;

      Vector<String> getMasterDevices() {
         return masterDevices_;
      }

      public void rebuild() {
         devices_ = model_.getDevices();
         masterDevices_.clear();
         peripheralDevices_.clear();
         selected_.clear();

         for (Device d : devices_) {
            if (!d.getName().equals("Core")) {

               // device "discovery" happens here
               StrVector installed = core_.getInstalledDevices(d.getName());
               // end of discovery

               if (0 < installed.size()) {
                  for (int i=0; i<installed.size(); i++) {                        
                     try {
                        if (model_.findDevice(installed.get(i)) == null)
                        {
                           String descr = model_.getDeviceDescription(d.getLibrary(), installed.get(i));
                           Device newDev = new Device(installed.get(i), d.getLibrary(), installed.get(i), descr);   
                           selected_.add(false);
                           masterDevices_.add(d.getName());
                           peripheralDevices_.add(newDev);
                        }
                     } catch (Exception e) {
                        ReportingUtils.logError(e.getMessage());
                     }
                  }
               }
            }
         }
      }
   }
   private JTable deviceTable_;
   private JScrollPane scrollPane_;
   private CMMCore core_;
   private MicroscopeModel model_;
   private String hub_;
   private final JPanel contentPanel = new JPanel();   

   public PeripheralSetupDlg(MicroscopeModel mod, CMMCore c, String hub, Vector<Device> per) {
      setTitle("Peripheral Devices Setup");
      setBounds(100, 100, 479, 353);
      //setModalityType(ModalityType.APPLICATION_MODAL);
      setModal(true);
      setResizable(false);
      hub_ = hub;
      core_ = c;
      model_ = mod;

      String hubColumn = Integer.toString(HUBCOLUMN + 1);
      String nameColumn = Integer.toString(NAMECOLUMN + 1);

      getContentPane().setLayout(new BorderLayout());
      contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
      getContentPane().add(contentPanel, BorderLayout.CENTER);
      contentPanel.setLayout(null);
      
      scrollPane_ = new JScrollPane();
      scrollPane_.setBounds(10, 36, 453, 236);
      contentPanel.add(scrollPane_);

      deviceTable_ = new JTable();
      deviceTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scrollPane_.setViewportView(deviceTable_);
      
      JLabel lblNewLabel = new JLabel("HUB (parent device):");
      lblNewLabel.setBounds(10, 11, 111, 14);
      contentPanel.add(lblNewLabel);
      
      JLabel lblParentDev = new JLabel(hub_);
      lblParentDev.setBounds(131, 11, 332, 14);
      contentPanel.add(lblParentDev);
      
      {
         JPanel buttonPane = new JPanel();
         buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
         getContentPane().add(buttonPane, BorderLayout.SOUTH);
         {
            JButton okButton = new JButton("OK");
            okButton.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  onOK();
               }
            });
            okButton.setActionCommand("OK");
            buttonPane.add(okButton);
            getRootPane().setDefaultButton(okButton);
         }
         {
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  onCancel();
               }
            });
            cancelButton.setActionCommand("Cancel");
            buttonPane.add(cancelButton);
         }
      }
      addWindowListener(new WindowAdapter() {
         public void windowClosing(final WindowEvent e) {
            savePosition();
         }
      });

      Rectangle r = getBounds();
      loadPosition(r.x, r.y);
      
      rebuildTable();
   }

   public void handleError(String message) {
      JOptionPane.showMessageDialog(this, message);
   }

   protected void removeDevice() {
      int sel = deviceTable_.getSelectedRow();
      if (sel < 0) {
         return;
      }
      String devName = (String) deviceTable_.getValueAt(sel, 0);

      if (devName.contentEquals(new StringBuffer().append(MMCoreJ.getG_Keyword_CoreDevice()))) {
         handleError(MMCoreJ.getG_Keyword_CoreDevice() + " device can't be removed!");
         return;
      }

      model_.removeDevice(devName);
      rebuildTable();
   }

   public void rebuildTable() {
      TableModel tm = deviceTable_.getModel();
      DeviceTable_TableModel tmd;
      if (tm instanceof DeviceTable_TableModel) {
         tmd = (DeviceTable_TableModel) deviceTable_.getModel();
         tmd.refresh();
      } else {
         tmd = new DeviceTable_TableModel(model_);
         deviceTable_.setModel(tmd);
      }
      tmd.fireTableStructureChanged();
      tmd.fireTableDataChanged();
   }

   public void refresh() {
      rebuildTable();
   }

   public void onOK() {
      try {
         DeviceTable_TableModel tmd = (DeviceTable_TableModel) deviceTable_.getModel();

         Vector<Device> pd = tmd.getPeripheralDevices();
         Vector<String> hubs = tmd.getMasterDevices();
         Vector<Boolean> sel = tmd.getSelected();
         model_.AddSelectedPeripherals(core_, pd, hubs, sel);
         model_.loadDeviceDataFromHardware(core_);
         tmd.rebuild();
      } catch (Exception e) {
         handleError(e.getMessage());
      } finally {
         dispose();
      }
   }
   
   public void onCancel() {
      dispose();
   }
}

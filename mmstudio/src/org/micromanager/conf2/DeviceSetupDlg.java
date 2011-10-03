package org.micromanager.conf2;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import mmcorej.CMMCore;

import org.micromanager.utils.MMDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Dialog.ModalityType;

public class DeviceSetupDlg extends MMDialog {

   private final JPanel contentPanel = new JPanel();
   private CMMCore core;
   private String name;
   private String lib;
   private MicroscopeModel model;
   private JTextField devLabel;
   private JButton btnInitialize;
   private JButton btnLoad;

   /**
    * Create the dialog.
    */
   public DeviceSetupDlg(MicroscopeModel mod, CMMCore c, String library, String devName) {
      setModalityType(ModalityType.APPLICATION_MODAL);
      setModal(true);
      setBounds(100, 100, 450, 300);
      model = mod;
      lib = library;
      name = devName;
      core = c;
      
      getContentPane().setLayout(new BorderLayout());
      contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
      getContentPane().add(contentPanel, BorderLayout.CENTER);
      contentPanel.setLayout(null);
      {
         JLabel lblNewLabel = new JLabel("Label");
         lblNewLabel.setBounds(10, 11, 54, 14);
         contentPanel.add(lblNewLabel);
      }
      
      devLabel = new JTextField();
      devLabel.setBounds(56, 8, 86, 20);
      contentPanel.add(devLabel);
      devLabel.setColumns(10);
      
      btnLoad = new JButton("Load");
      btnLoad.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            loadDevice();
         }
      });
      btnLoad.setBounds(182, 7, 89, 23);
      contentPanel.add(btnLoad);
      
      btnInitialize = new JButton("Initialize");
      btnInitialize.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            initializeDevice();
         }
      });
      btnInitialize.setBounds(292, 7, 89, 23);
      contentPanel.add(btnInitialize);
      btnInitialize.setEnabled(false);
      
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
      
      setTitle("Device: " + name + " | Library: " + lib);
      devLabel.setText("NewLabel");
   }

   protected void onCancel() {
      // TODO Auto-generated method stub
      
   }

   protected void onOK() {
      // TODO Auto-generated method stub
      
   }

   private void loadDevice() {
      // attempt to load device
      try {
         Device d = model.findDevice(devLabel.getText());
         if (d == null) {
            core.loadDevice(devLabel.getText(), lib, name);
            btnLoad.setEnabled(false);
            devLabel.setEditable(false);
            btnInitialize.setEnabled(true);
            Device dev = new Device(devLabel.getText(), lib, name);
            dev.loadDataFromHardware(core);
            model.addDevice(dev);
            
            // TODO: update property list
            
         } else {
            showMessage("Device label " + devLabel + " already in use.");
         }
      } catch (Exception e1) {
         showMessage(e1.getMessage());
      }
   }
   
   private void initializeDevice() {
      try {
         core.initializeDevice(devLabel.getText());
         btnInitialize.setEnabled(false);
      } catch (Exception e) {
         showMessage(e.getMessage());
      }
   }
   
   private void showMessage(String msg) {
      JOptionPane.showMessageDialog(this, msg);
   }
}
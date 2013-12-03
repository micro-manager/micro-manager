
package org.micromanager.asidispim;

import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author nico
 */
public class ASIdiSPIMFrame extends javax.swing.JFrame 
      implements MMListenerInterface {
   
   private ScriptInterface gui_;
   private CMMCore core_;
   
   /**
    * Creates the ASIdiSPIM plugin frame
    * @param gui - Micro-Manager script interface
    */
   public ASIdiSPIMFrame(ScriptInterface gui)  {
      gui_ = gui;
      core_ = gui_.getMMCore();
      
      JTabbedPane tabbedPane = new JTabbedPane();
           
      JComponent p1 = new JPanel(new MigLayout("", 
              "[]15[100px]15[100px]",
              "[]15"));
      p1.add(new JLabel(" ", null, JLabel.CENTER));
      p1.add(new JLabel("Side A", null, JLabel.CENTER));
      p1.add(new JLabel("Side B", null, JLabel.CENTER), "wrap");
      
      p1.add(new JLabel("Camera: ", null, JLabel.RIGHT));
      p1.add(makeDeviceBox(mmcorej.DeviceType.CameraDevice));
      p1.add(makeDeviceBox(mmcorej.DeviceType.CameraDevice), "wrap");
      
      p1.add(new JLabel("Imaging Piezo: ", null, JLabel.RIGHT));
      p1.add(makeDeviceBox(mmcorej.DeviceType.StageDevice));
      p1.add(makeDeviceBox(mmcorej.DeviceType.StageDevice), "wrap");
      
      JComponent p2 = makeTextPanel("hello");
      
      tabbedPane.addTab("Devices", p1);
      tabbedPane.addTab("Allignment", p2);
      
      add(tabbedPane);
      
      pack();
   }
   
   
   private JComboBox makeDeviceBox(mmcorej.DeviceType deviceType) {
      StrVector strvDevices = core_.getLoadedDevicesOfType(deviceType);
      String[] devices = strvDevices.toArray();
      
      JComboBox deviceBox = new JComboBox(devices);
      
      return deviceBox;
   }
   
   protected JComponent makeTextPanel(String text) {
        JPanel panel = new JPanel(false);
        JLabel filler = new JLabel(text);
        filler.setHorizontalAlignment(JLabel.CENTER);
        panel.setLayout(new GridLayout(1, 1));
        panel.add(filler);
        return panel;
    }
   
   /*
   private TabItem createTabPanel(TabFolder parent, String text, Layout lm)      
   {
      Composite panel = new Composite(parent, DOUBLE_BUFFER);
 //                panel.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
      TabItem tab = new TabItem(parent, DOUBLE_BUFFER);
      tab.setControl(panel);
      tab.setText(text);
      if (lm != null)
         panel.setLayout(lm);
 //                configureActiveComponet(panel);
      return tab;
   }
   */
   /*
   private Label createLabel(Object parent, String text, Object layout, int style) {
      final Label b = new Label(getComposite(parent), style | DOUBLE_BUFFER);
      b.setText(text);
      b.setLayoutData(layout != null ? layout : text);
      //                b.setAlignment();
      //                configureActiveComponet(b);
      return b;
   }
   
   public Composite getComposite(Object c) {
      if (c instanceof Control)
         return (Composite) c;
      
      return (Composite) ((TabItem) c).getControl();
   }
*/
   public void propertiesChangedAlert() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void propertyChangedAlert(String device, String property, String value) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void configGroupChangedAlert(String groupName, String newConfig) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void systemConfigurationLoaded() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void pixelSizeChangedAlert(double newPixelSizeUm) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void stagePositionChangedAlert(String deviceName, double pos) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void xyStagePositionChanged(String deviceName, double xPos, double yPos) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void exposureChanged(String cameraName, double newExposureTime) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }
}

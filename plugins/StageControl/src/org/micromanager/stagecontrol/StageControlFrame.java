
/**
 * StageControlFrame.java
 *
 * Created on Aug 19, 2010, 10:04:49 PM
 * Nico Stuurman, copyright UCSF, 2010
 * 
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.stagecontrol;

import mmcorej.CMMCore;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import java.util.prefs.Preferences;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;

/**
 *
 * @author nico
 */
public class StageControlFrame extends javax.swing.JFrame {
   private final ScriptInterface gui_;
   private final CMMCore core_;
   private Preferences prefs_;

   private double smallMovement_ = 1.0;
   private double mediumMovement_ = 10.0;
   private double largeMovement_ = 100.0;
   private Map<String, Double> smallMovementZ_ = new HashMap();
   private Map<String, Double> mediumMovementZ_ = new HashMap();
   private NumberFormat nf_;
   private String currentZDrive_ = "";
   private boolean initialized_ = false;

   private int frameXPos_ = 100;
   private int frameYPos_ = 100;

   private static final String FRAMEXPOS = "FRAMEXPOS";
   private static final String FRAMEYPOS = "FRAMEYPOS";
   private static final String SMALLMOVEMENT = "SMALLMOVEMENT";
   private static final String MEDIUMMOVEMENT = "MEDIUMMOVEMENT";
   private static final String LARGEMOVEMENT = "LARGEMOVEMENT";
   private static final String SMALLMOVEMENTZ = "SMALLMOVEMENTZ";
   private static final String MEDIUMMOVEMENTZ = "MEDIUMMOVEMENTZ";
   private static final String CURRENTZDRIVE = "CURRENTZDRIVE";



   /**
    * Creates new form StageControlFrame
    */
   public StageControlFrame(ScriptInterface gui) {
      gui_ = gui;
      core_ = gui_.getMMCore();
      nf_ = NumberFormat.getInstance();
      prefs_ = Preferences.userNodeForPackage(this.getClass());

      // Read values from PREFS
      frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
      frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);
      double pixelSize = core_.getPixelSizeUm();
      long nrPixelsX = core_.getImageWidth();
      if (pixelSize != 0) {
         smallMovement_ = pixelSize;
         mediumMovement_ = pixelSize * nrPixelsX * 0.1;
         largeMovement_ = pixelSize * nrPixelsX;
      }
      smallMovement_ = prefs_.getDouble(SMALLMOVEMENT, smallMovement_);
      mediumMovement_ = prefs_.getDouble(MEDIUMMOVEMENT, mediumMovement_);
      largeMovement_ = prefs_.getDouble(LARGEMOVEMENT, largeMovement_);
      currentZDrive_ = prefs_.get(CURRENTZDRIVE, currentZDrive_);

      initComponents();
      
      zDriveGUIElements_ = new javax.swing.JComponent[] {
         jLabel8, jLabel9, jLabel10, zDriveSelect_, mediumZUpButton_, 
         smallZUpButton_, mediumZDownButton_, smallZDownButton_, zPositionLabel_,
         smallZTextField_, mediumZTextField_
      };

      setLocation(frameXPos_, frameYPos_);

   }
   
   /**
    * Initialized GUI components based on current hardware configuration
    * Can be called at any time to adjust display (for instance after hardware
    * configuration change
    */
   public final void initialize() {
      smallXYTextField_.setText(nf_.format(smallMovement_));
      mediumXYTextField_.setText(nf_.format(mediumMovement_));
      largeXYTextField_.setText(nf_.format(largeMovement_));

      StrVector zDrives = core_.getLoadedDevicesOfType(DeviceType.StageDevice);

      if (zDrives.isEmpty()) {
         for (javax.swing.JComponent guiElement : zDriveGUIElements_) {
            guiElement.setVisible(false);
         }
         this.setSize(287, 369);
         
         return;
      } else {
         for (javax.swing.JComponent guiElement : zDriveGUIElements_) {
            guiElement.setVisible(true);
         }
         this.setSize(387, 369);
      }

      if (zDrives.size() == 1)
         zDriveSelect_.setVisible(false);
      
      boolean zDriveFound = false;
      for (int i = 0; i < zDrives.size(); i++) {
         String drive = zDrives.get(i);
         smallMovementZ_.put(drive, prefs_.getDouble(SMALLMOVEMENTZ + drive, 1.0) );
         mediumMovementZ_.put(drive, prefs_.getDouble(MEDIUMMOVEMENTZ + drive, 10.0) );
         zDriveSelect_.addItem(drive);
         if (currentZDrive_.equals(zDrives.get(i))) {
            zDriveFound = true;
         }
      }
      if (!zDriveFound) {
         currentZDrive_ = zDrives.get(0);
      }
      updateZMovements();
      initialized_ = true;
   }
   
   private void updateZMovements() {
      smallZTextField_.setText(nf_.format(smallMovementZ_.get(currentZDrive_)));
      mediumZTextField_.setText(nf_.format(mediumMovementZ_.get(currentZDrive_)));
   }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
   //@SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      jPanel1 = new javax.swing.JPanel();
      smallLeftButton_ = new javax.swing.JButton();
      smallXYDownButton_ = new javax.swing.JButton();
      largeLeftButton_ = new javax.swing.JButton();
      mediumLeftButton_ = new javax.swing.JButton();
      smallXYUpButton_ = new javax.swing.JButton();
      mediumXYDownButton_ = new javax.swing.JButton();
      largXYUpButton_ = new javax.swing.JButton();
      smallRightButton_ = new javax.swing.JButton();
      largeXYDownButton_ = new javax.swing.JButton();
      largeRightButton_ = new javax.swing.JButton();
      mediumXYUpButton_ = new javax.swing.JButton();
      mediumRightButton_ = new javax.swing.JButton();
      smallXYTextField_ = new javax.swing.JTextField();
      mediumXYTextField_ = new javax.swing.JTextField();
      largeXYTextField_ = new javax.swing.JTextField();
      jLabel1 = new javax.swing.JLabel();
      jLabel2 = new javax.swing.JLabel();
      jLabel3 = new javax.swing.JLabel();
      jLabel4 = new javax.swing.JLabel();
      jLabel5 = new javax.swing.JLabel();
      jLabel6 = new javax.swing.JLabel();
      get1PixellXYButton_ = new javax.swing.JButton();
      getPartFieldButton_ = new javax.swing.JButton();
      getFieldButton_ = new javax.swing.JButton();
      jLabel7 = new javax.swing.JLabel();
      mediumZUpButton_ = new javax.swing.JButton();
      smallZUpButton_ = new javax.swing.JButton();
      smallZDownButton_ = new javax.swing.JButton();
      mediumZDownButton_ = new javax.swing.JButton();
      smallZTextField_ = new javax.swing.JTextField();
      mediumZTextField_ = new javax.swing.JTextField();
      jLabel8 = new javax.swing.JLabel();
      jLabel9 = new javax.swing.JLabel();
      jLabel10 = new javax.swing.JLabel();
      zDriveSelect_ = new javax.swing.JComboBox();
      zPositionLabel_ = new javax.swing.JLabel();
      jButton20 = new javax.swing.JButton();
      jButton21 = new javax.swing.JButton();
      jButton22 = new javax.swing.JButton();

      setTitle("Stage Control");
      setLocationByPlatform(true);
      setResizable(false);
      addWindowListener(new java.awt.event.WindowAdapter() {
         public void windowClosing(java.awt.event.WindowEvent evt) {
            onWindowClosing(evt);
         }
      });

      smallLeftButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-sl.png"))); // NOI18N
      smallLeftButton_.setBorderPainted(false);
      smallLeftButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-slp.png"))); // NOI18N
      smallLeftButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            smallLeftButton_ActionPerformed(evt);
         }
      });

      smallXYDownButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-sd.png"))); // NOI18N
      smallXYDownButton_.setBorderPainted(false);
      smallXYDownButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-sdp.png"))); // NOI18N
      smallXYDownButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            smallXYDownButton_ActionPerformed(evt);
         }
      });

      largeLeftButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-tl.png"))); // NOI18N
      largeLeftButton_.setBorderPainted(false);
      largeLeftButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-tlp.png"))); // NOI18N
      largeLeftButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            largeLeftButton_ActionPerformed(evt);
         }
      });

      mediumLeftButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-dl.png"))); // NOI18N
      mediumLeftButton_.setBorderPainted(false);
      mediumLeftButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-dlp.png"))); // NOI18N
      mediumLeftButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mediumLeftButton_ActionPerformed(evt);
         }
      });

      smallXYUpButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-su.png"))); // NOI18N
      smallXYUpButton_.setBorderPainted(false);
      smallXYUpButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-sup.png"))); // NOI18N
      smallXYUpButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            smallXYUpButton_ActionPerformed(evt);
         }
      });

      mediumXYDownButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-dd.png"))); // NOI18N
      mediumXYDownButton_.setBorderPainted(false);
      mediumXYDownButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-ddp.png"))); // NOI18N
      mediumXYDownButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mediumXYDownButton_ActionPerformed(evt);
         }
      });

      largXYUpButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-tu.png"))); // NOI18N
      largXYUpButton_.setBorderPainted(false);
      largXYUpButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-tup.png"))); // NOI18N
      largXYUpButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            largXYUpButton_ActionPerformed(evt);
         }
      });

      smallRightButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-sr.png"))); // NOI18N
      smallRightButton_.setBorderPainted(false);
      smallRightButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-srp.png"))); // NOI18N
      smallRightButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            smallRightButton_ActionPerformed(evt);
         }
      });

      largeXYDownButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-td.png"))); // NOI18N
      largeXYDownButton_.setBorderPainted(false);
      largeXYDownButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-tdp.png"))); // NOI18N
      largeXYDownButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            largeXYDownButton_ActionPerformed(evt);
         }
      });

      largeRightButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-tr.png"))); // NOI18N
      largeRightButton_.setBorderPainted(false);
      largeRightButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-trp.png"))); // NOI18N
      largeRightButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            largeRightButton_ActionPerformed(evt);
         }
      });

      mediumXYUpButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-du.png"))); // NOI18N
      mediumXYUpButton_.setBorderPainted(false);
      mediumXYUpButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-dup.png"))); // NOI18N
      mediumXYUpButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mediumXYUpButton_ActionPerformed(evt);
         }
      });

      mediumRightButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-dr.png"))); // NOI18N
      mediumRightButton_.setBorderPainted(false);
      mediumRightButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-drp.png"))); // NOI18N
      mediumRightButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mediumRightButton_ActionPerformed(evt);
         }
      });

      org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
         jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(jPanel1Layout.createSequentialGroup()
            .addContainerGap()
            .add(largeLeftButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
            .add(mediumLeftButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
               .add(largeXYDownButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
               .add(org.jdesktop.layout.GroupLayout.LEADING, mediumXYDownButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
               .add(smallXYDownButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
               .add(org.jdesktop.layout.GroupLayout.LEADING, largXYUpButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
               .add(org.jdesktop.layout.GroupLayout.LEADING, mediumXYUpButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
               .add(smallXYUpButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
               .add(jPanel1Layout.createSequentialGroup()
                  .add(smallLeftButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .add(28, 28, 28)
                  .add(smallRightButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(mediumRightButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
            .add(largeRightButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addContainerGap())
      );
      jPanel1Layout.setVerticalGroup(
         jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
            .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(largXYUpButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
            .add(mediumXYUpButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 24, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(smallXYUpButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(smallRightButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(smallLeftButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(mediumLeftButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(largeLeftButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(mediumRightButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(largeRightButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(smallXYDownButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
            .add(mediumXYDownButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 24, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(largeXYDownButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 33, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
      );

      smallXYTextField_.setHorizontalAlignment(javax.swing.JTextField.LEFT);
      smallXYTextField_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            smallXYTextField_ActionPerformed(evt);
         }
      });
      smallXYTextField_.addFocusListener(new java.awt.event.FocusAdapter() {
         public void focusLost(java.awt.event.FocusEvent evt) {
            focusLostHandlerJTF1(evt);
         }
      });

      mediumXYTextField_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mediumXYTextField_ActionPerformed(evt);
         }
      });
      mediumXYTextField_.addFocusListener(new java.awt.event.FocusAdapter() {
         public void focusLost(java.awt.event.FocusEvent evt) {
            focusLostHandlerJTF2(evt);
         }
      });

      largeXYTextField_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            largeXYTextField_ActionPerformed(evt);
         }
      });
      largeXYTextField_.addFocusListener(new java.awt.event.FocusAdapter() {
         public void focusLost(java.awt.event.FocusEvent evt) {
            focusLostHandlerJTF3(evt);
         }
      });

      jLabel4.setText("<html>&#956;m</html>");

      jLabel5.setText("<html>&#956;m</html>");

      jLabel6.setText("<html>&#956;m</html>");

      get1PixellXYButton_.setFont(new java.awt.Font("Arial", 0, 10)); // NOI18N
      get1PixellXYButton_.setText("1 pixel");
      get1PixellXYButton_.setIconTextGap(6);
      get1PixellXYButton_.setMaximumSize(new java.awt.Dimension(0, 0));
      get1PixellXYButton_.setMinimumSize(new java.awt.Dimension(0, 0));
      get1PixellXYButton_.setPreferredSize(new java.awt.Dimension(35, 20));
      get1PixellXYButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            get1PixellXYButton_ActionPerformed(evt);
         }
      });

      getPartFieldButton_.setFont(new java.awt.Font("Arial", 0, 10)); // NOI18N
      getPartFieldButton_.setText("0.1 field");
      getPartFieldButton_.setMaximumSize(new java.awt.Dimension(35, 20));
      getPartFieldButton_.setMinimumSize(new java.awt.Dimension(0, 0));
      getPartFieldButton_.setPreferredSize(new java.awt.Dimension(35, 20));
      getPartFieldButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            getPartFieldButton_ActionPerformed(evt);
         }
      });

      getFieldButton_.setFont(new java.awt.Font("Arial", 0, 10)); // NOI18N
      getFieldButton_.setText("1 field");
      getFieldButton_.setMaximumSize(new java.awt.Dimension(35, 20));
      getFieldButton_.setMinimumSize(new java.awt.Dimension(0, 0));
      getFieldButton_.setPreferredSize(new java.awt.Dimension(35, 20));
      getFieldButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            getFieldButton_ActionPerformed(evt);
         }
      });

      jLabel7.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
      jLabel7.setText("XY Stage");

      mediumZUpButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-du.png"))); // NOI18N
      mediumZUpButton_.setBorderPainted(false);
      mediumZUpButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-dup.png"))); // NOI18N
      mediumZUpButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mediumZUpButton_ActionPerformed(evt);
         }
      });

      smallZUpButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-su.png"))); // NOI18N
      smallZUpButton_.setBorderPainted(false);
      smallZUpButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-sup.png"))); // NOI18N
      smallZUpButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            smallZUpButton_ActionPerformed(evt);
         }
      });

      smallZDownButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-sd.png"))); // NOI18N
      smallZDownButton_.setBorderPainted(false);
      smallZDownButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-sdp.png"))); // NOI18N
      smallZDownButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            smallZDownButton_ActionPerformed(evt);
         }
      });

      mediumZDownButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-dd.png"))); // NOI18N
      mediumZDownButton_.setBorderPainted(false);
      mediumZDownButton_.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-ddp.png"))); // NOI18N
      mediumZDownButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mediumZDownButton_ActionPerformed(evt);
         }
      });

      smallZTextField_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            smallZTextField_ActionPerformed(evt);
         }
      });
      smallZTextField_.addFocusListener(new java.awt.event.FocusAdapter() {
         public void focusLost(java.awt.event.FocusEvent evt) {
            smallZTextField_focusLostHandlerJTF1(evt);
         }
      });

      mediumZTextField_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            mediumZTextField_ActionPerformed(evt);
         }
      });
      mediumZTextField_.addFocusListener(new java.awt.event.FocusAdapter() {
         public void focusLost(java.awt.event.FocusEvent evt) {
            mediumZTextField_focusLostHandlerJTF2(evt);
         }
      });

      jLabel8.setText("<html>&#956;m</html>");

      jLabel9.setText("<html>&#956;m</html>");

      jLabel10.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
      jLabel10.setText("Z Stage");

      zDriveSelect_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            zDriveSelect_ActionPerformed(evt);
         }
      });

      zPositionLabel_.setText("0.00");

      jButton20.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-sr.png"))); // NOI18N
      jButton20.setBorderPainted(false);
      jButton20.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-srp.png"))); // NOI18N
      jButton20.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jButton20ActionPerformed(evt);
         }
      });

      jButton21.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-dr.png"))); // NOI18N
      jButton21.setBorderPainted(false);
      jButton21.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-drp.png"))); // NOI18N
      jButton21.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jButton21ActionPerformed(evt);
         }
      });

      jButton22.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-tr.png"))); // NOI18N
      jButton22.setBorderPainted(false);
      jButton22.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/stagecontrol/icons/arrowhead-trp.png"))); // NOI18N
      jButton22.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jButton22ActionPerformed(evt);
         }
      });

      org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .addContainerGap()
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
               .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                  .add(jLabel1)
                  .add(jLabel2))
               .add(jLabel3))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(layout.createSequentialGroup()
                  .add(2, 2, 2)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                     .add(jButton20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(jButton21, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(jButton22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                     .add(smallXYTextField_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 63, Short.MAX_VALUE)
                     .add(mediumXYTextField_)
                     .add(largeXYTextField_))
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                     .add(layout.createSequentialGroup()
                        .add(jLabel6, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(getFieldButton_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                     .add(layout.createSequentialGroup()
                        .add(jLabel5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(getPartFieldButton_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                     .add(layout.createSequentialGroup()
                        .add(jLabel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(get1PixellXYButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 93, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
               .add(layout.createSequentialGroup()
                  .add(73, 73, 73)
                  .add(jLabel7, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 79, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 24, Short.MAX_VALUE)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                  .add(org.jdesktop.layout.GroupLayout.LEADING, zDriveSelect_, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .add(mediumZDownButton_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .add(smallZDownButton_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .add(smallZUpButton_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .add(org.jdesktop.layout.GroupLayout.LEADING, mediumZUpButton_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .add(jLabel10, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 96, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
               .add(layout.createSequentialGroup()
                  .add(28, 28, 28)
                  .add(zPositionLabel_))
               .add(layout.createSequentialGroup()
                  .add(10, 10, 10)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                     .add(org.jdesktop.layout.GroupLayout.LEADING, mediumZTextField_, 0, 1, Short.MAX_VALUE)
                     .add(org.jdesktop.layout.GroupLayout.LEADING, smallZTextField_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 55, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                     .add(jLabel8, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(jLabel9, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
            .add(25, 25, 25))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
            .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jLabel3))
         .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
            .addContainerGap()
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                     .add(jLabel7)
                     .add(jLabel10))
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                           .add(smallXYTextField_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                           .add(jLabel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                           .add(get1PixellXYButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                           .add(layout.createSequentialGroup()
                              .add(jButton21, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                              .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                              .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                 .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                    .add(jLabel6, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(largeXYTextField_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                 .add(jButton22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                           .add(layout.createSequentialGroup()
                              .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                 .add(mediumXYTextField_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                 .add(jLabel5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                 .add(getPartFieldButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                              .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                              .add(getFieldButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                     .add(jButton20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                  .addContainerGap())
               .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                  .add(32, 32, 32)
                  .add(zDriveSelect_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .add(29, 29, 29)
                  .add(mediumZUpButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 24, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                  .add(smallZUpButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                  .add(zPositionLabel_)
                  .add(10, 10, 10)
                  .add(smallZDownButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(mediumZDownButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 24, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 45, Short.MAX_VALUE)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                     .add(smallZTextField_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(jLabel9, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                  .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                     .add(mediumZTextField_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(jLabel8, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                  .add(38, 38, 38))))
         .add(layout.createSequentialGroup()
            .add(259, 259, 259)
            .add(jLabel1)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(jLabel2)
            .addContainerGap(104, Short.MAX_VALUE))
      );

      pack();
   }// </editor-fold>//GEN-END:initComponents

   private void setRelativeXYStagePosition(double x, double y) {
      try {
         if (!core_.deviceBusy(core_.getXYStageDevice()))
            core_.setRelativeXYPosition(core_.getXYStageDevice(), x, y);
       } catch(Exception e) {
          gui_.logError(e);
       }
   }
   
   private void setRelativeStagePosition(double z) {
      try {
         if (!core_.deviceBusy(currentZDrive_)) {
            core_.setRelativePosition(currentZDrive_, z);
            updateZPosLabel();
         }
      } catch (Exception e) {
         gui_.logError(e);
      }
   }

   private void updateZPosLabel() throws Exception {
      double zPos = core_.getPosition(currentZDrive_);
      zPositionLabel_.setText(nf_.format(zPos) + " \u00B5m" );
   }

    private void smallLeftButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallLeftButton_ActionPerformed
       setRelativeXYStagePosition(-smallMovement_, 0.0);
    }//GEN-LAST:event_smallLeftButton_ActionPerformed

    private void largeLeftButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_largeLeftButton_ActionPerformed
       setRelativeXYStagePosition(-largeMovement_, 0.0);
    }//GEN-LAST:event_largeLeftButton_ActionPerformed

    private void mediumLeftButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mediumLeftButton_ActionPerformed
       setRelativeXYStagePosition(-mediumMovement_, 0.0);
    }//GEN-LAST:event_mediumLeftButton_ActionPerformed

    private void smallRightButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallRightButton_ActionPerformed
      setRelativeXYStagePosition(smallMovement_, 0.0);
    }//GEN-LAST:event_smallRightButton_ActionPerformed

    private void largeRightButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_largeRightButton_ActionPerformed
       setRelativeXYStagePosition(largeMovement_, 0.0);
    }//GEN-LAST:event_largeRightButton_ActionPerformed

    private void mediumRightButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mediumRightButton_ActionPerformed
       setRelativeXYStagePosition(mediumMovement_, 0.0);
    }//GEN-LAST:event_mediumRightButton_ActionPerformed

    private void smallXYUpButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallXYUpButton_ActionPerformed
       setRelativeXYStagePosition(0.0, -smallMovement_);
    }//GEN-LAST:event_smallXYUpButton_ActionPerformed

    private void largXYUpButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_largXYUpButton_ActionPerformed
       setRelativeXYStagePosition(0.0, -largeMovement_);
    }//GEN-LAST:event_largXYUpButton_ActionPerformed

    private void mediumXYUpButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mediumXYUpButton_ActionPerformed
       setRelativeXYStagePosition(0.0, -mediumMovement_);
    }//GEN-LAST:event_mediumXYUpButton_ActionPerformed

    private void smallXYDownButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallXYDownButton_ActionPerformed
       setRelativeXYStagePosition(0.0, smallMovement_);
    }//GEN-LAST:event_smallXYDownButton_ActionPerformed

    private void mediumXYDownButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mediumXYDownButton_ActionPerformed
       setRelativeXYStagePosition(0.0, mediumMovement_);
    }//GEN-LAST:event_mediumXYDownButton_ActionPerformed

    private void largeXYDownButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_largeXYDownButton_ActionPerformed
       setRelativeXYStagePosition(0.0, largeMovement_);
    }//GEN-LAST:event_largeXYDownButton_ActionPerformed

    private void mediumXYTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mediumXYTextField_ActionPerformed
       try {
         mediumMovement_ = nf_.parse(mediumXYTextField_.getText()).doubleValue();
         nf_.setMaximumFractionDigits(2);
         mediumXYTextField_.setText(nf_.format(mediumMovement_));
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }//GEN-LAST:event_mediumXYTextField_ActionPerformed

    private void getPartFieldButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_getPartFieldButton_ActionPerformed
       long nrPixelsX = core_.getImageWidth();
       double pixelSize = core_.getPixelSizeUm();
       nf_.setMaximumFractionDigits(2);
       mediumXYTextField_.setText(nf_.format(pixelSize * nrPixelsX * 0.1));
    }//GEN-LAST:event_getPartFieldButton_ActionPerformed

    private void largeXYTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_largeXYTextField_ActionPerformed
       try {
          largeMovement_ = nf_.parse(largeXYTextField_.getText()).doubleValue();
          nf_.setMaximumFractionDigits(1);
          largeXYTextField_.setText(nf_.format(largeMovement_));
       } catch(ParseException e) {
           // ignore if parsing fails
       }
    }//GEN-LAST:event_largeXYTextField_ActionPerformed

    private void smallXYTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallXYTextField_ActionPerformed
       try {
         smallMovement_ = nf_.parse(smallXYTextField_.getText()).doubleValue();
         nf_.setMaximumFractionDigits(3);
         smallXYTextField_.setText(nf_.format(smallMovement_));
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }//GEN-LAST:event_smallXYTextField_ActionPerformed

    private void get1PixellXYButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_get1PixellXYButton_ActionPerformed
       double pixelSize = core_.getPixelSizeUm();
       smallXYTextField_.setText(nf_.format(pixelSize));
       smallMovement_ = pixelSize;nf_.setMaximumFractionDigits(3);
       smallXYTextField_.setText(nf_.format(smallMovement_));  
    }//GEN-LAST:event_get1PixellXYButton_ActionPerformed

    private void getFieldButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_getFieldButton_ActionPerformed
       long nrPixelsX = core_.getImageWidth();
       double pixelSize = core_.getPixelSizeUm();
       nf_.setMaximumFractionDigits(1);
       largeXYTextField_.setText(nf_.format(pixelSize * nrPixelsX));
    }//GEN-LAST:event_getFieldButton_ActionPerformed

    private void onWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_onWindowClosing
       prefs_.putInt(FRAMEXPOS, (int) getLocation().getX());
       prefs_.putInt(FRAMEYPOS, (int) getLocation().getY());
       prefs_.putDouble(SMALLMOVEMENT, smallMovement_);
       prefs_.putDouble(MEDIUMMOVEMENT, mediumMovement_);
       prefs_.putDouble(LARGEMOVEMENT, largeMovement_);
    }//GEN-LAST:event_onWindowClosing

    private void focusLostHandlerJTF1(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_focusLostHandlerJTF1
       try {
         smallMovement_ = nf_.parse(smallXYTextField_.getText()).doubleValue();
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }//GEN-LAST:event_focusLostHandlerJTF1

    private void focusLostHandlerJTF2(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_focusLostHandlerJTF2
       try {
         mediumMovement_ = nf_.parse(mediumXYTextField_.getText()).doubleValue();
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }//GEN-LAST:event_focusLostHandlerJTF2

    private void focusLostHandlerJTF3(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_focusLostHandlerJTF3
       try {
         largeMovement_ = nf_.parse(largeXYTextField_.getText()).doubleValue();
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }//GEN-LAST:event_focusLostHandlerJTF3

    private void mediumZUpButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mediumZUpButton_ActionPerformed
       try {
          setRelativeStagePosition(nf_.parse(mediumZTextField_.getText()).doubleValue());
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }//GEN-LAST:event_mediumZUpButton_ActionPerformed

    private void smallZUpButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallZUpButton_ActionPerformed
       try {
         setRelativeStagePosition(nf_.parse(smallZTextField_.getText()).doubleValue());
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }//GEN-LAST:event_smallZUpButton_ActionPerformed

    private void smallZDownButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallZDownButton_ActionPerformed
       try {
         setRelativeStagePosition( - nf_.parse(smallZTextField_.getText()).doubleValue());
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }//GEN-LAST:event_smallZDownButton_ActionPerformed

    private void mediumZDownButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mediumZDownButton_ActionPerformed
       try {
          setRelativeStagePosition( - nf_.parse(mediumZTextField_.getText()).doubleValue());
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }//GEN-LAST:event_mediumZDownButton_ActionPerformed

    private void smallZTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallZTextField_ActionPerformed
       updateSmallZMove();
    }//GEN-LAST:event_smallZTextField_ActionPerformed

    private void smallZTextField_focusLostHandlerJTF1(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_smallZTextField_focusLostHandlerJTF1
      updateSmallZMove();
    }//GEN-LAST:event_smallZTextField_focusLostHandlerJTF1

   private void updateSmallZMove() {
      try {
         double smallZMove = nf_.parse(smallZTextField_.getText()).doubleValue();
         smallMovementZ_.put(currentZDrive_, smallZMove);
         prefs_.putDouble(SMALLMOVEMENTZ + currentZDrive_, smallZMove);
      } catch (ParseException e) {
         // ignore if parsing fails
      }
   }

    private void mediumZTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mediumZTextField_ActionPerformed
       updateMediumZMove();
    }//GEN-LAST:event_mediumZTextField_ActionPerformed

    private void mediumZTextField_focusLostHandlerJTF2(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_mediumZTextField_focusLostHandlerJTF2
       updateMediumZMove();
    }//GEN-LAST:event_mediumZTextField_focusLostHandlerJTF2

    private void updateMediumZMove() {
    try {
          double mediumZMove = nf_.parse(mediumZTextField_.getText()).doubleValue();
          mediumMovementZ_.put(currentZDrive_, mediumZMove);
          prefs_.putDouble(MEDIUMMOVEMENTZ + currentZDrive_, mediumZMove);
       } catch(ParseException e) {
          // ignore if parsing fails
       }
    }
   private void zDriveSelect_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zDriveSelect_ActionPerformed
      currentZDrive_ = (String) zDriveSelect_.getSelectedItem(); 
      if (initialized_) {
         prefs_.put(CURRENTZDRIVE, currentZDrive_);
         updateZMovements();   // Sets the small and medium Z movement to whatever we had for this new drive
         try {
            updateZPosLabel();
         } catch (Exception ex) {
            // ignore for now...
         }
      }
   }//GEN-LAST:event_zDriveSelect_ActionPerformed

   private void jButton20ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton20ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_jButton20ActionPerformed

   private void jButton21ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton21ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_jButton21ActionPerformed

   private void jButton22ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton22ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_jButton22ActionPerformed

 
   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JButton get1PixellXYButton_;
   private javax.swing.JButton getFieldButton_;
   private javax.swing.JButton getPartFieldButton_;
   private javax.swing.JButton jButton20;
   private javax.swing.JButton jButton21;
   private javax.swing.JButton jButton22;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel10;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JLabel jLabel5;
   private javax.swing.JLabel jLabel6;
   private javax.swing.JLabel jLabel7;
   private javax.swing.JLabel jLabel8;
   private javax.swing.JLabel jLabel9;
   private javax.swing.JPanel jPanel1;
   private javax.swing.JButton largXYUpButton_;
   private javax.swing.JButton largeLeftButton_;
   private javax.swing.JButton largeRightButton_;
   private javax.swing.JButton largeXYDownButton_;
   private javax.swing.JTextField largeXYTextField_;
   private javax.swing.JButton mediumLeftButton_;
   private javax.swing.JButton mediumRightButton_;
   private javax.swing.JButton mediumXYDownButton_;
   private javax.swing.JTextField mediumXYTextField_;
   private javax.swing.JButton mediumXYUpButton_;
   private javax.swing.JButton mediumZDownButton_;
   private javax.swing.JTextField mediumZTextField_;
   private javax.swing.JButton mediumZUpButton_;
   private javax.swing.JButton smallLeftButton_;
   private javax.swing.JButton smallRightButton_;
   private javax.swing.JButton smallXYDownButton_;
   private javax.swing.JTextField smallXYTextField_;
   private javax.swing.JButton smallXYUpButton_;
   private javax.swing.JButton smallZDownButton_;
   private javax.swing.JTextField smallZTextField_;
   private javax.swing.JButton smallZUpButton_;
   private javax.swing.JComboBox zDriveSelect_;
   private javax.swing.JLabel zPositionLabel_;
   // End of variables declaration//GEN-END:variables

   private javax.swing.JComponent zDriveGUIElements_[];
  
}

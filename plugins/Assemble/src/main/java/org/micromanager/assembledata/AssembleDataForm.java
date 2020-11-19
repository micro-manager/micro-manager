///////////////////////////////////////////////////////////////////////////////
//FILE:          AssembleDataForm.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     AssembleData plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2019
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

package org.micromanager.assembledata;

import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;

import javax.swing.event.ChangeEvent;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.assembledata.exceptions.MalFormedFileNameException;
import org.micromanager.data.Datastore;
import org.micromanager.display.DataViewer;
import org.micromanager.display.internal.event.DataViewerAddedEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author nico
 */
public class AssembleDataForm extends JDialog {
   private final Studio studio_;
   private final MutablePropertyMapView profileSettings_;
   
   private boolean wasDisposed_ = false;
   private String statusMessage_;
   private final Font arialSmallFont_;
   private final Dimension buttonSize_;
   private final JLabel statusLabel_;
   
   private final JComboBox<String> dataSet1Box_;
   private final JComboBox<String> dataSet2Box_;
   
   private final String DATAVIEWER1 = "DataViewer1";
   private final String DATAVIEWER2 = "DataViewer2";
   private final String CHOOSEDIR = "ChooseDir";
   private final String CHOOSEDATASET = "ChooseDataSet";
   private final String DIRNAME = "DirName";
   private final String XOFFSET = "XOffset";
   private final String YOFFSET = "YOffset";
   private final int DEFAULTX = -350;
   private final int DEFAULTY = 200;
   
     
    /**
     * Creates new form MultiChannelShadingForm
     * @param studio
     */
   @SuppressWarnings("LeakingThisInConstructor")
   public AssembleDataForm(Studio studio) {
      studio_ = studio;
      profileSettings_ = 
              studio_.profile().getSettings(AssembleDataForm.class);
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      super.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            dispose();
         }
      }
      );
      arialSmallFont_ = new Font("Arial", Font.PLAIN, 12);
      buttonSize_ = new Dimension(70, 21);

      
      super.setLayout(new MigLayout("flowx, fill, insets 8"));
      super.setTitle(AssembleData.MENUNAME);

      setBounds(100, 100, 449, 327);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);
      
      JRadioButton chooseDir = new JRadioButton("Choose Directory");
      super.add(chooseDir, "span 2, wrap");
      
      final JTextField locationsField = new JTextField(35);
      locationsField.setFont(arialSmallFont_);
      locationsField.setText(profileSettings_.getString(DIRNAME,
               profileSettings_.getString(DIRNAME, "")));
      locationsField.setHorizontalAlignment(JTextField.LEFT);
      super.add(locationsField, "span 2, split");
      
      DragDropListener dragDropListener = new DragDropListener(locationsField);
      new DropTarget(this, dragDropListener);
      new DropTarget(locationsField, dragDropListener);

      final JButton locationsFieldButton =  mcsButton(buttonSize_, arialSmallFont_);
      locationsFieldButton.setText("...");
      locationsFieldButton.addActionListener((ActionEvent evt) -> {
         File f = FileDialogs.openDir(this, "Locations File",   
                 new FileDialogs.FileType("MMProjector", "Locations File",
                         locationsField.getText(), true, "") );
         if (f != null) {
            locationsField.setText(f.getAbsolutePath());
         }
      });
      super.add(locationsFieldButton, "wrap");
      
      super.add(new JSeparator(), "span 2, grow, wrap");
      
      
      //populate DataViewer ComboBoxes   
      JRadioButton chooseData = new JRadioButton("Choose from open Data Sets");
      super.add(chooseData, "span 2, wrap");     
 
      dataSet1Box_ = new JComboBox();
      setupDataViewerBox(dataSet1Box_, DATAVIEWER1);
      super.add(new JLabel("First Data Set:"));
      super.add(dataSet1Box_, "wrap");
     
      dataSet2Box_ = new JComboBox();
      setupDataViewerBox(dataSet2Box_, DATAVIEWER2);
      super.add(new JLabel("Second Data Set:"));
      super.add(dataSet2Box_, "wrap");
      
      super.add(new JSeparator(), "span 2, grow, wrap");
      
      ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(chooseDir);
      buttonGroup.add(chooseData);
      
      chooseDir.addActionListener((ActionEvent e) -> {
         if (chooseDir.isSelected()) {
            locationsField.setEnabled(true);
            locationsFieldButton.setEnabled(true);
            dataSet1Box_.setEnabled(false);
            dataSet2Box_.setEnabled(false);
            profileSettings_.putBoolean(CHOOSEDIR, true);
            profileSettings_.putBoolean(CHOOSEDATASET, false);
         }
      });
      chooseData.addActionListener((ActionEvent e) -> {
         if (chooseData.isSelected()) {
            locationsField.setEnabled(false);
            locationsFieldButton.setEnabled(false);
            dataSet1Box_.setEnabled(true);
            dataSet2Box_.setEnabled(true);
            profileSettings_.putBoolean(CHOOSEDIR, false);
            profileSettings_.putBoolean(CHOOSEDATASET, true);
         }
      });
      
      chooseDir.setSelected(profileSettings_.getBoolean(CHOOSEDIR, false));
      chooseData.setSelected(profileSettings_.getBoolean(CHOOSEDATASET, true));
      locationsField.setEnabled(chooseDir.isSelected());
      locationsFieldButton.setEnabled(chooseDir.isSelected());
      dataSet1Box_.setEnabled(chooseData.isSelected());
      dataSet2Box_.setEnabled(chooseData.isSelected());

      JLabel xOffsetLabel = new JLabel("X Offset (pixels)");
      super.add(xOffsetLabel);
      int xOffset = profileSettings_.getInteger(XOFFSET, DEFAULTX);
      final SpinnerNumberModel xModel = new SpinnerNumberModel(xOffset, -1000, 1000, 1);
      final JSpinner xSpinner = new JSpinner (xModel);
      xSpinner.addChangeListener((ChangeEvent e) -> {
         profileSettings_.putInteger(XOFFSET, (Integer) xSpinner.getValue());
      });
      super.add(xSpinner, "wrap");
            
      JLabel yOffsetLabel = new JLabel("YOffset (pixels)");
      super.add(yOffsetLabel);
      int yOffset = profileSettings_.getInteger(YOFFSET, DEFAULTY);
      final SpinnerNumberModel yModel = new SpinnerNumberModel(yOffset, -1000, 1000, 1);
      final JSpinner ySpinner = new JSpinner (yModel);
      ySpinner.addChangeListener((ChangeEvent e) -> {
         profileSettings_.putInteger(YOFFSET, (Integer) ySpinner.getValue());
      });
      super.add(ySpinner, "wrap");
                  
      JButton helpButton = new JButton("Help");
      helpButton.addActionListener((ActionEvent e) -> {
         new Thread(org.micromanager.internal.utils.GUIUtils.makeURLRunnable(
                 "https://micro-manager.org/wiki/AssembleData")).start();
      });
      super.add (helpButton, "span 2, split 4");
    
                
      final JButton testButton =  new JButton("Test");
      testButton.addActionListener((ActionEvent e) -> {
         if (chooseDir.isSelected()) {
            assembleDir(true, locationsField.getText());
         } else {
            assembleDataSets(true); 
         }
      });
      super.add(testButton);
      
      final JButton assembleButton =  new JButton("Assemble");
      assembleButton.addActionListener((ActionEvent e) -> {
         if (chooseDir.isSelected()) {
            assembleDir(false, locationsField.getText());
         } else {
            assembleDataSets(false); 
         }
      });
      super.add(assembleButton, "wrap");
      
      statusLabel_ = new JLabel(" ");
      super.add(statusLabel_, "span 3, wrap");
      super.pack();
      
      super.setVisible(true);
      super.toFront();
      
      studio_.events().registerForEvents(this);
      studio_.displays().registerForEvents(this);
   }
      
   private void assembleDataSets(boolean test) {
      String dataViewerName1 = profileSettings_.getString(DATAVIEWER1,"");
      String dataViewerName2 = profileSettings_.getString(DATAVIEWER2,"");
      DataViewer dv1 = null;
      DataViewer dv2 = null;
      for (DataViewer dv : studio_.displays().getAllDataViewers()) {
         if (dv.getName().equals(dataViewerName1)) {
            dv1 = dv;
         }
         if (dv.getName().equals(dataViewerName2)) {
            dv2 = dv;
         }
      }     
      if (dv1 == null || dv2 == null) {
         studio_.logs().showError("One or both data sets are empty");
         return;
      }
      if (dv1.equals(dv2)) {
         studio_.logs().showError("Data Sets are the same");
         return;
      }
      int xOffset = profileSettings_.getInteger(XOFFSET, DEFAULTX);
      int yOffset = profileSettings_.getInteger(YOFFSET, DEFAULTY);
      ShowWorker.run(studio_, this, dv1.getDataProvider(), 
              dv2.getDataProvider(), xOffset, yOffset, test);    
   }
   
   private void assembleDir(boolean test, String dirLocation)  {
      File dir = new File(dirLocation);
      if (!dir.exists()) {
         studio_.logs().showError("Directory " + dirLocation + " does not exist");
         return;
      }
      if (!dir.isDirectory()) {
         studio_.logs().showError(dirLocation + " is not a directory");
      }
      profileSettings_.putString(DIRNAME, dirLocation);
      String[] dataSets = dir.list();
      
      // Expect dir names of the form:
      // TIRF/Confocal-A1-Site_0-0   
      // We will deal with extra "-" before well-Site, but not with extra "_"
      
      List<FileNameInfo> fileNameInfos = new ArrayList<>();
      for (String location : dataSets) {
         if (!location.startsWith(".")) { // avoid junk files emitted by Mac OS
            try {
               fileNameInfos.add(new FileNameInfo(location));
            } catch (MalFormedFileNameException mf) {
               studio_.logs().showError("Failed to parse filename: " + location);
            }
         }
      }
      Collections.sort(fileNameInfos);
      
      Set<String> roots = new HashSet<>();
      for (FileNameInfo entry : fileNameInfos) {
         roots.add(entry.root());
      }
      if (roots.size() != 2) {
         studio_.logs().showError("More than 2 roots found for the DataSet Names");
      }
      List<FileNameInfo> fni1 = new ArrayList<>();
      List<FileNameInfo> fni2 = new ArrayList<>();
      List<String> rootList = new ArrayList(2);
      for (String root : roots) {
         rootList.add(root);
      }
      for (FileNameInfo entry : fileNameInfos) {
         if (entry.root().equals(rootList.get(0))) {
            fni1.add(entry);
         } else if (entry.root().equals(rootList.get(1))) {
            fni2.add(entry);
         }
      }
      
      // TODO: check that the sie of fni1 and 2 are the same

      int xOffset = profileSettings_.getInteger(XOFFSET, DEFAULTX);
      int yOffset = profileSettings_.getInteger(YOFFSET, DEFAULTY);

      if (test) {
         String test1 = dataSets[0];
         String root = Utils.findRoot(test1, roots);
         if (!root.isEmpty()) {
            String remainder = test1.substring(root.length());
            String partner = Utils.findOtherRoot(root, roots) + remainder;
            File f1 = new File(dirLocation + File.separator + test1);
            File f2 = new File(dirLocation + File.separator + partner);
            if (!f1.exists() || !f1.isDirectory()) {
               studio_.logs().showError("Failed to find " + f1.getPath());
            }
            if (!f2.exists() || !f2.isDirectory()) {
               studio_.logs().showError("Failed to find " + f2.getPath());
            }
            try {
               try (Datastore store1 = studio_.data().loadData(f1.getPath(), false)) {
                  try (Datastore store2 = studio_.data().loadData(f2.getPath(), false)) {
                     ShowWorker.execute(studio_, this, store1, store2, xOffset, yOffset, test);
                  }
               }
            } catch (IOException ioe) {
               studio_.logs().showError(ioe, "Failed to open file " + f1.getPath());
            }
         }
      } else {
         DirWorker.run(studio_, this, dirLocation, fni1, fni2, xOffset, yOffset, test);
      }
       
   }
   

  
   public void showGUI() {
      setVisible(true);
   }
   
   @Override
   public void dispose() {
      wasDisposed_ = true;
      studio_.events().unregisterForEvents(this);
      studio_.displays().unregisterForEvents(this);
      super.dispose();
   }
   
   public boolean wasDisposed() { 
      return wasDisposed_; 
   }
 
   
   public final Font getButtonFont() {
      return arialSmallFont_;
   }
   
   public final Dimension getButtonDimension() {
      return buttonSize_;
   }
   
   
   public final JButton mcsButton(Dimension buttonSize, Font font) {
      JButton button = new JButton();
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setFont(font);
      button.setMargin(new Insets(0, 0, 0, 0));
      
      return button;
   }
   
   private void setupDataViewerBox(final JComboBox<String> box, final String key) {
      List<DataViewer> allDataViewers = studio_.displays().getAllDataViewers();
      String[] dataViewers = new String[allDataViewers.size()];
      for (int i = 0; i < allDataViewers.size(); i++) {
         dataViewers[i] = allDataViewers.get(i).getName();
      }
      for (ActionListener al : box.getActionListeners()) {
         box.removeActionListener(al);
      }
      box.setModel(new DefaultComboBoxModel<>(
              dataViewers));
      String dataViewer = profileSettings_.getString(key, "");
      box.setSelectedItem(dataViewer);
      profileSettings_.putString(key, (String)box.getSelectedItem());
      box.addActionListener((java.awt.event.ActionEvent evt) -> {
         profileSettings_.putString(key, (String) 
                 box.getSelectedItem());
      });
      super.pack();
   }
   
   public synchronized void setStatus(final String status) {
      statusMessage_ = status;
      SwingUtilities.invokeLater(() -> {
         // update the statusLabel from this thread
         if (status != null) {
            statusLabel_.setText(status);
         }
      });
   }

   public synchronized String getStatus() {
      String status = statusMessage_;
      statusMessage_ = null;
      return status;
   }
   

   @Subscribe
   public void closeRequested( ShutdownCommencingEvent sce){
      this.dispose();
   }
   
   private void setupDataViewerBoxes() {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> {
            setupDataViewerBoxes();
         });
      }
      setupDataViewerBox(dataSet1Box_, DATAVIEWER1);
      setupDataViewerBox(dataSet2Box_, DATAVIEWER2);
   }
   
 
   @Subscribe
   public void onWindowAddedEvent(DataViewerAddedEvent e) {
      setupDataViewerBoxes();
   }
   
   
   @Subscribe
   public void onWindowClosingEvent(DataViewerWillCloseEvent e) { 
      setupDataViewerBoxes();
   }
   
    
}
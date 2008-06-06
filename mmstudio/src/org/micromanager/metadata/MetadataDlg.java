///////////////////////////////////////////////////////////////////////////////
//FILE:           MetadataDlg.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      mmstudio
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
//COPYRIGHT:      University of California, San Francisco, 2006
//                100X Imaging Inc, www.100ximaging.com, 2008
//
//LICENSE:        This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//CVS:            $Id$

package org.micromanager.metadata;

import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpringLayout;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.utils.MMDialog;

/**
 * Creates a dialog displaying the Micro-Manager metadata.  Allows editing of Comment field and will save changes, including changes to channel contrast settings and colors
 */

public class MetadataDlg extends MMDialog {
   private JTextArea msgTextArea_;
   private static final long serialVersionUID = -8341923512513272695L;
   private SpringLayout springLayout;
   //private JTextArea summaryArea_;
   private JTextArea commentArea_;
   private AcquisitionData acqData_;
   private int channels_;
   private Image5DWindow i5dWindow_;
   private Color uneditableColor_ = java.awt.Color.GRAY;
   private JScrollPane stateTablePane_;
   private JTable stateTable_;
   private JScrollPane imageTablePane_;
   private JTable imageTable_;
   private JTabbedPane tabbedPane_;
   private JScrollPane sumTablePane_;
   private JTable sumTable_;
   private JTabbedPane sumTabbedPane_;

   /**
    * Create dialog to view and save metadata
    */
   public MetadataDlg(Frame owner, Image5DWindow i5dWindow) {
      super(owner);
      i5dWindow_ = i5dWindow;

      addWindowListener(new WindowAdapter() {
         public void windowClosing(final WindowEvent e) {
            savePosition();
         }
      });
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setTitle("Image Metadata");

      //setBounds(100, 100, 396, 435);
      loadPosition(100, 100, 396, 435);

      final JButton closeButton = new JButton();
      closeButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            savePosition();
            dispose();
         }
      });
      closeButton.setText("Close");
      getContentPane().add(closeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, closeButton, 30, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, closeButton, 4, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, closeButton, -3, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, closeButton, -91, SpringLayout.EAST, getContentPane());

      final JButton saveButton = new JButton();
      saveButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            updateMetadata();
         }
      });
      saveButton.setText("Save");
      getContentPane().add(saveButton);
      springLayout.putConstraint(SpringLayout.SOUTH, saveButton, 30, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, saveButton, 4, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, saveButton, -100, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, saveButton, -191, SpringLayout.EAST, getContentPane());


      tabbedPane_ = new JTabbedPane();
      tabbedPane_.setAutoscrolls(true);
      getContentPane().add(tabbedPane_);
      springLayout.putConstraint(SpringLayout.SOUTH, tabbedPane_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, tabbedPane_, 5, SpringLayout.SOUTH, saveButton);
      springLayout.putConstraint(SpringLayout.EAST, tabbedPane_, -3, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, tabbedPane_, 5, SpringLayout.WEST, getContentPane());
      commentArea_ = new JTextArea();
      commentArea_.setEditable(true);
      commentArea_.setWrapStyleWord(true);
      tabbedPane_.addTab("Comment", null, commentArea_, null);
      tabbedPane_.setBackgroundAt(0,java.awt.Color.white);
      commentArea_.setFont(new Font("Arial", Font.PLAIN, 12));

      // summary display
//      summaryArea_ = new JTextArea();
//      summaryArea_.setEditable(false);
//      summaryArea_.setBackground(uneditableColor_);
//      summaryArea_.setWrapStyleWord(true);
//      tabbedPane_.addTab("Summary", null, summaryArea_, null);
//      tabbedPane_.setBackgroundAt(1, uneditableColor_);
//      summaryArea_.setFont(new Font("Arial", Font.PLAIN, 12));
      
      sumTablePane_ = new JScrollPane();
      tabbedPane_.addTab("Summary", null, sumTablePane_, null);
      sumTable_ = new JTable();
      sumTablePane_.setViewportView(sumTable_);
      tabbedPane_.setBackgroundAt(1, uneditableColor_);
      
      // image display
      imageTablePane_ = new JScrollPane();
      tabbedPane_.addTab("Image", null, imageTablePane_, null);
      imageTable_ = new JTable();
      imageTablePane_.setViewportView(imageTable_);
      tabbedPane_.setBackgroundAt(2, uneditableColor_);

//      final JScrollPane scrollPane = new JScrollPane();
//      tabbedPane.addTab("Image", null, scrollPane, null);
//      tabbedPane.setBackgroundAt(2, uneditableColor_);
//
//      imageArea_ = new JEditorPane();
//      scrollPane.setViewportView(imageArea_);
//      imageArea_.setAutoscrolls(true);
//      imageArea_.setEditable(false);
//      imageArea_.setBackground(uneditableColor_);
//      imageArea_.setFont(new Font("Arial", Font.PLAIN, 12));
      
      // state display
      stateTablePane_ = new JScrollPane();
      tabbedPane_.addTab("State", null, stateTablePane_, null);

      stateTable_ = new JTable();
      stateTablePane_.setViewportView(stateTable_);
      tabbedPane_.setBackgroundAt(3, uneditableColor_);

      msgTextArea_ = new JTextArea();
      msgTextArea_.setEditable(false);
      msgTextArea_.setBackground(UIManager.getColor("Button.background"));
      getContentPane().add(msgTextArea_);
      springLayout.putConstraint(SpringLayout.EAST, msgTextArea_, -5, SpringLayout.WEST, saveButton);
      springLayout.putConstraint(SpringLayout.WEST, msgTextArea_, 5, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, msgTextArea_, 27, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, msgTextArea_, 9, SpringLayout.NORTH, getContentPane());

   }

   /*
    * This must be called directly after constructions or things will fail miserably.  Don't know why, but did not seem to work inside constructor
    */
   public void setMetadata(AcquisitionData md) {
      acqData_ = md;
   }


   /*
    * Puts text into the Summary tab.  Also enters values for globals setting number of frames, slices and channels
    */
   public void displaySummary() {
      msgTextArea_.setText("");
      try {
//         JSONObject summaryData = acqData_.getSummaryMetadata();
//         channels_ = acqData_.getNumberOfChannels();
//         summaryArea_.setText(summaryData.toString(3));
                  
         String sumKeys[] = acqData_.getSummaryKeys();
         String[][] sumData = new String[sumKeys.length][2]; 
         String sumColumnNames[] = { "Property", "Value(s)" };
         for (int i=0; i<sumKeys.length; i++) {
            sumData[i][0] = sumKeys[i];
            sumData[i][1] = acqData_.getSummaryValue(sumKeys[i]);
         }
         TableModel sumModel = new DefaultTableModel(sumData, sumColumnNames) {
            public Class<?> getColumnClass(int column) {
               return getValueAt(0, column).getClass();
            }
            public boolean isCellEditable(int row, int col) {
               return false;
            }
         };
         sortAllRowsBy((DefaultTableModel)sumModel, 0, true);
         sumTable_.setModel(sumModel);
         sumTablePane_.setViewportView(sumTable_);
      } catch (MMAcqDataException e) {
         javax.swing.JOptionPane.showMessageDialog(this, e.getMessage());
      }
   }

   /*
    * Displays image-specific data in Image data tab
    */
   public void displayImageData(int frame, int channel, int slice) {
      msgTextArea_.setText("");
      msgTextArea_.setToolTipText("");
      if (!acqData_.hasImageMetadata(frame, channel, slice)) {
         String txtMsg = "Metadata not available for the current image.";
         String toolTip = "If acquistion has gaps (skips), the actual image displayed was interpolated from the previous frame. " +
                          "\nOtherwise, acquisition was interrupted or in progress.";
         msgTextArea_.setText(txtMsg);
         msgTextArea_.setToolTipText(toolTip);
         imageTable_.setModel(new DefaultTableModel());
         stateTable_.setModel(new DefaultTableModel());
         return;
      }
      
      try {
         // display image data
//         JSONObject imgData = acqData_.getImageMetadata(frame, channel, slice);
//         imageArea_.setText(imgData.toString(3));
         
         String imageKeys[] = acqData_.getImageKeys(frame, channel, slice);
         String[][] imageData = new String[imageKeys.length][2]; 
         String imageColumnNames[] = { "Property", "Value" };
         for (int i=0; i<imageKeys.length; i++) {
            imageData[i][0] = imageKeys[i];
            imageData[i][1] = acqData_.getImageValue(frame, channel, slice, imageKeys[i]);
         }
         TableModel imageModel = new DefaultTableModel(imageData, imageColumnNames) {
            public Class<?> getColumnClass(int column) {
               return getValueAt(0, column).getClass();
            }
            public boolean isCellEditable(int row, int col) {
               return false;
            }
         };
         sortAllRowsBy((DefaultTableModel)imageModel, 0, true);
         imageTable_.setModel(imageModel);
         imageTablePane_.setViewportView(imageTable_);
         
         // display state data
         JSONObject state = acqData_.getSystemState(frame, channel, slice);

         String[][] stateTableData = new String[state.length()][2]; 
         String stateColumnNames[] = { "Item", "Value" };

         int count = 0;
         for (Iterator i = state.keys(); i.hasNext();) {
            String stateRowKey = (String)i.next();
            stateTableData[count][0] = stateRowKey;
            stateTableData[count++][1] = state.getString(stateRowKey);
         }

         TableModel stateModel = new DefaultTableModel(stateTableData, stateColumnNames) {
            public Class<?> getColumnClass(int column) {
               return getValueAt(0, column).getClass();
            }
         };

         sortAllRowsBy((DefaultTableModel)stateModel, 0, true);
         stateTable_.setModel(stateModel);
         //TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(model);
         //imageMdTable_.setRowSorter(sorter);
         stateTablePane_.setViewportView(stateTable_);

      } catch (JSONException e) {
         javax.swing.JOptionPane.showMessageDialog(this, e.getMessage());
      } catch (MMAcqDataException e) {
         javax.swing.JOptionPane.showMessageDialog(this, e.getMessage());
      }
   }

   /*
    * Enters text in Comment field
    */
   public void displayComment () {
      msgTextArea_.setText("");
      try {
         commentArea_.setText(acqData_.getComment());
      }catch (MMAcqDataException e) {
         javax.swing.JOptionPane.showMessageDialog(this, e.getMessage());
      }
   }
   
   private class ColumnSorter implements Comparator {
      int colIndex;
      boolean ascending;
      ColumnSorter(int colIndex, boolean ascending) {
         this.colIndex = colIndex;
         this.ascending = ascending;
      }
      public int compare(Object a, Object b) {
         Vector v1 = (Vector)a;
         Vector v2 = (Vector)b;
         Object o1 = v1.get(colIndex);
         Object o2 = v2.get(colIndex);

         // Treat empty strains like nulls
         if (o1 instanceof String && ((String)o1).length() == 0) {
            o1 = null;
         }
         if (o2 instanceof String && ((String)o2).length() == 0) {
            o2 = null;
         }

         // Sort nulls so they appear last, regardless
         // of sort order
         if (o1 == null && o2 == null) {
            return 0;
         } else if (o1 == null) {
            return 1;
         } else if (o2 == null) {
            return -1;
         } else if (o1 instanceof Comparable) {
            if (ascending) {
               return ((Comparable)o1).compareTo(o2);
            } else {
               return ((Comparable)o2).compareTo(o1);
            }
         } else {
            if (ascending) {
               return o1.toString().compareTo(o2.toString());
            } else {
               return o2.toString().compareTo(o1.toString());
            }
         }
      }
   }

   private void sortAllRowsBy(DefaultTableModel model, int colIndex, boolean ascending) {
      Vector data = model.getDataVector();
      Collections.sort(data, new ColumnSorter(colIndex, ascending));
      model.fireTableStructureChanged();
   }
   
   private void updateMetadata() {
      // Copy the Comment into the summary data and save the metadata           
      try {
         acqData_.setComment(commentArea_.getText());
         for (int i=0; i<channels_; i++) {
            i5dWindow_.getImage5D().storeChannelProperties(i+1);
            ChannelDisplayProperties cdp = i5dWindow_.getImage5D().getChannelDisplayProperties(i+1);
            DisplaySettings ds = new DisplaySettings(cdp.getMinValue(), cdp.getMaxValue());
            acqData_.setChannelDisplaySetting(i, ds);
            Color c = i5dWindow_.getColor(i+1);      
            acqData_.setChannelColor(i, c.getRGB());
         }
         displaySummary();
         // save metadata
         if (!acqData_.isInMemory()) {
            acqData_.saveMetadata();
         } else {
            javax.swing.JOptionPane.showMessageDialog(this, "Metadata were not previously saved.  Please save the image first");
            return;
         }
      }catch (MMAcqDataException e1) {
         javax.swing.JOptionPane.showMessageDialog(this, "Internal error: " + e1.getMessage());
         return; 
      }          
      
   }

}

package org.micromanager.metadata;

import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SpringLayout;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MMDialog;

public class MetadataDlg extends MMDialog {

   private SpringLayout springLayout;
   private JTextArea imageArea_;
   private JTextArea summaryArea_;
   private JSONObject metadata_;
   private int frames_;
   private int slices_;
   private int channels_;

   /**
    * Create the dialog
    */
   public MetadataDlg(Frame owner) {
      super(owner);
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

      final JTabbedPane tabbedPane = new JTabbedPane();
      getContentPane().add(tabbedPane);
      springLayout.putConstraint(SpringLayout.EAST, tabbedPane, -3, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, tabbedPane, 5, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, tabbedPane, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, tabbedPane, 5, SpringLayout.SOUTH, closeButton);
 
      summaryArea_ = new JTextArea();
      summaryArea_.setWrapStyleWord(true);
      tabbedPane.addTab("Summary", null, summaryArea_, null);
      summaryArea_.setFont(new Font("Arial", Font.PLAIN, 12));

      imageArea_ = new JTextArea();
      imageArea_.setWrapStyleWord(true);
      tabbedPane.addTab("Image Data", null, imageArea_, null);
      imageArea_.setFont(new Font("Arial", Font.PLAIN, 12));
      
      metadata_ = new JSONObject();
   }
   
   public void setMetadata(JSONObject md) {
      metadata_ = md;
//      try {
//         frames_ = md.getInt(SummaryKeys.NUM_FRAMES);
//         slices_ = md.getInt(SummaryKeys.NUM_SLICES);
//         channels_ = md.getInt(SummaryKeys.NUM_CHANNELS);
//      } catch (JSONException e) {
//         // TODO Auto-generated catch block
//         e.printStackTrace();
//      }
   }
   
   public void displaySummary() {
      try {
         JSONObject summaryData = metadata_.getJSONObject(SummaryKeys.SUMMARY);
         //String txt = "Time: " + summaryData.getString(SummaryKeys.TIME) + "\nFrames: " + frames_;
         summaryArea_.setText(summaryData.toString(3));
      } catch (JSONException e) {
         summaryArea_.setText("Invalid metadata: summary not available");
      }
   }
   
   public void displayImageData(int frame, int channel, int slice) {
      String key = ImageKey.generateFrameKey(frame, channel, slice);
      if (!metadata_.has(key)) {
         imageArea_.setText("Metadata not available for the current selection.\nAcquisition interrupted or in progress.");
         return;
      }
      try {
         JSONObject imgData = metadata_.getJSONObject(key);
         imageArea_.setText(imgData.toString(3));
      } catch (JSONException e) {
         imageArea_.setText("Unexpected error: image metadata not available");
      }
   }

}

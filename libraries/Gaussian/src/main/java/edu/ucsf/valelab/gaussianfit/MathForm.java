/*
 * MathForm.java
 *
 * Part of the Localization Microscopy plugin for Micro-Manager
 * 
 * Author:     Nico Stuurman
 *
Copyright (c) 2012-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit;

import edu.ucsf.valelab.gaussianfit.data.RowData;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.UserProfile;

/**
 * @author nico
 */
public class MathForm extends JFrame {

   private static final String FRAMEXPOS = "MathXPos";
   private static final String FRAMEYPOS = "MathYPos";
   private static final String DATASET1 = "DataSet1";
   private static final String DATASET2 = "DataSet2";
   private final String SELECTED = "Selected";
   private final UserProfile profile_;

   private JComboBox actionComboBox_;
   private JButton cancelButton_;
   private JComboBox dataSet1ComboBox_;
   private JComboBox dataSet2ComboBox_;
   private JLabel dataSet1Label_;
   private JLabel actionLabel_;
   private JLabel dataSet2Label_;
   private JButton okButton_;

   /**
    * Creates new form MathForm
    *
    * @param profile   - MM user profile
    * @param dataSets1 - dataSet to be shown in slot 1 of the dialog
    * @param dataSets2 - dataSet to be shown in slot 2 of the dialog
    */
   public MathForm(UserProfile profile, int[] dataSets1, int[] dataSets2) {
      profile_ = profile;
      initComponents();

      super.setLocation(profile_.getInt(this.getClass(), FRAMEYPOS, 50),
            profile_.getInt(this.getClass(), FRAMEYPOS, 100));

      dataSet1ComboBox_.removeAllItems();
      dataSet1ComboBox_.addItem(SELECTED);
      for (int i : dataSets1) {
         dataSet1ComboBox_.addItem(i);
      }
      int index = profile_.getInt(this.getClass(), DATASET1, 0);
      if (index >= dataSet1ComboBox_.getItemCount()) {
         index = 0;
      }
      dataSet1ComboBox_.setSelectedIndex(index);
      dataSet1ComboBox_.updateUI();

      dataSet2ComboBox_.removeAllItems();
      for (int i : dataSets2) {
         dataSet2ComboBox_.addItem(i);
      }
      index = dataSet2ComboBox_.getItemCount() - 1;
      if (index < 0) {
         index = 0;
      }
      if (dataSet2ComboBox_.getItemCount() > 0) {
         dataSet2ComboBox_.setSelectedIndex(index);
      }
      dataSet2ComboBox_.updateUI();
   }

   /**
    * Utility function to generate the user interface
    */
   private void initComponents() {

      dataSet1ComboBox_ = new JComboBox();
      actionComboBox_ = new JComboBox();
      dataSet2ComboBox_ = new JComboBox();
      dataSet1Label_ = new JLabel();
      actionLabel_ = new JLabel();
      dataSet2Label_ = new JLabel();
      okButton_ = new JButton();
      cancelButton_ = new JButton();

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Track Math");
      setResizable(false);
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            formWindowClosing(evt);
         }
      });

      dataSet1ComboBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      dataSet1ComboBox_.setModel(
            new DefaultComboBoxModel(new String[]{"Item 1", "Item 2", "Item 3", "Item 4"}));

      actionComboBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      actionComboBox_.setModel(new DefaultComboBoxModel(new String[]{"Subtract"}));
      actionComboBox_.setSelectedIndex(0);

      dataSet2ComboBox_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      dataSet2ComboBox_.setModel(
            new DefaultComboBoxModel(new String[]{"Item 1", "Item 2", "Item 3", "Item 4"}));

      dataSet1Label_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      dataSet1Label_.setText("DataSet 1");

      actionLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      actionLabel_.setText("Action");

      dataSet2Label_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      dataSet2Label_.setText("DataSet 2");

      okButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      okButton_.setText("Do it");
      okButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            okButton_ActionPerformed(evt);
         }
      });

      cancelButton_.setFont(new java.awt.Font("Lucida Grande", 0, 11)); // NOI18N
      cancelButton_.setText("Close");
      cancelButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            cancelButton_ActionPerformed(evt);
         }
      });

      MigLayout layout = new MigLayout("fillx");

      getContentPane().setLayout(layout);

      super.add(dataSet1Label_);
      super.add(dataSet1ComboBox_, "growx, wrap");
      super.add(actionLabel_);
      super.add(actionComboBox_, "growx, wrap");
      super.add(dataSet2Label_);
      super.add(dataSet2ComboBox_, "growx, wrap");
      super.add(okButton_, "tag ok");
      super.add(cancelButton_, "tag cancel");

      pack();
   }

   private void cancelButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      formWindowClosing(null);
      dispose();
   }

   private void okButton_ActionPerformed(java.awt.event.ActionEvent evt) {

      boolean usr = false;
      int i1 = 0;
      int i2;
      try {
         if (SELECTED.equals((String) dataSet1ComboBox_.getSelectedItem())) {
            usr = true;
         }
      } catch (java.lang.ClassCastException cce) {

         i1 = (Integer) dataSet1ComboBox_.getSelectedItem();

      }
      i2 = (Integer) dataSet2ComboBox_.getSelectedItem();
      final int id1 = i1;
      final int id2 = i2;
      final boolean useSelectedRows = usr;

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {
            DataCollectionForm df = DataCollectionForm.getInstance();

            RowData rd1 = null;
            RowData rd2 = null;
            int start = df.getNumberOfSpotData();

            if (!useSelectedRows) {
               for (int i = 0; i < df.getNumberOfSpotData(); i++) {
                  if (id1 == df.getSpotData(i).ID_) {
                     rd1 = df.getSpotData(i);
                  }
                  if (id2 == df.getSpotData(i).ID_) {
                     rd2 = df.getSpotData(i);
                  }
               }
               df.doMathOnRows(rd1, rd2, 0);
            } else {
               for (int i = 0; i < df.getNumberOfSpotData(); i++) {
                  if (id2 == df.getSpotData(i).ID_) {
                     rd2 = df.getSpotData(i);
                  }
               }
               int rows[] = df.getResultsTable().getSelectedRows();
               if (rows.length > 0) {
                  for (int i = 0; i < rows.length; i++) {
                     df.doMathOnRows(df.getSpotData(rows[i]), rd2, 0);
                  }
               }
            }

            int end = df.getNumberOfSpotData();
            if (end > start) {
               df.setSelectedRows(start, end - 1);
            }


         }
      };

      (new Thread(doWorkRunnable)).start();

      formWindowClosing(null);
      dispose();

   }

   private void formWindowClosing(java.awt.event.WindowEvent evt) {
      profile_.setInt(this.getClass(), FRAMEXPOS, getX());
      profile_.setInt(this.getClass(), FRAMEYPOS, getY());
      profile_.setInt(this.getClass(), DATASET1, dataSet1ComboBox_.getSelectedIndex());
      profile_.setInt(this.getClass(), DATASET2, dataSet2ComboBox_.getSelectedIndex());
   }

}
package org.micromanager.plugins.framecombiner;

import java.text.NumberFormat;
import javax.swing.text.NumberFormatter;


import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.internal.utils.MMFrame;

public class FrameCombinerConfigurator extends MMFrame implements ProcessorConfigurator {

   private static final String PROCESSOR_DIMENSION = "Dimension";
   private static final String PROCESSOR_ALGO = "Algorithm to apply on stack images";
   private static final String NUMBER_TO_PROCESS = "Number of images to process";
   private static final String CHANNEL_TO_AVOID = "Avoid Channel(s) (eg. 1,2 or 1-5)";

   private final Studio studio_;
   private final PropertyMap settings_;

   public FrameCombinerConfigurator(PropertyMap settings, Studio studio) {
      studio_ = studio;
      settings_ = settings;

      initComponents();
      loadSettingValue();
      super.loadAndRestorePosition(200, 200);
      
   }

   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      jPanel1 = new javax.swing.JPanel();
      jLabel4 = new javax.swing.JLabel();
      processorDimensionBox_ = new javax.swing.JComboBox();
      jLabel1 = new javax.swing.JLabel();
      NumberFormat format = NumberFormat.getInstance();
      NumberFormatter formatter = new NumberFormatter(format);
      formatter.setValueClass(Integer.class);
      formatter.setMinimum(1);
      formatter.setMaximum(Integer.MAX_VALUE);
      formatter.setCommitsOnValidEdit(true);
      formatter.setAllowsInvalid(false);
      numerOfImagesToProcessField_ = new javax.swing.JFormattedTextField(formatter);
      jLabel2 = new javax.swing.JLabel();
      processorAlgoBox_ = new javax.swing.JComboBox();
      jLabel3 = new javax.swing.JLabel();
      channelsToAvoidField_ = new javax.swing.JFormattedTextField();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("FrameCombiner Processor Configuration");

      jPanel1.setAutoscrolls(true);
      jPanel1.setLayout(new java.awt.GridLayout(4, 2, 10, 10));

      jLabel4.setText("Dimension to process");
      jPanel1.add(jLabel4);

      processorDimensionBox_.addItem(FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME);
      processorDimensionBox_.addItem(FrameCombinerPlugin.PROCESSOR_DIMENSION_Z);
      processorDimensionBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            processorDimensionBox_ActionPerformed(evt);
         }
      });
      jPanel1.add(processorDimensionBox_);

      jLabel1.setText("Number of images to process");
      jPanel1.add(jLabel1);

      numerOfImagesToProcessField_.setName("_"); // NOI18N
      jPanel1.add(numerOfImagesToProcessField_);

      jLabel2.setText("Algorithm to apply on image stack");
      jPanel1.add(jLabel2);

      processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MEAN);
      //processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MEDIAN);
      processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_SUM);
      processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MAX);
      processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MIN);
      jPanel1.add(processorAlgoBox_);

      jLabel3.setText("<html>Avoid Channel(s) (zero-based)<br/><p style=\"text-align: center;\">eg. 1,2 or 1-5 (no space)</p></html>");
      jPanel1.add(jLabel3);

      channelsToAvoidField_.setName("_"); // NOI18N
      jPanel1.add(channelsToAvoidField_);

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addContainerGap())
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addContainerGap())
      );

      pack();
   }// </editor-fold>//GEN-END:initComponents

   private void processorDimensionBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_processorDimensionBox_ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_processorDimensionBox_ActionPerformed

   private void loadSettingValue() {
      processorDimensionBox_.setSelectedItem(settings_.getString(
              "processorDimension", getProcessorDimension()));
      processorAlgoBox_.setSelectedItem(settings_.getString(
              "processorAlgo", getProcessorAglo()));
      numerOfImagesToProcessField_.setText(Integer.toString(settings_.getInt(
              "numerOfImagesToProcess", getNumerOfImagesToProcess())));
      channelsToAvoidField_.setText(settings_.getString(
              "channelsToAvoidField", getChannelsToAvoid()));
   }

   @Override
   public void showGUI() {
      pack();
      setVisible(true);
   }

   @Override
   public void cleanup() {
      dispose();
   }

   @Override
   public PropertyMap getSettings() {

      // Save preferences now.
      setProcessorAglo((String) processorAlgoBox_.getSelectedItem());
      setProcessorDimension((String) processorDimensionBox_.getSelectedItem());
      setNumerOfImagesToProcess(Integer.parseInt(numerOfImagesToProcessField_.getText()));
      setChannelsToAvoid(channelsToAvoidField_.getText());

      PropertyMap.PropertyMapBuilder builder = studio_.data().getPropertyMapBuilder();
      builder.putString("processorDimension", (String) processorDimensionBox_.getSelectedItem());
      builder.putString("processorAlgo", (String) processorAlgoBox_.getSelectedItem());
      builder.putInt("numerOfImagesToProcess", Integer.parseInt(numerOfImagesToProcessField_.getText()));
      builder.putString("channelsToAvoid", channelsToAvoidField_.getText());
      return builder.build();
   }

    private String getProcessorDimension() {
        return studio_.profile().getString(FrameCombinerConfigurator.class,
                PROCESSOR_DIMENSION, FrameCombinerPlugin.PROCESSOR_DIMENSION_Z);
    }

    private void setProcessorDimension(String processorDimension) {
        studio_.profile().setString(FrameCombinerConfigurator.class,
                PROCESSOR_DIMENSION, processorDimension);
    }

   private String getProcessorAglo() {
      return studio_.profile().getString(FrameCombinerConfigurator.class,
              PROCESSOR_ALGO, FrameCombinerPlugin.PROCESSOR_ALGO_MEAN);
   }

   private void setProcessorAglo(String processorAlgo) {
      studio_.profile().setString(FrameCombinerConfigurator.class,
              PROCESSOR_ALGO, processorAlgo);
   }

   private int getNumerOfImagesToProcess() {
      return studio_.profile().getInt(FrameCombinerConfigurator.class,
              NUMBER_TO_PROCESS, 10);
   }

   private void setNumerOfImagesToProcess(int numerOfImagesToProcess) {
      studio_.profile().setInt(FrameCombinerConfigurator.class,
              NUMBER_TO_PROCESS, numerOfImagesToProcess);
   }

   private String getChannelsToAvoid() {
      return studio_.profile().getString(FrameCombinerConfigurator.class,
              CHANNEL_TO_AVOID, "");
   }

   private void setChannelsToAvoid(String channelsToAvoid) {
      studio_.profile().setString(FrameCombinerConfigurator.class,
              CHANNEL_TO_AVOID, channelsToAvoid);
   }

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JFormattedTextField channelsToAvoidField_;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JPanel jPanel1;
   private javax.swing.JFormattedTextField numerOfImagesToProcessField_;
   private javax.swing.JComboBox processorAlgoBox_;
   private javax.swing.JComboBox processorDimensionBox_;
   // End of variables declaration//GEN-END:variables
}

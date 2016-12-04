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
   }

   @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        NumberFormat format = NumberFormat.getInstance();
        NumberFormatter formatter = new NumberFormatter(format);
        formatter.setValueClass(Integer.class);
        formatter.setMinimum(1);
        formatter.setMaximum(Integer.MAX_VALUE);
        formatter.setCommitsOnValidEdit(true);
        formatter.setAllowsInvalid(false);
        numerOfImagesToProcessField_ = new javax.swing.JFormattedTextField(formatter);
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        processorAlgoBox_ = new javax.swing.JComboBox();
        processorDimensionBox_ = new javax.swing.JComboBox();
        channelsToAvoidField_ = new javax.swing.JFormattedTextField();
        jLabel3 = new javax.swing.JLabel();
       jLabel4 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("FrameCombiner Processor Configuration");

        numerOfImagesToProcessField_.setName("_"); // NOI18N

        jLabel1.setText("<html>Number of images to process<br>( &lt; total frame/slices if using MDA)</html>");

        jLabel2.setText("Algorithm to apply on image stack");

       //no channel option, because mm2 already provides a channel overlapping view option
       processorDimensionBox_.addItem(FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME);
       processorDimensionBox_.addItem(FrameCombinerPlugin.PROCESSOR_DIMENSION_Z);

        processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MEAN);
        //processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MEDIAN);
        processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_SUM);
        processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MAX);
        processorAlgoBox_.addItem(FrameCombinerPlugin.PROCESSOR_ALGO_MIN);

        channelsToAvoidField_.setName("_"); // NOI18N

        jLabel3.setText("<html>Avoid Channel(s) (zero-based)<br/><p style=\"text-align: center;\">eg. 1,2 or 1-5 (no space)</p></html>");

        jLabel4.setText("Dimension to process");
        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(numerOfImagesToProcessField_)
                            .addComponent(channelsToAvoidField_))
                        .addContainerGap(156, Short.MAX_VALUE))
                        .addComponent(processorDimensionBox_)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(processorAlgoBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(139, Short.MAX_VALUE))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(processorDimensionBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(processorAlgoBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(numerOfImagesToProcessField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(channelsToAvoidField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.DEFAULT_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

//        pack();
    }// </editor-fold>//GEN-END:initComponents

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
    // End of variables declaration//GEN-END:variables

    private javax.swing.JComboBox processorDimensionBox_;
}

package org.micromanager.plugins.positionsplitter;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.internal.utils.MMFrame;

public class PositionSplitterConfigurator extends MMFrame implements ProcessorConfigurator {

        private final Studio studio_;
        private final PropertyMap settings_;

        public PositionSplitterConfigurator(PropertyMap settings, Studio studio) {
                studio_ = studio;
                settings_ = settings;

                initComponents();
        }

        @SuppressWarnings("unchecked")
        // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
        private void initComponents() {

                jLabel1 = new javax.swing.JLabel();

                setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
                setTitle("Position Splitter Configuration");

                jLabel1.setText("Test");

                javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
                getContentPane().setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGap(172, 172, 172)
                                .addComponent(jLabel1)
                                .addContainerGap(224, Short.MAX_VALUE))
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGap(69, 69, 69)
                                .addComponent(jLabel1)
                                .addContainerGap(72, Short.MAX_VALUE))
                );

                pack();
        }// </editor-fold>//GEN-END:initComponents

        @Override
        public void showGUI() {
                pack();
                setVisible(true);
        }

        @Override
        public void cleanup() {
                dispose();
        }

        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JLabel jLabel1;
        // End of variables declaration//GEN-END:variables

        @Override
        public PropertyMap getSettings() {
                PropertyMap.PropertyMapBuilder builder = studio_.data().getPropertyMapBuilder();
                return builder.build();
        }
}

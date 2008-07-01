package com.imaging100x.hcs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;

public class PlateEditor extends JFrame {
   private JTextField spacingField_;
   private JTextField columnsField_;
   private JTextField rowsField_;
   private JComboBox comboBox;
   private static final long serialVersionUID = 1L;
   private SpringLayout springLayout;
   private SBSPlate plate_;
   private PlatePanel platePanel_;
   
  public static void main(String args[]) {
      try {
         PlateEditor frame = new PlateEditor();
         frame.setVisible(true);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   /**
    * Create the frame
    */
   public PlateEditor() {
      super();
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      plate_ = new SBSPlate();
      
      setTitle("HCS plate editor");
      setBounds(100, 100, 654, 448);
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      platePanel_ = new PlatePanel(plate_);
      getContentPane().add(platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, platePanel_, -136, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, platePanel_, 5, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, platePanel_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, platePanel_, 5, SpringLayout.NORTH, getContentPane());

      final JButton customButton = new JButton();
      customButton.setText("Custom...");
      getContentPane().add(customButton);
      springLayout.putConstraint(SpringLayout.EAST, customButton, -11, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, customButton, -116, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, customButton, 86, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, customButton, 60, SpringLayout.NORTH, getContentPane());

      comboBox = new JComboBox();
      getContentPane().add(comboBox);
      comboBox.addItem(SBSPlate.SBS_96_WELL);
      comboBox.addItem(SBSPlate.SBS_384_WELL);
      springLayout.putConstraint(SpringLayout.EAST, comboBox, -10, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, comboBox, -116, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, comboBox, 55, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, comboBox, 30, SpringLayout.NORTH, getContentPane());
      comboBox.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            plate_.initialize((String)comboBox.getSelectedItem());
            PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()), 
                                               Double.parseDouble(spacingField_.getText()));
            platePanel_.refreshImagingSites(sites);
            platePanel_.repaint();
         }
      });

      final JLabel plateFormatLabel = new JLabel();
      plateFormatLabel.setText("Plate format");
      getContentPane().add(plateFormatLabel);
      springLayout.putConstraint(SpringLayout.EAST, plateFormatLabel, 0, SpringLayout.EAST, comboBox);
      springLayout.putConstraint(SpringLayout.WEST, plateFormatLabel, 5, SpringLayout.WEST, comboBox);
      springLayout.putConstraint(SpringLayout.NORTH, plateFormatLabel, 5, SpringLayout.NORTH, getContentPane());

      rowsField_ = new JTextField();
      rowsField_.setText("1");
      getContentPane().add(rowsField_);
      springLayout.putConstraint(SpringLayout.EAST, rowsField_, -76, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rowsField_, -116, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, rowsField_, 175, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rowsField_, 155, SpringLayout.NORTH, getContentPane());

      final JLabel imagingSitesLabel = new JLabel();
      imagingSitesLabel.setText("Imaging Sites");
      getContentPane().add(imagingSitesLabel);
      springLayout.putConstraint(SpringLayout.EAST, imagingSitesLabel, 0, SpringLayout.EAST, customButton);
      springLayout.putConstraint(SpringLayout.WEST, imagingSitesLabel, 0, SpringLayout.WEST, rowsField_);
      springLayout.putConstraint(SpringLayout.NORTH, imagingSitesLabel, 115, SpringLayout.NORTH, getContentPane());

      columnsField_ = new JTextField();
      columnsField_.setText("1");
      getContentPane().add(columnsField_);
      springLayout.putConstraint(SpringLayout.SOUTH, columnsField_, 175, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, columnsField_, 155, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, columnsField_, -31, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, columnsField_, -71, SpringLayout.EAST, getContentPane());

      spacingField_ = new JTextField();
      spacingField_.setText("20");
      getContentPane().add(spacingField_);
      springLayout.putConstraint(SpringLayout.EAST, spacingField_, -76, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingField_, -116, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, spacingField_, 220, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingField_, 200, SpringLayout.NORTH, getContentPane());

      final JLabel rowsColumnsLabel = new JLabel();
      rowsColumnsLabel.setText("Rows, Columns");
      getContentPane().add(rowsColumnsLabel);
      springLayout.putConstraint(SpringLayout.SOUTH, rowsColumnsLabel, 152, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rowsColumnsLabel, 136, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, rowsColumnsLabel, -26, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rowsColumnsLabel, -116, SpringLayout.EAST, getContentPane());

      final JLabel spacingLabel = new JLabel();
      spacingLabel.setText("Spacing [um]");
      getContentPane().add(spacingLabel);
      springLayout.putConstraint(SpringLayout.SOUTH, spacingLabel, 196, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingLabel, 180, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, spacingLabel, -31, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingLabel, -116, SpringLayout.EAST, getContentPane());

      final JButton refreshButton = new JButton();
      refreshButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()), 
                  Double.parseDouble(spacingField_.getText()));
            plate_.initialize((String)comboBox.getSelectedItem());
            platePanel_.refreshImagingSites(sites);
            platePanel_.repaint();
         }
      });
      refreshButton.setText("Refresh");
      getContentPane().add(refreshButton);
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 250, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, 106, SpringLayout.WEST, spacingField_);
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, 0, SpringLayout.WEST, spacingField_);
      //
   }
   
   private PositionList generateSites(int rows, int cols, double spacing) {
      PositionList sites = new PositionList();
      for (int i=0; i<rows; i++)
         for (int j=0; j<cols; j++) {
            double x;
            double y;
            if (cols > 1)
               x = - cols * spacing /2.0 + spacing*j;
            else
               x = 0.0;
            
            if (rows > 1)
               y = - rows * spacing/2.0 + spacing*i;
            else
               y = 0.0;
            
            MultiStagePosition mps = new MultiStagePosition();
            StagePosition sp = new StagePosition();
            sp.numAxes = 2;
            sp.x = x;
            sp.y = y;
            System.out.println("("+i+","+j+") = " + x + "," + y);
            
            mps.add(sp);
            sites.addPosition(mps);            
         }
            
      return sites;
   }

}

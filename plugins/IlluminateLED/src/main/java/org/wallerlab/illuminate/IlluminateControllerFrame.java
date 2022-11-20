/*
Copyright (c) 2019, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL REGENTS OF THE UNIVERSITY OF CALIFORNIA BE LIABLE 
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.wallerlab.illuminate;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.event.ChangeEvent;
import mmcorej.CMMCore;
import mmcorej.org.json.JSONObject;

public class IlluminateControllerFrame {

   public int ledArrayDeviceIndex;
   public String comPort;
   public boolean debugFlag = true;
   public CMMCore mmCore;


   public IlluminateControllerFrame(CMMCore mmCore, String deviceName, boolean debugFlag)
         throws Exception {
      this.debugFlag = debugFlag;
      this.mmCore = mmCore;

      // Create interface
      IlluminateInterface ledArrayInterface = new IlluminateInterface(mmCore, deviceName);

      EventQueue.invokeLater(() -> {
         try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
               | UnsupportedLookAndFeelException ex) {
            Logger.getLogger(IlluminateControllerFrame.class.getName()).log(Level.WARNING,
                  null, ex);
         }

         double[][] ledPositionsNa = null;
         try {
            ledPositionsNa = ledArrayInterface.ledPositionList;
         } catch (Exception ex) {
            Logger.getLogger(IlluminateControllerFrame.class.getName()).log(Level.SEVERE,
                  null, ex);
         }

         ControlPane cFrame = new ControlPane(ledPositionsNa);

         try {
            cFrame.setLedArrayInterface(ledArrayInterface);
         } catch (Exception ex) {
            Logger.getLogger(IlluminateControllerFrame.class.getName()).log(Level.SEVERE, null, ex);
         }

         JFrame frame = new JFrame("Illuminate LED Array Controller");
         frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         frame.setLayout(new BorderLayout());
         frame.add(cFrame);
         frame.pack();
         frame.setLocationRelativeTo(null);
         frame.setVisible(true);
         frame.setResizable(true);
      });
   }

   public final class ControlPane extends JPanel {

      private LEDPlot lPlot;
      public JSONObject ledArrayJson;
      public String serialNumber;
      public String model;
      public int ledCount;
      public double zDistanceDefault;
      public double fileVersion;
      public double[][] ledPositionList;
      public double[] channelList;
      public int ledUsedCount = 0;
      public double circleSize = 0;
      private boolean debugFlag = false;
      private boolean toggled = true;
      private JButton colorChooserButton;
      private JColorChooser colorChooser;
      public GridBagConstraints c;
      int colorSelectionSize = 275;
      IlluminateInterface lInterface;
      private final Color initialColor = new Color(200, 200, 200);
      boolean[] ledMask;

      private JLabel commandLabel = new JLabel("Command:");
      private JTextField setNaTextField = new JTextField(10);
      private JTextField setArrayDistTextField = new JTextField(10);

      public double maxX;
      public double maxY;

      public void setLedArrayInterface(IlluminateInterface ledInterface) throws Exception {
         this.lInterface = ledInterface;
         setNaTextField.setText(String.valueOf(ledInterface.getNa()));
         setArrayDistTextField.setText(String.valueOf(ledInterface.getArrayDistance()));

      }

      public double[][] getLedPositionList() {
         return ledPositionList;
      }

      public double[] getLedChannelList() {
         return channelList;
      }

      public ControlPane(double[][] ledPositionsNa) {

         // Load LED array coordinates
         //loadLedArrayJson(jsonFileName);
         ledPositionList = ledPositionsNa;
         ledCount = ledPositionsNa.length;

         // Generate LED mask and solve for max/min coverage in x/y
         lPlot = new LEDPlot(ledCount);
         lPlot.setBackground(Color.DARK_GRAY);

         ledMask = new boolean[ledCount];
         maxX = -999;
         maxY = -999;
         ledUsedCount = 1;
         for (int ledIdx = 0; ledIdx < ledCount; ledIdx++) {
            ledMask[ledIdx] = true;
            if (ledMask[ledIdx]) {
               if (ledPositionList[ledIdx][0] > maxX) {
                  maxX = ledPositionList[ledIdx][0];
               }
               if (ledPositionList[ledIdx][1] > maxY) {
                  maxY = ledPositionList[ledIdx][1];
               }
               ledUsedCount++;
            }
         }
         System.out.println("Using " + Integer.toString(ledUsedCount) + " LEDs.");
         System.out.println("Max value X is " + Double.toString(maxX));
         System.out.println("Max value Y is " + Double.toString(maxY));

         lPlot.setLedCoordinates(ledPositionList, ledMask, ledUsedCount);
         lPlot.setBounds(maxX, maxY);
         lPlot.setMinimumSize(new Dimension(500, 500));

         // Set brush to red by default
         lPlot.setColor(initialColor);

         setLayout(new GridBagLayout());

         //ButtonGroup myButtonGroup = new ButtonGroup();
         //TODO: change these to toggle buttons
         JButton bfButton = new JButton("Brightfield");
         bfButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.drawBrightfield();

               updateLedMap(lPlot, lInterface);
            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         JButton dfButton = new JButton("Darkfield");
         dfButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.drawDarkfield();

               updateLedMap(lPlot, lInterface);
            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }
         });

         JButton anButton = new JButton("Fill Array");
         anButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.fillArray();

               updateLedMap(lPlot, lInterface);
            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }
            lPlot.fillArray();
         });

         JButton singleLedButton = new JButton("Center LED");
         singleLedButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.drawCenter();

               updateLedMap(lPlot, lInterface);

            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         JButton cdpcButton = new JButton("Color DPC");
         cdpcButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.drawColorDpc();

               updateLedMap(lPlot, lInterface);

            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         JButton colorDarkfieldButton = new JButton("Color Darkfield");
         colorDarkfieldButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.drawColorDarkfield();

               updateLedMap(lPlot, lInterface);

            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         JButton clearButton = new JButton("Clear");
         clearButton.addActionListener((ActionEvent e) -> {
            try {

               lInterface.clearArray();

               updateLedMap(lPlot, lInterface);
            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         JTextField commandTextField = new JTextField();
         Action action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
               if (commandTextField.getText().length() > 0) {
                  try {
                     lInterface.sendCommand(commandTextField.getText());
                  } catch (Exception ex) {
                     Logger.getLogger(IlluminateControllerFrame.class.getName())
                           .log(Level.SEVERE, null, ex);
                  }
                  commandTextField.setText("");
                  try {
                     updateLedMap(lPlot, lInterface);
                  } catch (Exception ex) {
                     Logger.getLogger(IlluminateControllerFrame.class.getName())
                           .log(Level.SEVERE, null, ex);
                  }
               }
            }
         };
         commandTextField.addActionListener(action);

         Action action2 = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
               if (setNaTextField.getText().length() > 0) {
                  double na = Double.parseDouble(setNaTextField.getText());
                  if (na <= 1.0 & na > 0.0) {
                     try {
                        lInterface.setNa(na);
                     } catch (Exception ex) {
                        Logger.getLogger(IlluminateControllerFrame.class.getName())
                              .log(Level.SEVERE, null, ex);
                     }
                  }

                  try {
                     setNaTextField.setText(Double.toString(lInterface.getNa()));
                  } catch (Exception ex) {
                     Logger.getLogger(IlluminateControllerFrame.class.getName())
                           .log(Level.SEVERE, null, ex);
                  }

                  try {
                     updateLedMap(lPlot, lInterface);
                  } catch (Exception ex) {
                     Logger.getLogger(IlluminateControllerFrame.class.getName())
                           .log(Level.SEVERE, null, ex);
                  }
               }
            }
         };
         setNaTextField.addActionListener(action2);

         Action action3 = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
               if (setArrayDistTextField.getText().length() > 0) {
                  double arrayDistance = Double.parseDouble(setArrayDistTextField.getText());
                  if (arrayDistance > 0.0) {
                     try {
                        lInterface.setArrayDistance((float) arrayDistance);
                     } catch (Exception ex) {
                        Logger.getLogger(IlluminateControllerFrame.class.getName())
                              .log(Level.SEVERE, null, ex);
                     }
                  }

                  try {
                     setArrayDistTextField.setText(Double.toString(lInterface.getArrayDistance()));
                  } catch (Exception ex) {
                     Logger.getLogger(IlluminateControllerFrame.class.getName())
                           .log(Level.SEVERE, null, ex);
                  }

                  try {
                     updateLedMap(lPlot, lInterface);
                  } catch (Exception ex) {
                     Logger.getLogger(IlluminateControllerFrame.class.getName())
                           .log(Level.SEVERE, null, ex);
                  }
               }
            }
         };
         setArrayDistTextField.addActionListener(action3);

         JButton commandSendButton = new JButton("Send");
         commandSendButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.sendCommand(commandTextField.getText());

            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         colorChooserButton = new JColorButton("Pick Color");
         colorChooserButton.addActionListener((ActionEvent arg0) -> {
            toggleColorChooser(); // show and hide the color chooser

         });

         colorChooserButton.setOpaque(true);
         colorChooserButton.setBackground(initialColor);
         colorChooserButton.setForeground(getComplementColor(initialColor));

         colorChooser = new JColorChooser(initialColor); // default color is black
         colorChooser.setBorder(null);
         colorChooser.setColor(initialColor);

         colorChooser.getSelectionModel().addChangeListener((ChangeEvent e) -> {
            Color newColor = colorChooser.getSelectionModel().getSelectedColor();
            colorChooserButton.setBackground(newColor);
            //colorChooserButton.setForeground(getComplementColor(newColor));
            if (System.getProperty("os.name").startsWith("Windows")) {
               colorChooserButton.setForeground(getComplementColor(newColor));
            } else {
               colorChooserButton.setForeground(Color.BLACK);
            }
            lPlot.setColor(newColor);

            try {
               lInterface.setColor(lPlot.getSelectedColor());
            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }
         });

         colorChooser.setPreviewPanel(new JPanel());
         colorChooser.setVisible(true);

         // Color chooser buttons
         JColorButton colorWhiteButton = new JColorButton("White");
         colorWhiteButton.addActionListener((ActionEvent e) -> {
            if (debugFlag) {
               System.out.println("White Button clicked");

            }
            lPlot.setColor(Color.WHITE);
            try {
               lInterface.setColor(lPlot.getSelectedColor());
            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }
         });

         JButton dpcBottomButton = new JButton("DPC Bottom");
         dpcBottomButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.drawDpc("Bottom");

               updateLedMap(lPlot, lInterface);

            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         JButton dpcRightButton = new JButton("DPC Right");
         dpcRightButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.drawDpc("Right");

               updateLedMap(lPlot, lInterface);

            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         JButton dpcTopButton = new JButton("DPC Top");
         dpcTopButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.drawDpc("Top");

               updateLedMap(lPlot, lInterface);

            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         JButton dpcLeftButton = new JButton("DPC Left");
         dpcLeftButton.addActionListener((ActionEvent e) -> {
            try {
               lInterface.setColor(lPlot.getSelectedColor());
               lInterface.drawDpc("Left");

               updateLedMap(lPlot, lInterface);

            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }

         });

         colorWhiteButton.setColor(Color.WHITE);
         colorWhiteButton.setForeground(getComplementColor(colorWhiteButton.getColor()));

         JColorButton colorRedButton = new JColorButton("Red");
         colorRedButton.addActionListener((ActionEvent e) -> {
            if (debugFlag) {
               System.out.println("Red Button clicked");

            }
            lPlot.setColor(Color.RED);
            try {
               lInterface.setColor(lPlot.getSelectedColor());
            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }
         });
         colorRedButton.setColor(Color.RED);
         if (System.getProperty("os.name").startsWith("Windows")) {
            colorRedButton.setForeground(getComplementColor(colorRedButton.getColor()));
         } else {
            colorRedButton.setForeground(Color.BLACK);
         }

         JColorButton colorGreenButton = new JColorButton("Green");
         colorGreenButton.addActionListener((ActionEvent e) -> {
            if (debugFlag) {
               System.out.println("Green Button clicked");
            }
            lPlot.setColor(Color.GREEN);
            try {
               lInterface.setColor(lPlot.getSelectedColor());
            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }
         });
         colorGreenButton.setColor(Color.GREEN);
         if (System.getProperty("os.name").startsWith("Windows")) {
            colorGreenButton.setForeground(getComplementColor(colorGreenButton.getColor()));
         } else {
            colorGreenButton.setForeground(Color.BLACK);
         }

         JColorButton colorBlueButton = new JColorButton("Blue");
         colorBlueButton.addActionListener((ActionEvent e) -> {
            if (debugFlag) {
               System.out.println("Blue Button clicked");
            }
            lPlot.setColor(Color.BLUE);
            try {
               lInterface.setColor(lPlot.getSelectedColor());
            } catch (Exception ex) {
               Logger.getLogger(IlluminateControllerFrame.class.getName())
                     .log(Level.SEVERE, null, ex);
            }
         });
         colorBlueButton.setColor(Color.BLUE);
         if (System.getProperty("os.name").startsWith("Windows")) {
            colorBlueButton.setForeground(getComplementColor(colorBlueButton.getColor()));
         } else {
            colorBlueButton.setForeground(Color.BLACK);
         }

         lPlot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

               // Center pixel coordinates
               int x = e.getX() - lPlot.getWidth() / 2;
               int y = e.getY() - lPlot.getHeight() / 2;

               // Scale to LED coordinates
               double xs = lPlot.pxToLedCoords(x);
               double ys = lPlot.pxToLedCoords(y);

               // Convert circle size in px to led coordinates
               double circleSizeLedCoords =
                     lPlot.circleSize / lPlot.ledCoordsToPixel / 2; // Convert to radius

               if (debugFlag) {
                  System.out.println("x: " + x + "," + y);
                  System.out.println("xs:" + xs + "," + ys);

                  System.out.println("xl: " + ledPositionList[3][0] + "," + ledPositionList[3][1]);
                  System.out.println("circ: " + circleSizeLedCoords);
               }

               for (int lIdx = 0; lIdx < ledMask.length; lIdx++) {
                  if (ledMask[lIdx] & (Math.sqrt(Math.pow(ledPositionList[lIdx][0] - xs, 2)
                        + Math.pow(ledPositionList[lIdx][1] - ys, 2)) <= circleSizeLedCoords)) {
                     if (debugFlag) {
                        System.out.println("Clicked LED " + Integer.toString(lIdx + 1));
                     }
                     lPlot.setLedValue(lIdx);
                     try {
                        lInterface.drawLedList(Arrays.asList(lIdx));

                     } catch (Exception ex) {
                        Logger.getLogger(IlluminateControllerFrame.class.getName())
                              .log(Level.SEVERE, null, ex);
                     }
                  }
               }
               try {
                  updateLedMap(lPlot, lInterface);
               } catch (Exception ex) {
                  Logger.getLogger(IlluminateControllerFrame.class.getName())
                        .log(Level.SEVERE, null, ex);
               }
               try {
                  updateLedMap(lPlot, lInterface);
               } catch (Exception ex) {
                  Logger.getLogger(IlluminateControllerFrame.class.getName())
                        .log(Level.SEVERE, null, ex);
               }
               lPlot.repaint();
            }
         });

         lPlot.addMouseMotionListener(new MouseAdapter() {
            @Override //I override only one method for presentation
            public void mouseDragged(MouseEvent e) {

               try {
                  lInterface.setColor(lPlot.getSelectedColor());
               } catch (Exception ex) {
                  Logger.getLogger(IlluminateControllerFrame.class.getName())
                        .log(Level.SEVERE, null, ex);
               }

               // Center pixel coordinates
               int x = e.getX() - lPlot.getWidth() / 2;
               int y = e.getY() - lPlot.getHeight() / 2;

               // Scale to LED coordinates
               double xs = lPlot.pxToLedCoords(x);
               double ys = lPlot.pxToLedCoords(y);

               // Convert circle size in px to led coordinates
               double circleSizeLedCoords =
                     lPlot.circleSize / lPlot.ledCoordsToPixel / 2; // Convert to radius

               if (debugFlag) {
                  System.out.println("x: " + x + "," + y);
                  System.out.println("xs:" + xs + "," + ys);

                  System.out.println("xl: " + ledPositionList[3][0] + "," + ledPositionList[3][1]);
                  System.out.println("circ: " + circleSizeLedCoords);
               }

               for (int lIdx = 0; lIdx < ledMask.length; lIdx++) {
                  if (ledMask[lIdx] & (Math.sqrt(Math.pow(ledPositionList[lIdx][0] - xs, 2)
                        + Math.pow(ledPositionList[lIdx][1] - ys, 2)) <= circleSizeLedCoords)) {
                     if (debugFlag) {
                        System.out.println("Clicked LED " + Integer.toString(lIdx + 1));
                     }
                     lPlot.setLedValue(lIdx);
                     try {
                        lInterface.drawLedList(Arrays.asList(lIdx));

                     } catch (Exception ex) {
                        Logger.getLogger(IlluminateControllerFrame.class.getName())
                              .log(Level.SEVERE, null, ex);
                     }
                  }
               }
               try {
                  updateLedMap(lPlot, lInterface);
               } catch (Exception ex) {
                  Logger.getLogger(IlluminateControllerFrame.class.getName())
                        .log(Level.SEVERE, null, ex);
               }
               lPlot.repaint();
            }
         });

         setLayout(new GridBagLayout());
         c = new GridBagConstraints();

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 0;
         c.gridy = 0;
         c.gridwidth = 6;
         c.anchor = GridBagConstraints.EAST;
         add(commandLabel, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 1;
         c.gridy = 0;
         c.gridwidth = 5;
         c.anchor = GridBagConstraints.CENTER;
         add(commandTextField, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.gridwidth = 1;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 6;
         c.gridy = 0;
         c.anchor = GridBagConstraints.CENTER;
         add(commandSendButton, c);

         // LED Plot
         c.fill = GridBagConstraints.HORIZONTAL;
         c.ipady = 0;      //make this component tall
         c.weightx = 0.5;
         c.weighty = 1;
         c.gridwidth = 7;
         c.gridx = 0;
         c.gridy = 1;
         c.weighty = 0.9;
         lPlot.setPreferredSize((new Dimension(getWidth(), getWidth())));
         add(lPlot, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.gridwidth = 1;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 0;
         c.gridy = 2;
         c.anchor = GridBagConstraints.CENTER;
         add(clearButton, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.gridwidth = 1;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 1;
         c.gridy = 2;
         c.anchor = GridBagConstraints.CENTER;
         add(bfButton, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 2;
         c.gridy = 2;
         c.anchor = GridBagConstraints.CENTER;
         add(dfButton, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 3;
         c.gridy = 2;
         c.anchor = GridBagConstraints.CENTER;
         add(dpcTopButton, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 4;
         c.gridy = 2;
         c.anchor = GridBagConstraints.CENTER;
         add(dpcBottomButton, c);

         final JLabel setArrayDistLabel = new JLabel("Z:");
         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 5;
         c.gridy = 2;
         c.anchor = GridBagConstraints.CENTER;
         add(setArrayDistLabel, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 6;
         c.gridy = 2;
         c.gridwidth = 1;
         c.anchor = GridBagConstraints.CENTER;
         add(setArrayDistTextField, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 0;
         c.gridy = 3;
         c.anchor = GridBagConstraints.CENTER;
         add(colorChooserButton, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 1;
         c.gridy = 3;
         c.anchor = GridBagConstraints.CENTER;
         add(colorDarkfieldButton, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 2;
         c.gridy = 3;
         c.anchor = GridBagConstraints.CENTER;
         add(cdpcButton, c);


         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 3;
         c.gridy = 3;
         c.anchor = GridBagConstraints.CENTER;
         add(dpcLeftButton, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 4;
         c.gridy = 3;
         c.anchor = GridBagConstraints.CENTER;
         add(dpcRightButton, c);

         final JLabel setNaLabel = new JLabel("NA:");
         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 5;
         c.gridy = 3;
         c.anchor = GridBagConstraints.CENTER;
         add(setNaLabel, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 6;
         c.gridy = 3;
         c.gridwidth = 1;
         c.anchor = GridBagConstraints.CENTER;
         add(setNaTextField, c);

         c.fill = GridBagConstraints.HORIZONTAL;
         c.weightx = 0.5;
         c.weighty = 0.0;
         c.gridx = 0;
         c.gridy = 4;
         c.gridwidth = 1;
         c.anchor = GridBagConstraints.CENTER;
         add(colorChooser, c);

         // Remove extra color chooser panels
         final AbstractColorChooserPanel[] panels = this.colorChooser.getChooserPanels();
         for (final AbstractColorChooserPanel accp : panels) {
            if (!accp.getDisplayName().equals("HSV")) {
               this.colorChooser.removeChooserPanel(accp);
            }
         }

         colorChooser.setVisible(false);
         toggled = false;

      }

      public void updateLedMap(LEDPlot lPlot, IlluminateInterface lInterface) throws Exception {
         int[][] ledVals8Bit;
         ledVals8Bit = lInterface.getLedIntensities();

         lPlot.clearArray();
         for (int ledIndex = 0; ledIndex < lInterface.ledCount; ledIndex++) {
            lPlot.setLedValue(ledIndex, new Color(ledVals8Bit[ledIndex][0],
                  ledVals8Bit[ledIndex][1],
                  ledVals8Bit[ledIndex][2]));
         }
         lPlot.repaint();
      }

      protected void toggleColorChooser() {
         if (toggled) {

            JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
            colorChooser.setVisible(false);
         } else {
            JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
            colorChooser.setVisible(true);
         }
         toggled = !toggled;
         validate();
         repaint();
      }

      public Color getComplementColor(Color newColor) {
         double y =
               (299 * newColor.getRed() + 587 * newColor.getGreen() + 114 * newColor.getBlue())
                     / 1000;
         return y >= 128 ? Color.black : Color.white;
      }
   }

   private class JColorButton extends JButton {

      private Color selectedColor;

      private JColorButton(String label) {
         super(label);
         setContentAreaFilled(false);
         setFocusPainted(false);
         selectedColor = Color.BLACK;
      }

      @Override
      protected void paintComponent(Graphics g) {
         final Graphics2D g2 = (Graphics2D) g.create();
         g2.setPaint(new GradientPaint(
               new Point(0, 0),
               selectedColor,
               new Point(0, getHeight()),
               selectedColor));

         g2.fillRect(0, 0, getWidth(), getHeight());
         g2.dispose();

         super.paintComponent(g);
      }

      public JColorButton newInstance(String label) {

         return new JColorButton(label);
      }

      public void setColor(Color newColor) {
         selectedColor = newColor;

      }

      public Color getColor() {
         return selectedColor;
      }

   }

   public class LEDPlot extends JPanel {

      private double[][] ledCoords;
      private int[][] ledValues8Bit;
      private boolean[] ledMask;
      private double minX;
      private double minY;
      private double maxX;
      private double maxY;
      private int ledUsedCount;
      public int circleSize;
      public double paddingFactor = 0.8;
      public double ledCoordsToPixel;
      private Color selectedColor;

      public LEDPlot(int ledCount) {
         ledValues8Bit = new int[ledCount][3];

      }

      public void setLedCoordinates(double[][] ledCoords, boolean[] ledMask, int ledUsedCount) {
         this.ledCoords = ledCoords;
         this.ledMask = ledMask;
         this.ledUsedCount = ledUsedCount;
      }

      public void setBounds(double maxX, double maxY) {
         this.maxX = maxX;
         this.maxY = maxY;
      }

      @Override
      public Dimension getPreferredSize() {
         return new Dimension(600, 500);
      }

      public int ledCoordsToPixels(double ledCoord, int wh) {
         return (int) (Math.round(ledCoord * ledCoordsToPixel)) + wh / 2 - circleSize / 2;
      }

      public double pxToLedCoords(int px) {
         return (double) px / ledCoordsToPixel;
      }

      public void clearArray() {
         for (int lIdx = 0; lIdx < ledCoords.length; lIdx++) {

            if (ledMask[lIdx]) {
               ledValues8Bit[lIdx][0] = 0;
               ledValues8Bit[lIdx][1] = 0;
               ledValues8Bit[lIdx][2] = 0;
            }

         }
         repaint();
      }

      public void fillArray() {
         for (int lIdx = 0; lIdx < ledCoords.length; lIdx++) {

            if (ledMask[lIdx]) {
               ledValues8Bit[lIdx][0] = selectedColor.getRed();
               ledValues8Bit[lIdx][1] = selectedColor.getGreen();
               ledValues8Bit[lIdx][2] = selectedColor.getBlue();
            }
         }
         repaint();
      }

      public void setLedValue(int lIdx) {
         if (ledMask[lIdx]) {
            ledValues8Bit[lIdx][0] = selectedColor.getRed();
            ledValues8Bit[lIdx][1] = selectedColor.getGreen();
            ledValues8Bit[lIdx][2] = selectedColor.getBlue();
         }

      }

      public void setLedValue(int lIdx, Color newColor) {
         if (ledMask[lIdx]) {
            ledValues8Bit[lIdx][0] = newColor.getRed();
            ledValues8Bit[lIdx][1] = newColor.getGreen();
            ledValues8Bit[lIdx][2] = newColor.getBlue();
         }

      }

      public void setColor(Color newColor) {
         selectedColor = newColor;
      }

      public Color getSelectedColor() {
         return (selectedColor);
      }

      @Override
      protected void paintComponent(Graphics g) {

         super.paintComponent(g);
         Graphics2D g2d = (Graphics2D) g.create();
         Color myColor;

         // Base circle Size on number of LEDs in window and window size
         int wh = Math.min(getWidth(), getHeight());
         circleSize = (int) Math.round(paddingFactor * wh / Math.sqrt(ledUsedCount));
         for (int lIdx = 0; lIdx < ledCoords.length; lIdx++) {

            ledCoordsToPixel = wh / 2 * paddingFactor / maxX;

            int xPx = ledCoordsToPixels(ledCoords[lIdx][0], getWidth());
            int yPx = ledCoordsToPixels(ledCoords[lIdx][1], getHeight());

            if (ledMask[lIdx]) {
               myColor = new Color(ledValues8Bit[lIdx][0], ledValues8Bit[lIdx][1],
                     ledValues8Bit[lIdx][2]);
               g2d.setColor(myColor);
               if (ledValues8Bit[lIdx][0] + ledValues8Bit[lIdx][1] + ledValues8Bit[lIdx][2]
                     > 0) {
                  g2d.fillOval(xPx, yPx, circleSize, circleSize);
               }

               g2d.setColor(Color.GRAY);
               g2d.drawOval(xPx, yPx, circleSize, circleSize);

            }

         }
         g2d.dispose();
      }

   }
}

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;


public class BleachControlDlg extends JDialog {
   
   private JTextField framesPreField_;
   private JTextField intervalPreField_;
   private JTextField namePrefix_;
   private JTextField dirRoot_;
   private JTextField bleachDurationField_;
   private JTextField framesField_;
   private JTextField exposureField_;
   private JTextField intervalField_;
   private double intervalMs_ = 5000.0;
   private double exposureMs_ = 100.0;
   private int numFrames_ = 10;
   private int numPreFrames_ = 2;
   private double bleachDurationMs_ = 3000.0;
   private double intervalPreMs_ = 1000.0;
   private CMMCore core_;
   private JLabel statusLabel_;
   private BleachThread bleachThread_;

   
   /**
    * Create the dialog
    */
   public BleachControlDlg(CMMCore core) {
      super();
      core_ = core;

      setTitle("MM Bleach Control");
      setResizable(false);
      getContentPane().setLayout(null);
      setBounds(100, 100, 472, 367);

      final JLabel intervalmsLabel = new JLabel();
      intervalmsLabel.setText("Interval [ms]");
      intervalmsLabel.setBounds(5, 162, 76, 14);
      getContentPane().add(intervalmsLabel);

      intervalField_ = new JTextField();
      intervalField_.setBounds(105, 159, 91, 19);
      getContentPane().add(intervalField_);

      final JButton startButton = new JButton();
      startButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            start();
         }
      });
      startButton.setText("Start");
      startButton.setBounds(363, 133, 93, 23);
      getContentPane().add(startButton);

      final JButton closeButton = new JButton();
      closeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            stop();
            dispose();
         }
      });
      closeButton.setText("Close");
      closeButton.setBounds(363, 37, 93, 23);
      getContentPane().add(closeButton);

      final JLabel pixelSizeumLabel = new JLabel();
      pixelSizeumLabel.setText("Exposure [ms]");
      pixelSizeumLabel.setBounds(5, 208, 93, 14);
      getContentPane().add(pixelSizeumLabel);

      exposureField_ = new JTextField();
      exposureField_.setBounds(105, 205, 91, 19);
      getContentPane().add(exposureField_);

      final JLabel offsetLabel = new JLabel();
      offsetLabel.setText("Frames");
      offsetLabel.setBounds(5, 185, 63, 14);
      getContentPane().add(offsetLabel);

      framesField_ = new JTextField();
      framesField_.setBounds(105, 183, 91, 19);
      getContentPane().add(framesField_);

      final JLabel bleachExposureLabel = new JLabel();
      bleachExposureLabel.setText("Duration [ms]");
      bleachExposureLabel.setBounds(5, 108, 93, 14);
      getContentPane().add(bleachExposureLabel);

      bleachDurationField_ = new JTextField();
      bleachDurationField_.setBounds(105, 105, 91, 19);
      getContentPane().add(bleachDurationField_);

      final JLabel bleachingLabel = new JLabel();
      bleachingLabel.setFont(new Font("", Font.BOLD, 12));
      bleachingLabel.setText("Bleaching");
      bleachingLabel.setBounds(5, 88, 186, 14);
      getContentPane().add(bleachingLabel);

      final JLabel bleachingLabel_1 = new JLabel();
      bleachingLabel_1.setFont(new Font("Dialog", Font.BOLD, 12));
      bleachingLabel_1.setText("Post-bleaching time-lapse");
      bleachingLabel_1.setBounds(5, 137, 186, 14);
      getContentPane().add(bleachingLabel_1);

      final JButton stopButton = new JButton();
      stopButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            stop();
         }
      });
      stopButton.setText("Stop");
      stopButton.setBounds(363, 158, 93, 23);
      getContentPane().add(stopButton);

      statusLabel_ = new JLabel();
      statusLabel_.setBorder(new LineBorder(Color.black, 1, false));
      statusLabel_.setBounds(5, 311, 451, 14);
      getContentPane().add(statusLabel_);
      //
      
      final JLabel rootDirectoryLabel = new JLabel();
      rootDirectoryLabel.setText("Directory root");
      rootDirectoryLabel.setBounds(5, 248, 101, 14);
      getContentPane().add(rootDirectoryLabel);

      dirRoot_ = new JTextField();
      dirRoot_.setBounds(105, 243, 286, 20);
      getContentPane().add(dirRoot_);

      final JLabel namePrefixLabel = new JLabel();
      namePrefixLabel.setText("Name prefix");
      namePrefixLabel.setBounds(5, 273, 101, 14);
      getContentPane().add(namePrefixLabel);

      namePrefix_ = new JTextField();
      namePrefix_.setBounds(105, 270, 286, 20);
      getContentPane().add(namePrefix_);

      final JButton button = new JButton();
      button.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            browse();
         }
      });
      button.setText("...");
      button.setBounds(412, 244, 44, 23);
      getContentPane().add(button);
      
      JLabel bleachingLabel_1_1 = new JLabel();
      bleachingLabel_1_1.setFont(new Font("Dialog", Font.BOLD, 12));
      bleachingLabel_1_1.setText("Pre-bleaching time-lapse");
      bleachingLabel_1_1.setBounds(5, 0, 186, 14);
      getContentPane().add(bleachingLabel_1_1);

      JLabel intervalmsLabel_1 = new JLabel();
      intervalmsLabel_1.setText("Interval [ms]");
      intervalmsLabel_1.setBounds(5, 25, 76, 14);
      getContentPane().add(intervalmsLabel_1);

      intervalPreField_ = new JTextField();
      intervalPreField_.setText("5000.0");
      intervalPreField_.setBounds(105, 22, 91, 19);
      getContentPane().add(intervalPreField_);

      JLabel offsetLabel_1 = new JLabel();
      offsetLabel_1.setText("Frames");
      offsetLabel_1.setBounds(5, 48, 63, 14);
      getContentPane().add(offsetLabel_1);

      framesPreField_ = new JTextField();
      framesPreField_.setText("10");
      framesPreField_.setBounds(105, 46, 91, 19);
      getContentPane().add(framesPreField_);
      
      initializeGUI();

  }
   
   private void initializeGUI() {
      intervalField_.setText(Double.toString(intervalMs_));
      intervalPreField_.setText(Double.toString(intervalPreMs_));
      exposureField_.setText(Double.toString(exposureMs_));
      framesField_.setText(Integer.toString(numFrames_));
      bleachDurationField_.setText(Double.toString(bleachDurationMs_)); 
      framesPreField_.setText(Integer.toString(numPreFrames_));
      dirRoot_.setText("C:/mm_frap_data");
      namePrefix_.setText("experiment");
   }

   /**
    * Stop acquisition.
    */
   protected void stop() {
      if (bleachThread_ != null) {
         
         bleachThread_.stopAcquisition();
         bleachThread_.interrupt();
         
         // wait until the thread is finished
         try {
            while (bleachThread_.isAlive())
               Thread.sleep(100);
         } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }
   }

   /**
    * Start acquisition.
    */
   private void start() {
      try {
         intervalMs_ = Double.parseDouble(intervalField_.getText());
         exposureMs_ = Double.parseDouble(exposureField_.getText());
         numFrames_ = Integer.parseInt(framesField_.getText());
         bleachDurationMs_ = Double.parseDouble(bleachDurationField_.getText());
         intervalPreMs_ = Double.parseDouble(intervalPreField_.getText());
         numPreFrames_ = Integer.parseInt(framesPreField_.getText());
      } catch (NumberFormatException e) {
         handleError(e.getMessage());
         return;
      }
      
      bleachThread_ = new BleachThread(core_, this, dirRoot_.getText(), namePrefix_.getText());
      bleachThread_.bleachDurationMs_ = bleachDurationMs_;
      bleachThread_.exposureMs_ = exposureMs_;
      bleachThread_.intervalMs_ = intervalMs_;
      bleachThread_.intervalPreMs_ = intervalPreMs_;
      bleachThread_.numFrames_ = numFrames_;
      bleachThread_.numPreFrames_ = numPreFrames_;
      
      bleachThread_.start();
   }

   public void displayImage(Object img) {
      //ImagePlus implus = WindowManager.getImage(MMStudioMainFrame.LIVE_WINDOW_TITLE);
      ImagePlus implus = MMStudioMainFrame.getLiveWin().getImagePlus();
      if (implus == null) {
         return;
      }
      // display image
      implus.getProcessor().setPixels(img);
      implus.updateAndDraw();
      ImageWindow iwnd = implus.getWindow();
      iwnd.getCanvas().paint(iwnd.getCanvas().getGraphics());
   }

   private void handleError(String message) {
      JOptionPane.showMessageDialog(this, message);     
   }

   public void displayMessage(String txt) {
      statusLabel_.setText(txt);
   }
   
   /**
    * Choose the root directory to save files.
    */
   protected void browse() {
      JFileChooser fc = new JFileChooser();
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      fc.setCurrentDirectory(new File(dirRoot_.getText()));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         dirRoot_.setText(fc.getSelectedFile().getAbsolutePath());
      }
   }

}

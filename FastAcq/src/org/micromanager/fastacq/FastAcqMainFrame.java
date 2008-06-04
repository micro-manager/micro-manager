package org.micromanager.fastacq;

import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Timer;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;

import org.micromanager.MMStudioMainFrame;

import mmcorej.CMMCore;


public class FastAcqMainFrame extends JFrame implements GUIStatus {

   private JLabel diskStatusLabel_;
   private JTextField namePrefix_;
   private JTextField dirRoot_;
   private JTextField intervalMs_;
   private JTextField seqLength_;
   private CMMCore core_;
   private DiskStreamingThread streamThread_;
   //private DisplayThread       dispThread_;
   private JLabel bufStatusLabel_;
   private Preferences prefs_;
   private Timer dispTimer_;
   private Timer statusTimer_;
   private ImageWindow imageWin_;
   private String cameraName_;
   
   // preference keys
   static final String NAME_PREFIX = "name_prefix";
   static final String DIR_ROOT = "dir_root";
   static final String INTERVAL_MS = "interval_ms";
   static final String SEQ_LENGTH = "sequence_length";
   static final String PANEL_X = "panel_x";
   static final String PANEL_Y = "panel_y";
   
   /**
    * Main procedure for stand alone operation.
    */
   public static void main(String args[]) {
      try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         CMMCore core = new CMMCore();
         core.loadSystemConfiguration("MMConfig_stream_andor.cfg");
         core.setExposure(1);
         FastAcqMainFrame frame = new FastAcqMainFrame(core);
         frame.setVisible(true);
         frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      } catch (Exception e) {
         e.printStackTrace();
         System.exit(1);
      }
   }
   
   /**
    * Create the frame
    */
   public FastAcqMainFrame(CMMCore core) {
      super();
      
      addWindowListener(new WindowAdapter() {
         public void windowClosed(WindowEvent arg0) {
            saveSettings();
            if (imageWin_ != null) {
               imageWin_.dispose();
            }
         }
      });
      core_ = core;
      getContentPane().setLayout(null);
      setTitle("MM Fast Acquisition Plugin");
      setResizable(false);
      
      // load preferences
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root.node(root.absolutePath() + "/settings");
      
      //setBounds(100, 100, 458, 227);
      setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      int x = prefs_.getInt(PANEL_X, 100);
      int y = prefs_.getInt(PANEL_Y, 100);
      setBounds(x, y, 458, 227);
      
      final JLabel sequenceLengthLabel = new JLabel();
      sequenceLengthLabel.setText("Sequence length");
      sequenceLengthLabel.setBounds(10, 10, 101, 14);
      getContentPane().add(sequenceLengthLabel);

      final JLabel intervalmsLabel = new JLabel();
      intervalmsLabel.setText("Interval [ms]");
      intervalmsLabel.setBounds(10, 35, 101, 14);
      getContentPane().add(intervalmsLabel);

      final JLabel rootDirectoryLabel = new JLabel();
      rootDirectoryLabel.setText("Directory root");
      rootDirectoryLabel.setBounds(10, 60, 101, 14);
      getContentPane().add(rootDirectoryLabel);

      final JLabel namePrefixLabel = new JLabel();
      namePrefixLabel.setText("Name prefix");
      namePrefixLabel.setBounds(10, 85, 101, 14);
      getContentPane().add(namePrefixLabel);

      seqLength_ = new JTextField();
      seqLength_.setBounds(115, 5, 79, 20);
      getContentPane().add(seqLength_);

      intervalMs_ = new JTextField();
      intervalMs_.setBounds(115, 30, 79, 20);
      getContentPane().add(intervalMs_);

      dirRoot_ = new JTextField();
      dirRoot_.setBounds(115, 55, 281, 20);
      getContentPane().add(dirRoot_);

      namePrefix_ = new JTextField();
      namePrefix_.setBounds(115, 82, 281, 20);
      getContentPane().add(namePrefix_);

      final JButton startButton = new JButton();
      startButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            start();
         }
      });
      startButton.setText("Start");
      startButton.setBounds(10, 165, 93, 23);
      getContentPane().add(startButton);

      final JButton stopButton = new JButton();
      stopButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            stop();
         }
      });
      stopButton.setText("Stop");
      stopButton.setBounds(105, 165, 93, 23);
      getContentPane().add(stopButton);

      final JButton exitButton = new JButton();
      exitButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (canExit()) {
               dispose();
               saveSettings();
               System.exit(0);
            } else {
               displayMessageDialog("Saving to disk not finished.\n" +
                     "Please wait until all acquired images are procesessed.");
               return;
            }
         }
      });
      exitButton.setText("Close");
      exitButton.setBounds(349, 165, 93, 23);
      getContentPane().add(exitButton);

      final JButton button = new JButton();
      button.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            browse();
         }
      });
      button.setText("...");
      button.setBounds(402, 56, 44, 23);
      getContentPane().add(button);

      final JLabel bsLabel = new JLabel();
      bsLabel.setText("Buffer status");
      bsLabel.setBounds(10, 123, 93, 14);
      getContentPane().add(bsLabel);

      bufStatusLabel_ = new JLabel();
      bufStatusLabel_.setBorder(new LineBorder(Color.black, 1, false));
      bufStatusLabel_.setBounds(115, 123, 327, 14);
      getContentPane().add(bufStatusLabel_);
      //
      
      loadSettings();

      final JLabel dsLabel = new JLabel();
      dsLabel.setText("Disk status");
      dsLabel.setBounds(10, 143, 93, 14);
      getContentPane().add(dsLabel);

      diskStatusLabel_ = new JLabel();
      diskStatusLabel_.setBorder(new LineBorder(Color.black, 1, false));
      diskStatusLabel_.setBounds(115, 143, 327, 14);
      getContentPane().add(diskStatusLabel_);
      
      // start status thread
      StatusTimerTask statusTimerTask = new StatusTimerTask(core_, this);
      statusTimer_ = new Timer();
      statusTimer_.schedule(statusTimerTask, 0, 200);
      
      final int memoryFootprintMB = 25;
      try {
         core_.setCircularBufferMemoryFootprint(memoryFootprintMB);
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }
   
   protected void browse() {
      JFileChooser fc = new JFileChooser();
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      fc.setCurrentDirectory(new File(dirRoot_.getText()));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         dirRoot_.setText(fc.getSelectedFile().getAbsolutePath());
      }
   }

   private void start() {
      cameraName_ = core_.getCameraDevice();
      if (cameraName_.length() == 0) {
         displayMessageDialog("Camera device not defined or configured.");
         return;
      }
      if (isBusy()) {
         displayMessageDialog("Acquisition still running, please wait until done.");
         return;
      }
      
      final int numFrames = Integer.parseInt(seqLength_.getText());
      final double intervalMs = Math.max(Double.parseDouble(intervalMs_.getText()), 50.0);
      
      // create window if needed
      if (imageWin_ == null || imageWin_.isClosed()) {
         openImageWindow();
      }

      // adjust dimensions if necessary
      if (imageWin_.getImagePlus().getProcessor().getWidth() != core_.getImageWidth() ||
            imageWin_.getImagePlus().getProcessor().getHeight() != core_.getImageHeight() ||
            imageWin_.getImagePlus().getBitDepth() != core_.getBytesPerPixel() * 8) {
         openImageWindow();
      }
      
      try {
         // start acquiring
         core_.startSequenceAcquisition(numFrames, intervalMs);

         // start writing to disk
         streamThread_ = new DiskStreamingThread(core_, this, dirRoot_.getText(), namePrefix_.getText());
         streamThread_.start();
         
         // start displaying
         // ----------------
                  
         DisplayTimerTask dispTimerTask = new DisplayTimerTask(core_, imageWin_);
         dispTimer_ = new Timer();
         dispTimer_.schedule(dispTimerTask, (long)intervalMs, (long)intervalMs);
         
         // start displaying
//         dispThread_ = new DisplayThread(core_);
//         dispThread_.start();
         
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }            
   }
   
   private void stop() {
      try {
         
         // stop display
//         if (dispThread_ != null && dispThread_.isAlive()) {
//            dispThread_.terminate(); // ??
//            dispThread_.join();
//         }
         if (dispTimer_ != null)
            dispTimer_.cancel();
         
//         if (statusTimer_ != null)
//            statusTimer_.cancel();
         
         // stop acquiring
         core_.stopSequenceAcquisition();
                  
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }
   
   public synchronized void displayMessage(String msg)
   {
      bufStatusLabel_.setText(msg);
   }

   private void loadSettings() {
      namePrefix_.setText(prefs_.get(NAME_PREFIX, "experiment"));
      dirRoot_.setText(prefs_.get(DIR_ROOT, "C:/mm_sequence_data"));
      intervalMs_.setText(prefs_.get(INTERVAL_MS, "100"));
      seqLength_.setText(prefs_.get(SEQ_LENGTH, "50"));
   }
   
   private void saveSettings() {
      prefs_.put(NAME_PREFIX, namePrefix_.getText());
      prefs_.put(DIR_ROOT, dirRoot_.getText());
      prefs_.put(INTERVAL_MS, intervalMs_.getText());
      prefs_.put(SEQ_LENGTH, seqLength_.getText());
      Rectangle r = getBounds();
      prefs_.putInt(PANEL_X, r.x);
      prefs_.putInt(PANEL_Y, r.y);
    }
   
   private boolean openImageWindow(){
      try {
         ImageProcessor ip;
         long byteDepth = core_.getBytesPerPixel();
         if (byteDepth == 1){
            ip = new ByteProcessor((int)core_.getImageWidth(), (int)core_.getImageHeight());
         } else if (byteDepth == 2) {
            ip = new ShortProcessor((int)core_.getImageWidth(), (int)core_.getImageHeight());
         }
         else if (byteDepth == 0) {
            return false;
         }
         else {
            return false;
         }
         ip.setColor(Color.black);
         ip.fill();
         ImagePlus imp = new ImagePlus("FastAcq window", ip);
        
         if (imageWin_ != null) {
            imageWin_.dispose();
         }
         imageWin_ = new ImageWindow(imp);                  
         
      } catch (Exception e){
         // TODO Auto-generated catch block
         e.printStackTrace();
         return false;
      }
      return true;
   }

   public void displayStreamingMessage(String msg) {
      diskStatusLabel_.setText(msg);
   }

   protected void displayMessageDialog(String message) {
      JOptionPane.showMessageDialog(this, message);     
   }


   private boolean canExit() {
      // make sure writing thread finishes
      if (streamThread_ != null && streamThread_.isAlive())
         return false;
      return true;
   }
   
   private boolean isBusy() {
      // make sure writing thread finishes
      if (streamThread_ != null && streamThread_.isAlive())
         return true;
      
      try {
         if (core_.deviceBusy(cameraName_))
            return true;
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
      return false;
      
   }
}

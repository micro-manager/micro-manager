import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.StackWindow;
import ij.plugin.PlugIn;
import ij.process.ImageStatistics;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.MMAcqDataException;

/*
 * Created on Jul 18, 2005
 * author: Nenad Amodaj
 */

/**
 * ImageJ plugin wrapper for uManager.
 */
public class MMStackReaderPlugin_ implements PlugIn {

   public void run(String arg) {
      // create and display control panel frame
      try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (ClassNotFoundException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (InstantiationException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (IllegalAccessException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (UnsupportedLookAndFeelException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      // choose the directory
      // --------------------

      JFileChooser fc = new JFileChooser();
      fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
      String openAcqDirectory = new String("");
      fc.setSelectedFile(new File(openAcqDirectory));
      int retVal = fc.showOpenDialog(IJ.getInstance().getOwner());
      if (retVal == JFileChooser.APPROVE_OPTION) {
         File f = fc.getSelectedFile();
         if (f.isDirectory()) {
            openAcqDirectory = f.getAbsolutePath();
         } else {
            openAcqDirectory = f.getParent();
         }

         AcquisitionData ad = new AcquisitionData();
         try {

            // attempt to open metafile
            ad.load(openAcqDirectory);

            // set pixels
            ImageStack is = new ImageStack(ad.getImageWidth(), ad.getImageHeight());
            for (int i=0; i<ad.getNumberOfFrames(); i++) {
               ImageProcessor ip;
               if (ad.getPixelDepth() == 1)
                  ip = new ByteProcessor(ad.getImageWidth(), ad.getImageHeight());
               else
                  ip = new ShortProcessor(ad.getImageWidth(), ad.getImageHeight());
               ip.setPixels(ad.getPixels(i, 0, 0));
               is.addSlice(String.valueOf(i), ip);
            }
            
            ImagePlus imp = new ImagePlus("Tracking", is);
               
            // pop-up image window
            StackWindow sw = new StackWindow(imp);

         } catch (MMAcqDataException e) {
            JOptionPane.showMessageDialog(IJ.getInstance().getOwner(), e.getMessage());
         }        
      }     
   }
}

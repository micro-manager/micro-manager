/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

//this class writes a simple xml file that accomponaies 

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

public class BDVXMLWriter {
   
   private RandomAccessFile raFile_;
   private final long lastTPOffset_;
   private long viewRegistrationOffset_;
   private int currentTP_ = 0;
   private int numChannels_;
   
   
//   public static void main(String[] args) throws IOException {
//      BDVXMLWriter xml = new BDVXMLWriter(new File("C:/Users/Henry/Desktop"), 6);
//      for (int i = 0; i < 5; i++) {
//         xml.addTP();
//      }
//      xml.close();
//   }
   
   public BDVXMLWriter(File dir, int numChannels) throws IOException {
      numChannels_ = numChannels;
      File xmlFile = new File(dir.getAbsolutePath() + File.separator+ "FIJI_BigDataViewer_Metadata.xml");      
      raFile_ = new RandomAccessFile(xmlFile, "rw");
      raFile_.writeBytes("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<SpimData version=\"0.2\">\n <BasePath type=\"relative\">.</BasePath>\n"
              + "<SequenceDescription>\n<ImageLoader format=\"micromagellan\">\n</ImageLoader>\n<ViewSetups>\n");
      for (int c = 0; c < numChannels; c++) {
         raFile_.writeBytes("<ViewSetup>\n<id>" + c + "</id>\n</ViewSetup>\n");
      }
      raFile_.writeBytes("</ViewSetups>\n<Timepoints type=\"range\">\n <first>0</first>\n");
      lastTPOffset_ = raFile_.getFilePointer();
      raFile_.writeBytes("<last></last>                \n</Timepoints>\n</SequenceDescription>\n<ViewRegistrations>\n");
      viewRegistrationOffset_ = raFile_.getFilePointer();
   }
   
   public synchronized void addTP() throws IOException {
      raFile_.seek(lastTPOffset_);
      //update current number of TPs
      raFile_.writeBytes("<last>" + currentTP_+"</last>");
      raFile_.seek(viewRegistrationOffset_);
      //add affine transforms
      for (int c = 0; c < numChannels_; c++) {
         raFile_.writeBytes( "<ViewRegistration timepoint=\"" + currentTP_ + "\" setup=\""+c+"\">\n"+
                 "<ViewTransform type=\"affine\">\n<affine>1 0 0 0\n0 1 0 0\n0 0 1 0</affine>\n</ViewTransform>\n"+
            "</ViewRegistration>\n");
      }
      viewRegistrationOffset_ = raFile_.getFilePointer();
      raFile_.writeBytes("</ViewRegistrations>\n</SpimData>\n");
      currentTP_++;
   }

   public synchronized void close() throws IOException {
      raFile_.close();
   }

   
   
}

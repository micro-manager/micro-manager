import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import mmcorej.TaggedImage;

import org.micromanager.acquisition.AcquisitionWrapperEngine;
import org.micromanager.acquisition.DefaultTaggedImagePipeline;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.TaggedImageAnalyzer;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.MMScriptException;


public class AcqStandalone {

   public static void main(String[] args) {
      
      SequenceSettings s = new SequenceSettings();
      
      s.numFrames = 3;
      
      s.slices = new ArrayList<Double>();
      s.slices.add(-1.0);
      s.slices.add(0.0);
      s.slices.add(1.0);     
      s.relativeZSlice = true;
      
      s.channels = new ArrayList<ChannelSpec>();
      ChannelSpec ch1 = new ChannelSpec();
      ch1.config_ = "DAPI";
      ch1.name_ = "DAPI"; // what is the difference between 'name' and 'config'?
      ch1.exposure_ = 5.0;
      s.channels.add(ch1);
      ChannelSpec ch2 = new ChannelSpec();
      ch2.config_ = "FITC";
      ch2.name_ = "FITC";
      ch2.exposure_ = 15.0;
      s.channels.add(ch2);
      
      s.prefix = "ACQ-TEST-B";
      s.root = "C:/AcquisitionData";
      
      IAcquisitionEngine2010 acqEng = null;
      try {
         Class acquisitionEngine2010Class = Class.forName("org.micromanager.AcquisitionEngine2010");
         acqEng = (IAcquisitionEngine2010) acquisitionEngine2010Class.getConstructors()[0].newInstance(null);
      } catch (ClassNotFoundException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      } catch (InstantiationException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (IllegalAccessException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (IllegalArgumentException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (InvocationTargetException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (SecurityException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
      // List<DataProcessor<TaggedImage>> imageProcessors =new List<DataProcessor<TaggedImage>>();
      // The line above does not work, because we need a specific derived class - 
      // so if we don't need any processors, we are forced to use null value
      List<DataProcessor<TaggedImage>> imageProcessors = null;
      
      try {
         DefaultTaggedImagePipeline taggedImagePipeline = new DefaultTaggedImagePipeline(
               acqEng,
               s,
               imageProcessors,
               null, // we have no GUI so we pass null?
               s.save);
         
         taggedImagePipeline.wait(); // wait for the thread to finish
         
      } catch (ClassNotFoundException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (InstantiationException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (IllegalAccessException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (MMScriptException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
   }

}

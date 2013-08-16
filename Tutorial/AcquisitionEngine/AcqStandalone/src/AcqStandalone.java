import ij.ImagePlus;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.AcquisitionWrapperEngine;
import org.micromanager.acquisition.DefaultTaggedImagePipeline;
import org.micromanager.acquisition.LiveAcq;
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.acquisition.ProcessorStack;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ImageCache;
import org.micromanager.api.TaggedImageAnalyzer;
import org.micromanager.api.TaggedImageStorage;
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

      String path = s.root + "/" + s.prefix;
      String actualPath = path;
      File f = new File(path);
      int count = 1;
      while (f.exists()) {
         actualPath = path + "_" + count;
         f = new File(actualPath);
      }

      // get hardware params
      CMMCore core = new CMMCore();
      try {
         core.loadSystemConfiguration("MMConfig_demo.cfg");
      } catch (Exception e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }
      long w = core.getImageWidth();
      long h = core.getImageHeight();
      long d = core.getBytesPerPixel();


      // create summary metadata
      JSONObject summary = new JSONObject();
      try {
         summary.put("Slices", s.slices.size());
         summary.put("Positions", 1);
         summary.put("Channels", s.channels.size());
         summary.put("Frames", s.numFrames);
         summary.put("SlicesFirst", true);
         summary.put("TimeFirst", false);

         if (d == 2) {
            summary.put("PixelType", "GRAY16");
            summary.put("IJType", ImagePlus.GRAY16);
         } else if (d==1) {
            summary.put("PixelType", "GRAY8");
            summary.put("IJType", ImagePlus.GRAY8);
         } else {
            System.out.println("Unsupported pixel type");
            return;
         }
         summary.put("Width", 512);
         summary.put("Height", 512);
         summary.put("Prefix", s.prefix); // Acquisition name

         //these are used to create display settings
         JSONArray chColors = new JSONArray();
         JSONArray chNames = new JSONArray();
         JSONArray chMins = new JSONArray();
         JSONArray chMaxs = new JSONArray();
         for (int ch=0; ch<s.channels.size(); ch++) {
            chColors.put(1);
            chNames.put(s.channels.get(ch));
            chMins.put(0);
            chMaxs.put(d == 2 ? 65535 : 255);
         }
         summary.put("ChColors", chColors);
         summary.put("ChNames", chNames);
         summary.put("ChMins", chMins);
         summary.put("ChMaxes", chMaxs);

      } catch (JSONException e2) {
         // TODO Auto-generated catch block
         e2.printStackTrace();
      }

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

      try {
         // Start up the acquisition engine
         BlockingQueue<TaggedImage> taggedImageQueue = acqEng.run(s);

         // create storage
         TaggedImageStorage storage = new TaggedImageStorageDiskDefault(actualPath, true, summary);
         MMImageCache imageCache = new MMImageCache(storage);
         
         // Start pumping images into the ImageCache
         LiveAcq liveAcq = new LiveAcq(taggedImageQueue, imageCache);
         liveAcq.start();
         
         taggedImageQueue.wait();

      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }

   }

}

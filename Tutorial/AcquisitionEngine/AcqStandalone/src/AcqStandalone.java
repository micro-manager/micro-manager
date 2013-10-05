import ij.ImagePlus;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.AcquisitionEngine2010;
import org.micromanager.acquisition.DefaultTaggedImageSink;
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.utils.ChannelSpec;


public class AcqStandalone {

   public synchronized static void main(String[] args) {
      String channelGroup = "Channel";
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
      s.channelGroup = channelGroup;

      String path = s.root + "/" + s.prefix;
      String actualPath = path;
      File f = new File(path);
      int count = 1;
      while (f.exists()) {
         actualPath = path + "_" + count++;
         f = new File(actualPath);
      }

      // get hardware params
      CMMCore core = new CMMCore();
      try {
         core.loadSystemConfiguration("MMConfig_demo.cfg");
         core.setProperty("Core",  "ChannelGroup", channelGroup);
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
      acqEng = new AcquisitionEngine2010(core);

      try {
         // Start up the acquisition engine
         PositionList posList = new PositionList();
         posList.addPosition(new MultiStagePosition());
         BlockingQueue<TaggedImage> taggedImageQueue = acqEng.run(s, true, posList, null);

         // create storage
         TaggedImageStorage storage = new TaggedImageStorageDiskDefault(actualPath, true, summary);
         MMImageCache imageCache = new MMImageCache(storage);
         
         // Start pumping images into the ImageCache
         DefaultTaggedImageSink sink = new DefaultTaggedImageSink(taggedImageQueue, imageCache);
         sink.start();
         
         TaggedImage img = null;
         do {
            img = taggedImageQueue.take();
            if (img.tags != null && img.tags.has("FrameIndex")) {
               System.out.println("Current frame index " + img.tags.getInt("FrameIndex"));
            }
            Thread.sleep(50);
         } while (img != TaggedImageQueue.POISON);

      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }

   }

}

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
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ImageCacheListener;
import org.micromanager.api.MMTags;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.SequenceSettings;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ChannelSpec;


public class AcqStandalone implements ImageCacheListener {
   
   public synchronized static void main(String[] args) {
      String channelGroup = "Channel";
      SequenceSettings s = new SequenceSettings();

      s.numFrames = 20;

      s.slices = new ArrayList<Double>();
      s.slices.add(-1.0);
      s.slices.add(0.0);
      s.slices.add(1.0);     
      s.relativeZSlice = true;

      s.channels = new ArrayList<ChannelSpec>();
      ChannelSpec ch1 = new ChannelSpec();
      ch1.config = "DAPI";
      ch1.exposure = 5.0;
      s.channels.add(ch1);
      ChannelSpec ch2 = new ChannelSpec();
      ch2.config = "FITC";
      ch2.exposure = 15.0;
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
         summary.put(MMTags.Summary.SLICES, s.slices.size());
         summary.put(MMTags.Summary.POSITIONS, 1);
         summary.put(MMTags.Summary.CHANNELS, s.channels.size());
         summary.put(MMTags.Summary.FRAMES, s.numFrames);
         summary.put(MMTags.Summary.SLICES_FIRST, true);
         summary.put(MMTags.Summary.TIME_FIRST, false);

         if (d == 2) {
            summary.put(MMTags.Summary.PIX_TYPE, "GRAY16");
            summary.put(MMTags.Summary.IJ_TYPE, ImagePlus.GRAY16);
         } else if (d==1) {
            summary.put(MMTags.Summary.PIX_TYPE, "GRAY8");
            summary.put(MMTags.Summary.IJ_TYPE, ImagePlus.GRAY8);
         } else {
            System.out.println("Unsupported pixel type");
            return;
         }
         summary.put(MMTags.Summary.WIDTH, w);
         summary.put(MMTags.Summary.HEIGHT, h);
         summary.put(MMTags.Summary.PREFIX, s.prefix); // Acquisition name

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
         summary.put(MMTags.Summary.COLORS, chColors);
         summary.put(MMTags.Summary.NAMES, chNames);
         summary.put(MMTags.Summary.CHANNEL_MINS, chMins);
         summary.put(MMTags.Summary.CHANNEL_MAXES, chMaxs);

      } catch (JSONException e2) {
         // TODO Auto-generated catch block
         e2.printStackTrace();
      }

      IAcquisitionEngine2010 acqEng = null;
      acqEng = new AcquisitionEngine2010(core);
      AcqStandalone listener = new AcqStandalone();

      try {
         // Start up the acquisition engine
         PositionList posList = new PositionList();
         posList.addPosition(new MultiStagePosition());
         BlockingQueue<TaggedImage> taggedImageQueue = acqEng.run(s, true, posList, null);

         // create storage
         TaggedImageStorage storage = new TaggedImageStorageDiskDefault(actualPath, true, summary);
         MMImageCache imageCache = new MMImageCache(storage);
         imageCache.addImageCacheListener(listener);
         
         // Start pumping images into the ImageCache
         DefaultTaggedImageSink sink = new DefaultTaggedImageSink(taggedImageQueue, imageCache);
         sink.start();
         
         do {
            Thread.sleep(50);
         } while (!imageCache.isFinished());

      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }

   }
   
   @Override
   public void imageReceived(TaggedImage taggedImage) {
     try {
      int frame = taggedImage.tags.getInt(MMTags.Image.FRAME);
      String channel = taggedImage.tags.getString(MMTags.Image.CHANNEL);
      int slice = taggedImage.tags.getInt(MMTags.Image.SLICE_INDEX);
      int position = taggedImage.tags.getInt(MMTags.Image.POS_INDEX);
      System.out.println("Acquired " + position + ", " + channel + ", " + frame + ", " + slice);
   } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
   }
      
   }

   @Override
   public void imagingFinished(String path) {
      System.out.println("Acqusition finished: " + path);
   }

}

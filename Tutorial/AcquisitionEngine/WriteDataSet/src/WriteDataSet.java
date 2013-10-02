import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import ij.ImagePlus;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.api.MMTags;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MMScriptException;


public class WriteDataSet {
   
   public static void main(String[] args) {
      taggedWrite();
   }
   
   /**
    * Create and write acquisition data set using the 'tag' API
    */
   public static void taggedWrite() {
      
      // create MMCore instance
      CMMCore core = new CMMCore();
      try {
         core.loadSystemConfiguration("MMConfig_demo.cfg");
      } catch (Exception e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }

      // set up acquisition parameters
      String acqName = "Test-A";
      String rootName = "C:/AcqusitionData/Tests";
      boolean ome = false;
      final int frames = 5;
      final int slices = 3;
      final double deltaZ = 0.3;
      final String channelGroup = "Channel";
      StrVector channels = core.getAvailableConfigs(channelGroup);
      double[] exposures = new double[(int)channels.size()];
      String zStage = core.getFocusDevice();
      for (int i=0; i<channels.size(); i++)
         exposures[i] = 10.0 + i*5.0;
      long w = core.getImageWidth();
      long h = core.getImageHeight();
      long d = core.getBytesPerPixel();
            
      try {
         // Create new data set
         JSONObject summary = new JSONObject();
         summary.put(MMTags.Summary.SLICES, slices);
         summary.put(MMTags.Summary.POSITIONS, 1);
         summary.put(MMTags.Summary.CHANNELS, channels.size());
         summary.put(MMTags.Summary.FRAMES, frames);
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
         summary.put(MMTags.Summary.WIDTH, 512);
         summary.put(MMTags.Summary.HEIGHT, 512);
         summary.put(MMTags.Summary.PREFIX, acqName); // Acquisition name
         
         //these are used to create display settings
         JSONArray chColors = new JSONArray();
         JSONArray chNames = new JSONArray();
         JSONArray chMins = new JSONArray();
         JSONArray chMaxs = new JSONArray();
         for (int ch=0; ch<channels.size(); ch++) {
            chColors.put(1);
            chNames.put(channels.get(ch));
            chMins.put(0);
            chMaxs.put(d == 2 ? 65535 : 255);
         }
         summary.put(MMTags.Summary.COLORS, chColors);
         summary.put(MMTags.Summary.CHANNELS, chNames);
         summary.put(MMTags.Summary.CHANNEL_MINS, chMins);
         summary.put(MMTags.Summary.CHANNEL_MAXES, chMaxs);
         
         TaggedImageStorage storage = null;
         String path = rootName + "/" + acqName;
         String actualPath = path;
         File f = new File(path);
         int count = 1;
         while (f.exists()) {
            actualPath = path + "_" + count++;
            f = new File(actualPath);
         }
         
         if (ome)
            // use ome compatible single file
            storage = new TaggedImageStorageMultipageTiff(actualPath, true, summary, true, true, false); 
         else
            // use micro-manager format
            storage = new TaggedImageStorageDiskDefault(actualPath, true, summary);
         
         core.setProperty("Core",  "ChannelGroup", channelGroup);
         MMImageCache imageCache = new MMImageCache(storage);

         int imageCounter = 0;
         for (int fr=0; fr<frames; fr++)
         {
            System.out.println("acquiring frame " + fr + "...");
            for (int sl=0; sl<slices; sl++)
            {
               // move z stage
               core.setPosition(zStage, sl*deltaZ);
               core.waitForDevice(zStage);
               
               for (int ch=0; ch<channels.size(); ch++)
               {
                  core.setConfig(channelGroup, channels.get(ch));
                  core.setExposure(exposures[ch]);
                  double x[] = new double[1];
                  double y[] = new double[1];
                  core.getXYPosition(core.getXYStageDevice(), x, y);
                  double z = core.getPosition(core.getFocusDevice());
                  
                  core.snapImage();
                  Object img = core.getImage();
                  
                  // build metadata
                  JSONObject md = new JSONObject();
                  md.put(MMTags.Image.WIDTH, w);
                  md.put(MMTags.Image.HEIGHT, h);
                  
                  
                  // NOTE: are both FRAME and FRAME_INDEX necessary??
                  md.put(MMTags.Image.FRAME, fr);
                  md.put(MMTags.Image.FRAME_INDEX, fr);

                  md.put(MMTags.Image.SLICE, sl);
                  md.put(MMTags.Image.SLICE_INDEX, sl);

                  md.put(MMTags.Image.CHANNEL_INDEX, ch);
                  md.put(MMTags.Image.CHANNEL_NAME, channels.get(ch));
                  md.put(MMTags.Image.POS_INDEX, 1);

                  md.put(MMTags.Image.XUM, x[0]);
                  md.put(MMTags.Image.YUM, y[0]);
                  md.put(MMTags.Image.ZUM, z);
                  
                  if (d == 2) {
                     md.put(MMTags.Image.IJ_TYPE, ImagePlus.GRAY16);
                  } else if (d==1) {
                     md.put(MMTags.Image.IJ_TYPE, ImagePlus.GRAY8);
                  } else {
                     System.out.println("Unsupported pixel type");
                     return;
                  }

                  md.put(MMTags.Image.TIME, new SimpleDateFormat("yyyyMMdd HHmmss").format(Calendar.getInstance().getTime())); // TIME FORMAT ???

                  TaggedImage ti = new TaggedImage(img, md);
                  imageCache.putImage(ti);
                                    
                  imageCounter++;
               }
            }
         }
         System.out.println("Acquired " + imageCounter + " images");
         storage.finished();
         storage.close();
         
      } catch (MMScriptException e) {
         e.printStackTrace();
      } catch (Exception e) {
         e.printStackTrace();
         return;
      }
   }


	/**
	 * Create and write acquisition data set using the legacy API
	 */
	public static void legacyWrite() {
		
		// instantiate acquisition interface
		ScriptInterface app = new MMStudioMainFrame(false);
		
		// create MMCore instance
		CMMCore core = new CMMCore();
		try {
			core.loadSystemConfiguration("MMConfig_demo.cfg");
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// set up acquisition parameters
		String acqName = "Test-A";
		String rootName = "C:/AcqusitionData";
		final int frames = 20;
		final int slices = 10;
		final double deltaZ = 0.3;
		final String channelGroup = "Channel";
		StrVector channels = core.getAvailableConfigs(channelGroup);
		double[] exposures = new double[(int)channels.size()];
		String zStage = core.getFocusDevice();
		for (int i=0; i<channels.size(); i++)
			exposures[i] = 10.0 + i*5.0;
      long w = core.getImageWidth();
      long h = core.getImageHeight();
      long d = core.getBytesPerPixel();
		
		try {
			// Create new data set
			app.openAcquisition(acqName, rootName, frames, (int)channels.size(), slices, 1, false, true);
         MMAcquisition a = app.getAcquisition(acqName);
         app.getAcquisition(acqName).setProperty(MMTags.Summary.SLICES_FIRST, "true");
         app.getAcquisition(acqName).setProperty(MMTags.Summary.TIME_FIRST, "false");

         app.initializeAcquisition(acqName, (int)w, (int)h, (int)d);

         for (int i = 0; i < channels.size(); i++) {
            a.setChannelName(i, channels.get(i));
            // TODO: a.setChannelColor(i, rgb);
         }

			int imageCounter = 0;
			for (int fr=0; fr<frames; fr++)
			{
				System.out.println("acquiring frame " + fr + "...");
				for (int sl=0; sl<slices; sl++)
				{
					// move z stage
					core.setPosition(zStage, sl*deltaZ);
					core.waitForDevice(zStage);
					
					for (int ch=0; ch<channels.size(); ch++)
					{
						core.setConfig(channelGroup, channels.get(ch));
						core.setExposure(exposures[ch]);
						double x[] = new double[1];
						double y[] = new double[1];
						core.getXYPosition(core.getXYStageDevice(), x, y);
						double z = core.getPosition(core.getFocusDevice());
						
						core.snapImage();
						Object img = core.getImage();
						
						// build metadata
						JSONObject md = new JSONObject();
                  md.put(MMTags.Image.WIDTH, w);
                  md.put(MMTags.Image.HEIGHT, h);
                  
                  if (d == 2)
                     md.put(MMTags.Image.PIX_TYPE, "GRAY16");
                  else if (d==1)
                     md.put(MMTags.Image.PIX_TYPE, "GRAY8");
                  else {
                     System.out.println("Unsupported pixel type");
                     return;
                  }
                  
                  // NOTE: are both FRAME and FRAME_INDEX necessary??
                  md.put(MMTags.Image.FRAME, fr);
                  md.put(MMTags.Image.FRAME_INDEX, fr);

                  md.put(MMTags.Image.SLICE, sl);
                  md.put(MMTags.Image.SLICE_INDEX, sl);

                  md.put(MMTags.Image.CHANNEL_INDEX, ch);
                  md.put(MMTags.Image.CHANNEL_NAME, channels.get(ch));

                  md.put(MMTags.Image.XUM, x[0]);
                  md.put(MMTags.Image.YUM, y[0]);
                  md.put(MMTags.Image.ZUM, z);

                  TaggedImage ti = new TaggedImage(img, md);
                  app.addImage(acqName, ti);
												
						imageCounter++;
					}
				}
			}
			System.out.println("Acquired " + imageCounter + " images");
			
		} catch (MMScriptException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

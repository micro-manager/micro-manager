import mmcorej.CMMCore;
import mmcorej.StrVector;
import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MMScriptException;


public class WriteDataSet {

   /**
    * Metadata field definitions
    * NOTE: this should be defined in the micro-manager metadata class
    */
   public static final String SUMMARY = "Summary";
   public static final String WIDTH = "Width";
   public static final String HEIGHT = "Height";
   public static final String PIXSIZE = "PixelSize_um";
   public static final String XUM = "XPositionUm";
   public static final String YUM = "YPositionUm";
   public static final String ZUM = "ZPositionUm";
   public static final String ZUM2 = "Z-Position";
   public static final String FILE_NAME = "FileName";
   public static final String PIXEL_ASPECT = "PixelAspect";
   public static final String SOURCE = "Source";
   public static final String FRAMES = "Frames";
   public static final String CHANNELS = "Channels";
   public static final String SLICES = "Slices";
   public static final String POSITIONS = "Positions";
   public static final String COLORS = "ChColors";
   public static final String NAMES = "ChNames";
   public static final String CHANNEL = "Channel";
   public static final String FRAME = "Frame";
   public static final String SLICE = "Slice";
   public static final String CHANNEL_INDEX = "ChannelIndex";
   public static final String SLICE_INDEX = "SliceIndex";
   public static final String FRAME_INDEX = "FrameIndex";
   public static final String CHANNEL_NAME = "Channel";
   public static final String POS_NAME = "PositionName";
   public static final String PIX_TYPE = "PixelType";
   public static final String BIT_DEPTH = "BitDepth";
   public static final String SLICES_FIRST = "SlicesFirst";
   public static final String TIME_FIRST = "TimeFirst";
   
   public static void main(String[] args) {
      taggedWrite();
   }
   
   /**
    * Create and write acquisition data set using the legacy API
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
      String rootName = "C:/AcqusitionData";
      boolean ome = false;
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
         JSONObject summary = new JSONObject();
         summary.put("Slices", slices);
         summary.put("Positions", 1);
         summary.put("Channels", channels.size());
         summary.put("Frames", frames);
         summary.put("SlicesFirst", true);
         summary.put("TimeFirst", false);
         
         if (d == 2)
            summary.put(PIX_TYPE, "GRAY16");
         else if (d==1)
            summary.put(PIX_TYPE, "GRAY8");
         else {
            System.out.println("Unsupported pixel type");
            return;
         }
         summary.put("Width", 512);
         summary.put("Height", 512);
         summary.put("Prefix", acqName); // Acquisition name
         
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
         summary.put("ChColors", chColors);
         summary.put("ChNames", chNames);
         summary.put("ChMins", chMins);
         summary.put("ChMaxes", chMaxs);
         
         TaggedImageStorage storage = null;
         if (ome)
            // use ome compatible single file
            storage = new TaggedImageStorageMultipageTiff(rootName, true, summary, true, true, false); 
         else
            // use micro-manager format
            storage = new TaggedImageStorageDiskDefault(rootName, true, summary); 

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
                  md.put(WIDTH, w);
                  md.put(HEIGHT, h);
                  
                  
                  // NOTE: are both FRAME and FRAME_INDEX necessary??
                  md.put(FRAME, fr);
                  md.put(FRAME_INDEX, fr);

                  md.put(SLICE, sl);
                  md.put(SLICE_INDEX, sl);

                  md.put(CHANNEL_INDEX, ch);
                  md.put(CHANNEL_NAME, channels.get(ch));
                  md.put("PositionIndex", 1);

                  md.put(XUM, x[0]);
                  md.put(YUM, y[0]);
                  md.put(ZUM, z);

                  TaggedImage ti = new TaggedImage(img, md);
                  storage.putImage(ti);
                                    
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
         app.getAcquisition(acqName).setProperty(SLICES_FIRST, "true");
         app.getAcquisition(acqName).setProperty(TIME_FIRST, "false");

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
                  md.put(WIDTH, w);
                  md.put(HEIGHT, h);
                  
                  if (d == 2)
                     md.put(PIX_TYPE, "GRAY16");
                  else if (d==1)
                     md.put(PIX_TYPE, "GRAY8");
                  else {
                     System.out.println("Unsupported pixel type");
                     return;
                  }
                  
                  // NOTE: are both FRAME and FRAME_INDEX necessary??
                  md.put(FRAME, fr);
                  md.put(FRAME_INDEX, fr);

                  md.put(SLICE, sl);
                  md.put(SLICE_INDEX, sl);

                  md.put(CHANNEL_INDEX, ch);
                  md.put(CHANNEL_NAME, channels.get(ch));

                  md.put(XUM, x[0]);
                  md.put(YUM, y[0]);
                  md.put(ZUM, z);

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

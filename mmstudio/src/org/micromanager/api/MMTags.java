package org.micromanager.api;

/**
 * The list of tags used by the Micro-manager
 */
public class MMTags {
   
   /**
    * Meta-tags referring to main sections of the metadata
    */
   public class Root {
      public static final String SUMMARY = "Summary"; // key for the Summary metadata      
   }
   
   /**
    * Summary tags
    */
   public class Summary {
      public static final String PREFIX = "Prefix"; // Acquisition name
      public static final String DIRECTORY = "Directory"; // Acquisition directory

      public static final String WIDTH = "Width"; // image width
      public static final String HEIGHT = "Height"; // image height
      public static final String FRAMES = "Frames"; // number of frames
      public static final String SLICES = "Slices"; // number of (z) slices
      public static final String CHANNELS = "Channels"; // number of channels
      public static final String POSITIONS = "Positions"; // number of positions

      public static final String PIXSIZE = "PixelSize_um";
      public static final String PIX_TYPE = "PixelType";
      public static final String IJ_TYPE = "IJType";
      
      public static final String PIXEL_ASPECT = "PixelAspect";
      public static final String SOURCE = "Source";
      public static final String COLORS = "ChColors";
      public static final String CHANNEL_MINS = "ChContrastMin";
      public static final String CHANNEL_MAXES = "ChContrastMax";
      public static final String NAMES = "ChNames";
      public static final String BIT_DEPTH = "BitDepth";
      public static final String OBJECTIVE_LABEL = "Objective-Label";
      public static final String ELAPSED_TIME = "ElapsedTime-ms";

      public static final String SLICES_FIRST = "SlicesFirst";
      public static final String TIME_FIRST = "TimeFirst";

   }
   
   public class Image {
      public static final String WIDTH = "Width"; // image width
      public static final String HEIGHT = "Height"; // image height
      public static final String CHANNEL = "Channel";
      public static final String FRAME = "Frame";
      public static final String SLICE = "Slice";
      public static final String CHANNEL_INDEX = "ChannelIndex";
      public static final String SLICE_INDEX = "SliceIndex";
      public static final String FRAME_INDEX = "FrameIndex";
      public static final String CHANNEL_NAME = "Channel";
      public static final String POS_NAME = "PositionName";
      public static final String POS_INDEX = "PositionIndex";
      public static final String XUM = "XPositionUm";
      public static final String YUM = "YPositionUm";
      public static final String ZUM = "ZPositionUm";
      public static final String IJ_TYPE = "IJType";
      public static final String TIME = "Time";
      public static final String PIX_TYPE = "PixelType";
      public static final String BIT_DEPTH = "BitDepth";
      public static final String ELAPSED_TIME_MS = "ElapsedTime-ms";
   }
   
   public class Values {
      public static final String PIX_TYPE_GRAY_32 = "GRAY32";
      public static final String PIX_TYPE_GRAY_16 = "GRAY16";
      public static final String PIX_TYPE_GRAY_8 = "GRAY8";
      public static final String PIX_TYPE_RGB_32 = "RGB32";
      public static final String PIX_TYPE_RGB_64 = "RGB64";

      public static final String CHANNEL_DEFAULT = "Default";
   }

}

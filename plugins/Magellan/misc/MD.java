/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

/**
 * List of metadata tags
 */
public class MD {
   
   public static final String WIDTH = "Width";
   public static final String HEIGHT = "Height";
   public static final String PIX_SIZE = "PixelSize_um";
   public static final String POS_NAME = "PositionName";
   public static final String POS_INDEX = "PositionIndex";
   public static final String XUM = "XPositionUm";
   public static final String YUM = "YPositionUm";
   public static final String ZUM = "ZPositionUm";
   public static final String SLICE = "Slice";
   public static final String FRAME = "Frame";
   public static final String CHANNEL_INDEX = "ChannelIndex";
   public static final String SLICE_INDEX = "SliceIndex";
   public static final String FRAME_INDEX = "FrameIndex";
   public static final String NUM_CHANNELS = "Channels";
   public static final String CHANNEL_NAME = "Channel";
   public static final String CHANNEL_NAMES = "ChNames";
   public static final String CHANNEL_COLORS = "ChColors";
   public static final String ZC_ORDER = "SlicesFirst";
   public static final String TIME = "Time";
   public static final String SAVING_PREFIX = "Prefix";
   public static final String INITIAL_POS_LIST = "InitialPositionList";
   public static final String TIMELAPSE_INTERVAL = "Interval_ms";
   public static final String PIX_TYPE = "PixelType";
   public static final String BIT_DEPTH = "BitDepth";
   public static final String ELAPSED_TIME_MS = "ElapsedTime-ms";
   public static final String Z_STEP_UM = "z-step_um";
   public static final String OVERLAP_X = "GridPixelOverlapX";
   public static final String OVERLAP_Y = "GridPixelOverlapY";
   public static final String AFFINE_TRANSFORM = "AffineTransform";
   public static final String EXPLORE_ACQ = "MagellanExploreAcquisition";
   
   

   public static int[] getIndices(String imageLabel) {
      int[] ind = new int[4];
      String[] s = imageLabel.split("_");
      for (int i = 0; i < 4; i++) {
         ind[i] = Integer.parseInt(s[i]);
      }
      return ind;
   }
   
}

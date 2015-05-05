package imagedisplay;

/**
 * This interface allows us to manipulate the dimensions
 * in an ImagePlus without it throwing conniptions.
 */
public interface IMMImagePlus {
   public int getNChannelsUnverified();
   public int getNSlicesUnverified();
   public int getNFramesUnverified();
   public void setNChannelsUnverified(int nChannels);
   public void setNSlicesUnverified(int nSlices);
   public void setNFramesUnverified(int nFrames);
   public void drawWithoutUpdate();

   public int[] getPixelIntensities(int x, int y);
}

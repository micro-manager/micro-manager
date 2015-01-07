package edu.valelab.gaussianfit.data;


import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Data structure to internally store fit data
 * 
 * Also contains utility functions to generate an ImageJ ImageProcessor
 * containing the spot in the image
 * 
 * @author Nico Stuurman
 */


public class SpotData {

   
   // lock to avoid clashes during access to image data
   public static final Object lockIP = new Object();

   private ImageProcessor ip_ = null;   // ImageProcessor for given spot
   private final int frame_;        // frame number in the original stack - 1-based
   private final int channel_;      // channel number in the original stack
   private final int slice_;        // slice number in the original stack - 1-based
   private final int position_;     // position number in the original stack
   private final int nr_;           // spot index in given image
   private final int x_;            // x as found by spotfinder
   private final int y_;            // y as found by spotfinder
   private double intensity_; // total intensity expressed in photons
   private double background_;// background expressed in photons (may or may not be corrected for baseline)
   private double xCenter_;      // center of gaussian in Coordinates (image coordinate system)
   private double yCenter_;      // center of gaussian in Coordinates (image coordinate system)
   private double zCenter_;   // estimate of z position in nm
   private double xOri_;      // original position before correction in Coordinates
   private double yOri_;      // original position before correction in Coordinates
   private double zOri_;      // original position before correction
   private double width_;         // width of the gaussian (in nm)
   private double a_;         // shape of the peak, defined as width(long axis) / width (short axis)
   private double theta_;     // shape factor for spot (rotation of assymetric peak)
   private double sigma_;     // Estimate of error in localization based on Web et al. formula
                              // that uses # of photons, background and width of gaussian
   public int nrLinks_;       // number of frames/slices in which this spot was found
   public int originalFrame_; // original first frame/slice in which this spot was found
   private final Map<String, Double> keyValue_; // Map of keys/values taht can be used to extend what we store in the SpotData

   public SpotData(ImageProcessor ip, int channel, int slice, int frame, 
           int position, int nr, int x, int y) {
      ip_ = ip;
      frame_ = frame;
      channel_ = channel;
      slice_ = slice;
      position_ = position;
      nr_ = nr;
      x_ = x;
      y_ = y;
      keyValue_ = new HashMap<String, Double>();
   }
   
   
   /**
    * Copy constructor.  Copies frame, slice, channel, position ,  x,  y,
    * intensity, background, width, a, theta and sigma!
    * @param spot
    */
   public SpotData(SpotData spot) {
      frame_ = spot.frame_;
      slice_ = spot.slice_;
      channel_ = spot.channel_;
      position_ = spot.position_;
      nr_ = spot.nr_;
      x_ = spot.x_;
      y_ = spot.y_;
      intensity_ = spot.intensity_;
      background_ = spot.background_;
      xCenter_ = spot.xCenter_;
      yCenter_ = spot.yCenter_;
      zCenter_ = spot.zCenter_;
      xOri_ = spot.xOri_;
      yOri_ = spot.yOri_;
      zOri_ = spot.zOri_;
      width_ = spot.width_;
      a_ = spot.a_;
      theta_ = spot.theta_;
      sigma_ = spot.sigma_;  
      keyValue_ = new HashMap<String, Double>(spot.keyValue_);
   }

   public void setData(double intensity, 
           double background, 
           double xCenter, 
           double yCenter, 
           double zCenter,
           double width, 
           double a, 
           double theta, 
           double sigma) {
      intensity_ = intensity;
      background_ = background;
      xCenter_ = xCenter;
      yCenter_ = yCenter;
      width_ = width;
      a_ = a;
      theta_ = theta;
      sigma_ = sigma;
   }
   
   public void addKeyValue(String key, double value) {
      keyValue_.put(key, value);
   }
   
   public Double getValue(String key) {
      return keyValue_.get(key);
   }
   
   public String[] getKeys() {
      Set<String> keys = keyValue_.keySet();
      return  keys.toArray(new String[keys.size()]);
   }
   
   public boolean hasKey(String key) {
      return keyValue_.containsKey(key);
   }
   
   public void setOriginalPosition(double xPos, double yPos, double zPos) {
      xOri_ = xPos;
      yOri_ = yPos;
      zOri_ = zPos;
   }

   public ImageProcessor getImageProcessor() {
      return ip_;
   }
   public void setImageProcessor (ImageProcessor ip) {
      ip_ = ip;
   }
   public int getFrame() {
      return frame_;
   }
   public int getSlice() {
      return slice_;
   }
   public int getChannel() {
      return channel_;
   }
   public int getPosition() {
      return position_;
   }
   public int getNr() {
      return nr_;
   }
   public int getX() {
      return x_;
   }
   public int getY() {
      return y_;
   }
   public double getIntensity() {
      return intensity_;
   }
   public double getBackground() {
      return background_;
   }
   public double getXCenter() {
      return xCenter_;
   }
   public void setXCenter(double x) {
      xCenter_ = x;
   }
   public double getYCenter() {
      return yCenter_;
   }
   public void setYCenter(double y) {
      yCenter_ = y;
   }
   public double getZCenter() {
      return zCenter_;
   }
   public void setZCenter(double z) {
      zCenter_ = z;
   }
   public double getXOri() {
      return xOri_;
   }
   public double geYOri() {
      return yOri_;
   }
   public double getZOri() {
      return zOri_;
   }
   public double getWidth() {
      return width_;
   }
   public double getA() {
      return a_;
   }
   public double getTheta() {
      return theta_;
   }
   public double getSigma() {
      return sigma_;
   }

   // For performance reasons, it is much better to use the cached version of the processor
   public ImageProcessor getSpotProcessor(ImagePlus siPlus, int halfSize) {
      if (ip_ != null)
         return ip_;
      synchronized(lockIP) {
         Roi spotRoi = new Roi(x_ - halfSize, y_ - halfSize, 2 * halfSize, 2 * halfSize);
         siPlus.setPositionWithoutUpdate(channel_, slice_, frame_);
         siPlus.setRoi(spotRoi, false);
         return siPlus.getProcessor().crop();
      }
   }

   public ImageProcessor getSpotProcessor(ImageProcessor siProc, int halfSize) {
      if (ip_ != null)
         return ip_;
      synchronized(lockIP) {
         Roi spotRoi = new Roi(x_ - halfSize, y_ - halfSize, 2 * halfSize, 2 * halfSize);
         //siProc.setSliceWithoutUpdate(frame_);
         siProc.setRoi(spotRoi);
         return siProc.crop();
      }
   }

   public static ImageProcessor getSpotProcessor(ImageProcessor siProc, int halfSize, int x, int y) {
      synchronized(lockIP) {
         Roi spotRoi = new Roi(x - halfSize, y - halfSize, 2 * halfSize, 2 * halfSize);
         siProc.setRoi(spotRoi);
         try {
            return siProc.crop();
         } catch (java.lang.ArrayIndexOutOfBoundsException ex) {
            return null;
         }
      }
   }
}

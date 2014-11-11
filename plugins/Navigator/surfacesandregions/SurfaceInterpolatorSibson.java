///*
// * To change this template, choose Tools | Templates
// * and open the template in the editor.
// */
//package surfacesandregions;
//
//import edu.mines.jtk.dsp.Sampling;
//import edu.mines.jtk.interp.SibsonInterpolator2;
//import java.util.LinkedList;
//import org.micromanager.MMStudio;
//
///**
// *
// * @author Henry
// */
//public class SurfaceInterpolatorSibson extends SurfaceInterpolator{
//   
//      private SibsonInterpolator2 interpolator_;
//
//   
//   public SurfaceInterpolatorSibson(SurfaceManager manager) {
//      super(manager);
//      interpolator_ = new SibsonInterpolator2(new float[]{0}, new float[]{0}, new float[]{0});
//   }
//
//   
//      protected void interpolateSurface(LinkedList<Point3d> points) {
//      try {
//
//         //provide interpolator with current list of data points
//         float x[] = new float[points.size()];
//         float y[] = new float[points.size()];
//         float z[] = new float[points.size()];
//         for (int i = 0; i < points.size(); i++) {
//            x[i] = (float) points.get(i).x;
//            y[i] = (float) points.get(i).y;
//            z[i] = (float) points.get(i).z;
//         }
////      gridder_.setScattered(z, x, y);
//         interpolator_.setSamples(z, x, y);
//
//         int maxPixelDimension = (int) (Math.max(boundXMax_ - boundXMin_, boundYMax_ - boundYMin_) / MMStudio.getInstance().getCore().getPixelSizeUm());
//         //Start with at least 20 interp points and go smaller and smaller until every pixel interped?
//         int pixelsPerInterpPoint = 1;
//         while (maxPixelDimension / (pixelsPerInterpPoint + 1) > 20) {
//            pixelsPerInterpPoint *= 2;
//         }
//         if (Thread.interrupted()) {
//            throw new InterruptedException();
//         }
//
//         while (pixelsPerInterpPoint >= MIN_PIXELS_PER_INTERP_POINT) {
//            int numInterpPointsX = (int) (((boundXMax_ - boundXMin_) / MMStudio.getInstance().getCore().getPixelSizeUm()) / pixelsPerInterpPoint);
//            int numInterpPointsY = (int) (((boundYMax_ - boundYMin_) / MMStudio.getInstance().getCore().getPixelSizeUm()) / pixelsPerInterpPoint);
//
//            //do interpolation
//            Sampling xSampling = new Sampling(numInterpPointsX, (boundXMax_ - boundXMin_) / (numInterpPointsX - 1), boundXMin_);
//            Sampling ySampling = new Sampling(numInterpPointsY, (boundYMax_ - boundYMin_) / (numInterpPointsY - 1), boundYMin_);
//            interpolator_.setNullValue(Float.MIN_VALUE);
//            interpolator_.useConvexHullBounds();
//            float[][] interpVals = interpolator_.interpolate(xSampling, ySampling);
////            currentInterpolation_ = new SingleResolutionInterpolation(pixelsPerInterpPoint, interpVals, boundXMin_, boundXMax_, boundYMin_, boundYMax_, convexHullRegion_);
//            pixelsPerInterpPoint /= 2;
//         }
//      } catch (InterruptedException e) {
//         Thread.interrupted();
//      }
//   }
//   
//   
//}

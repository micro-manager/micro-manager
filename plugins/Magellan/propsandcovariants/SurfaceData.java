///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package propsandcovariants;

import acq.AcquisitionEvent;
import coordinates.AffineUtils;
import coordinates.XYStagePosition;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import misc.Log;
import surfacesandregions.SingleResolutionInterpolation;
import surfacesandregions.SurfaceInterpolator;

/**
 * Category about interpolated surface (e.g. distance below surface) to be used in
 * covaried settings
 */
public class SurfaceData implements Covariant {

   //all data must start with this prefix so they can be reconstructed when read from a text file on disk
   public static String PREFIX = "Surface data: ";
   //number of test points per dimension for finding minimum distance to surface
   private static final int NUM_XY_TEST_POINTS = 9;
      //number of test points per dimension for finding minimum distance to surface within angle
//   private static final int NUM_XY_TEST_POINTS_ANGLE = 5;
   
   public static String  SPACER = "--";
   public static String  DISTANCE_BELOW_SURFACE_CENTER = "Vertical distance below at XY position center";
   public static String  DISTANCE_BELOW_SURFACE_MINIMUM = "Minimum vertical distance below at XY position";
   public static String  DISTANCE_BELOW_SURFACE_MAXIMUM = "Maximum vertical distance below at XY position";
   public static String  LN_OPTIMAL_DISTANCE_MT = "Lymph Node optimal distance Maitai";
   public static String  LN_OPTIMAL_DISTANCE_CHAM = "Lymph Node optimal distance Chameleon";
   
   private String category_;
   private SurfaceInterpolator surface_;
   
   public SurfaceData(SurfaceInterpolator surface, String type) throws Exception {
      category_ = type;
      surface_ = surface;
      if (!Arrays.asList(enumerateDataTypes()).contains(type)) {
         //not a recognized type
         throw new Exception();
      }
   }
   
   public SurfaceInterpolator getSurface() {
      return surface_;
   }
   
   public static String[] enumerateDataTypes() {
      return new String[] {DISTANCE_BELOW_SURFACE_CENTER, DISTANCE_BELOW_SURFACE_MINIMUM, 
          DISTANCE_BELOW_SURFACE_MAXIMUM, LN_OPTIMAL_DISTANCE_MT, LN_OPTIMAL_DISTANCE_CHAM};
   }
   
   @Override
   public String toString() {
      return getName();
   }
   
   
   @Override
   public String getAbbreviatedName() {
      if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
         return "Vertical distance to " + surface_.getName();
       } else if (category_.equals(DISTANCE_BELOW_SURFACE_MINIMUM)) {
           return "Min vertical distance to " + surface_.getName();
       } else if (category_.equals(DISTANCE_BELOW_SURFACE_MAXIMUM)) {
           return "Min distance to " + surface_.getName();
       } else if (category_.equals(LN_OPTIMAL_DISTANCE_MT)) {
           return "Maitai Lymph node optimal distance for " + surface_.getName();
 
       } else {
           Log.log("Unknown Surface data type");
           throw new RuntimeException();
       }
   }

    @Override
    public String getName() {
        return PREFIX + surface_.getName() + SPACER + category_;
    }

      @Override
   public boolean isValid(CovariantValue potentialValue) {
      return potentialValue.getType() == CovariantType.DOUBLE;
   }

   @Override
   public CovariantValue[] getAllowedValues() {
      //not applicable because all numerical for now
      return null;
   }

   @Override
   public boolean isDiscrete() {
      return false;
   }

   @Override
   public boolean hasLimits() {
      return false;
   }

   @Override
   public CovariantValue getLowerLimit() {
      return null;
   }

   @Override
   public CovariantValue getUpperLimit() {
      return null;
   }

   @Override
   public CovariantType getType() {
      return CovariantType.DOUBLE;
   }

   @Override
   public CovariantValue getValidValue(List<CovariantValue> vals) {
      double d = 0;
      while (true) {
         if (!vals.contains(new CovariantValue(d))) {
            return new CovariantValue(d);
         }
         d++;
      }
   }

//    private double lnOptimalDistanceOld(XYStagePosition xyPos, double zPosition) throws InterruptedException {
//        // a special measure for curved surfaces, which gives:
//        //-min distance at surface on flatter parts of curved surface (ie top)
//        //-increased distance up to max distance as you go deeper
//        //-higher distance at surface on side to account for curved surface blocking out some of exciation light
//        //{minDistance,maxDistance, minNormalAngle, maxNormalAngle)
//        double[] vals = distanceAndNormalCalc(xyPos.getFullTileCorners(), zPosition);
//        double extraDistance = 0; //pretend actually deeper in LN than we are to account for blockage by curved surface
//        double angleCutoff = 64; //angle cutoff is maximum nalge colletred by 1.2 NA objective
//        double doublingDistance = 50;
//        double angleCutoffPercent = 0;
//        //twice as much power if angle goes to 0
//        //doubling distance ~40-70 um when b = 0.01-0.018 i exponent
//        //add extra distance to account for blockage by LN surface
//        //never want to make extra distance higher than the double distance,
//        //so extra power is capped at 2x
//        angleCutoffPercent = Math.min(angleCutoff, vals[3]) / angleCutoff;
//        extraDistance = angleCutoffPercent * doublingDistance;
//
//        double curvatureCorrectedMin = vals[0] + extraDistance;
//        double ret = Math.min(vals[1], Math.max(curvatureCorrectedMin, Math.pow(vals[0], 1.2)));
//        return ret;
//    }
      private double lnOptimalDistance(XYStagePosition xyPos, double zPosition, boolean maitai) throws InterruptedException {
      double[] vals = distanceAndNormalCalc(xyPos.getFullTileCorners(), zPosition);
      //use mindistance and max normal so as to not explode LN
      double minDist = vals[0];
      double maxNormal = vals[3];
      //look up 
      int minDistIndex = (int) Math.max(0, Math.min(33, minDist / 9));
      int normalIndex = (int) Math.max(0, Math.min(15, maxNormal / 5));


      return maitai ? LN_DISTANCES_MT[normalIndex][minDistIndex] : LN_DISTANCES_CHAM[normalIndex][minDistIndex];
   }

   /**
    *
    * @param corners
    * @param min true to get min, false to get max
    * @return {minDistance,maxDistance, minNormalAngle, maxNormalAngle)
    */
   private double[] distanceAndNormalCalc(Point2D.Double[] corners, double zVal) throws InterruptedException {      
      //check a grid of points spanning entire position        
      //square is aligned with axes in pixel space, so convert to pixel space to generate test points
      double xSpan = corners[2].getX() - corners[0].getX();
      double ySpan = corners[2].getY() - corners[0].getY();
      Point2D.Double pixelSpan = new Point2D.Double();
      AffineTransform transform = AffineUtils.getAffineTransform(surface_.getCurrentPixelSizeConfig(),0, 0);
      try {
         transform.inverseTransform(new Point2D.Double(xSpan, ySpan), pixelSpan);
      } catch (NoninvertibleTransformException ex) {
         Log.log("Problem inverting affine transform");
      }
      double minDistance = Integer.MAX_VALUE;
      double maxDistance = 0;
      double minNormalAngle = 90;
      double maxNormalAngle = 0;
      for (double x = 0; x <= pixelSpan.x; x += pixelSpan.x / (double) NUM_XY_TEST_POINTS) {
         for (double y = 0; y <= pixelSpan.y; y += pixelSpan.y / (double) NUM_XY_TEST_POINTS) {
            //convert these abritray pixel coordinates back to stage coordinates
            double[] transformMaxtrix = new double[6];
            transform.getMatrix(transformMaxtrix);
            transformMaxtrix[4] = corners[0].getX();
            transformMaxtrix[5] = corners[0].getY();
            //create new transform with translation applied
            transform = new AffineTransform(transformMaxtrix);
            Point2D.Double stageCoords = new Point2D.Double();
            transform.transform(new Point2D.Double(x, y), stageCoords);
            //test point for inclusion of position
              if (!surface_.waitForCurentInterpolation().isInterpDefined(stageCoords.x, stageCoords.y)) {
               //if position is outside of convex hull, assume min distance is 0
               minDistance = 0;
               //get extrapolated value for max distance
               float interpVal = surface_.waitForCurentInterpolation().getExtrapolatedValue(stageCoords.x, stageCoords.y);
               maxDistance = Math.max(zVal - interpVal, maxDistance);
               //only take actual values for normals
            } else {
                   float interpVal = surface_.waitForCurentInterpolation().getInterpolatedValue(stageCoords.x, stageCoords.y);
            float normalAngle = surface_.waitForCurentInterpolation().getNormalAngleToVertical(stageCoords.x, stageCoords.y);
               minDistance = Math.min(Math.max(0,zVal - interpVal), minDistance);
               maxDistance = Math.max(zVal - interpVal, maxDistance);
               minNormalAngle = Math.min(minNormalAngle, normalAngle);
               maxNormalAngle = Math.max(maxNormalAngle, normalAngle);
            }
         }
      }
      return new double[]{minDistance, maxDistance, minNormalAngle, maxNormalAngle};
   }

 
   
   @Override
   public CovariantValue getCurrentValue(AcquisitionEvent event) throws Exception {
      XYStagePosition xyPos = event.xyPosition_;
      if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
         //if interpolation is undefined at position center, assume distance below is 0
         Point2D.Double center = xyPos.getCenter();
        SingleResolutionInterpolation interp = surface_.waitForCurentInterpolation();
        if (interp.isInterpDefined(center.x, center.y)) {
           return new CovariantValue( event.zPosition_ - interp.getInterpolatedValue(center.x, center.y)); 
        }
        return new CovariantValue(0.0);

      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MINIMUM)) {
         return new CovariantValue(distanceAndNormalCalc(xyPos.getFullTileCorners(), event.zPosition_)[0]);
      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MAXIMUM)) {
         return new CovariantValue(distanceAndNormalCalc(xyPos.getFullTileCorners(), event.zPosition_)[1]);
      } else if (category_.equals(LN_DISTANCES_MT)) {
          return new CovariantValue(lnOptimalDistance(xyPos, event.zPosition_, true));
          } else if (category_.equals(LN_DISTANCES_CHAM)) {
          return new CovariantValue(lnOptimalDistance(xyPos, event.zPosition_, false));
      } else {
         Log.log("Unknown Surface data type",true);
         throw new RuntimeException();
      }
   }  

   @Override
   public void updateHardwareToValue(CovariantValue dVal) {
      Log.log("No hardware associated with Surface data",true);
      throw new RuntimeException();
   }

   public static void main (String[] args) {
      System.out.println();
      LN_DISTANCES_CHAM[15][33]
   }
   
   private static final double[][] LN_DISTANCES_CHAM = new double[][]
   {{-0.000000,9.146382,18.234974,27.266530,36.241822,45.161643,54.026803,62.838128,71.596457,80.302642,88.957547,97.562041,106.117005,114.623321,123.081877,131.493564,139.859272,148.179891,156.456312,164.689418,172.880093,181.029210,189.137641,197.206247,205.235882,213.227391,221.181608,229.099357,236.981452,244.828692,252.641867,260.421753,268.169112,275.884693},
{-0.000000,9.207467,18.345123,27.414656,36.417732,45.355985,54.231023,63.044424,71.797734,80.492472,89.130121,97.712136,106.239938,114.714918,123.138433,131.511808,139.836339,148.113288,156.343885,164.529331,172.670797,180.769420,188.826311,196.842550,204.819188,212.757250,220.657730,228.521597,236.349792,244.143232,251.902806,259.629380,267.323796,274.986870},
{-0.000000,9.404749,18.697773,27.884768,36.971114,45.961890,54.861878,63.675580,72.407225,81.060787,89.639993,98.148342,106.589117,114.965395,123.280061,131.535825,139.735228,147.880654,155.974344,164.018404,172.014814,179.965436,187.872025,195.736234,203.559625,211.343670,219.089761,226.799214,234.473277,242.113132,249.719899,257.294643,264.838376,272.352061},
{-0.000000,9.790376,19.372345,28.764980,37.985429,47.049067,55.969646,64.759452,73.429452,81.989434,90.448132,98.813350,107.092063,115.290516,123.414306,131.468463,139.457512,147.385532,155.256211,163.072891,170.838604,178.556110,186.227929,193.856365,201.443529,208.991363,216.501656,223.976059,231.416102,238.823202,246.198678,253.543758,260.859590,268.147246},
{-0.000000,10.503015,20.558682,30.241821,39.612650,48.719599,57.601695,66.290509,74.811717,83.186329,91.431665,99.562106,107.589684,115.524548,123.375321,131.149387,138.853111,146.492019,154.070944,161.594130,169.065334,176.487896,183.864799,191.198724,198.492087,205.747076,212.965681,220.149717,227.300848,234.420598,241.510374,248.571475,255.605104,262.612378},
{-0.000000,11.984941,22.690768,32.601064,41.972711,50.953196,59.633933,68.075522,76.320435,84.399784,92.337186,100.151080,107.856197,115.464528,122.985995,130.428928,137.800379,145.106403,152.352235,159.542440,166.681024,173.771531,180.817108,187.820571,194.784446,201.711014,208.602338,215.460298,222.286607,229.082832,235.850414,242.590681,249.304857,255.994076},
{1.294231,16.288229,27.001753,36.651080,45.691522,54.313270,62.621654,70.683381,78.544162,86.237027,93.786831,101.212836,108.530337,115.751726,122.887204,129.945299,136.933220,143.857122,150.722316,157.533419,164.294474,171.009048,177.680301,184.311054,190.903833,197.460914,203.984353,210.476015,216.937602,223.370665,229.776630,236.156803,242.512391,248.844490},
{5.012817,21.692950,32.820143,42.418489,51.202039,59.468169,67.371629,75.003599,82.422502,89.668292,96.769459,103.747362,110.618426,117.395618,124.089442,130.708555,137.260194,143.750494,150.184715,156.567405,162.902534,169.193591,175.443663,181.655497,187.831550,193.974029,200.084928,206.166052,212.219046,218.245407,224.246509,230.223612,236.177876,242.110371},
{9.157103,26.104313,37.458632,47.144951,55.894159,64.028282,71.724340,79.091655,86.202846,93.108671,99.845988,106.442368,112.918944,119.292262,125.575493,131.779285,137.912395,143.982083,149.994474,155.954716,161.867555,167.735986,173.564187,179.354811,185.110454,190.833415,196.525758,202.189320,207.825787,213.436656,219.023309,224.587002,230.128887,235.650024},
{13.510033,29.910306,41.100297,50.679720,59.323709,67.334129,74.880367,82.069623,88.974957,95.648967,102.130694,108.450203,114.631060,120.692103,126.648622,132.513183,138.296233,144.006543,149.651542,155.237568,160.770067,166.253752,171.692723,177.090570,182.450446,187.775141,193.067132,198.328626,203.561601,208.767833,213.948925,219.106329,224.241361,229.355225},
{17.956378,33.312456,44.001962,53.225301,61.582666,69.342758,76.657019,83.621813,90.303545,96.750424,102.998863,109.077211,115.008050,120.809706,126.497323,132.083543,137.579054,142.992955,148.333095,153.606264,158.818362,163.974588,169.079522,174.137201,179.151243,184.124842,189.060894,193.961984,198.830448,203.668385,208.477718,213.260184,218.017372,222.750731},
{22.445367,36.454511,46.386762,55.027609,62.900258,70.238595,77.174247,83.791019,90.078298,96.282991,102.231979,108.018404,113.662160,119.179505,124.584093,129.886810,135.097855,140.225332,145.276543,150.257767,155.174617,160.032027,164.834399,169.585668,174.289376,178.948711,183.566575,188.145594,192.688162,197.196470,201.672522,206.118159,210.535074,214.924830},
{26.964997,39.440850,48.427195,56.301506,63.512406,70.260494,76.658522,82.778176,88.668743,94.366008,99.897061,105.283018,110.540815,115.684242,120.724793,125.672143,130.534514,135.319069,140.032007,144.678626,149.263826,153.791889,158.266626,162.691455,167.069474,171.403498,175.696085,179.949573,184.166110,188.347664,192.496055,196.612966,200.699954,204.758462},
{31.526919,42.345342,50.245140,57.207783,63.610228,69.621362,75.336208,80.815169,86.099763,91.220115,96.198980,101.054094,105.799641,110.447179,115.006295,119.485059,123.890341,128.228052,132.503388,136.720796,140.884276,144.997365,149.063225,153.084695,157.064341,161.004491,164.907265,168.774604,172.608281,176.409929,180.181071,183.923094,187.637302,191.324912},
{36.158279,45.219329,51.918043,57.849844,63.321680,68.471592,73.377494,78.089000,82.640253,87.056054,91.355151,95.552127,99.658661,103.684271,107.636863,111.523074,115.348572,119.118119,122.836025,126.505953,130.131182,133.714608,137.258674,140.765845,144.238151,147.677547,151.085636,154.464090,157.814314,161.137640,164.435273,167.708348,170.957906,174.184878},
{40.897855,48.095654,53.482716,58.272749,62.702423,66.878934,70.863017,74.693513,78.397195,81.993603,85.497472,88.920350,92.271479,95.558382,98.787347,101.963537,105.091537,108.175171,111.217792,114.222274,117.191374,120.127223,123.031926,125.907324,128.755076,131.576685,134.373231,137.146822,139.897740,142.627322,145.336550,148.026284,150.697362,153.350573}};

   private static final double[][] LN_DISTANCES_MT = new double[][]
   {{-0.000000,9.124383,18.148035,27.073360,35.902862,44.639129,53.284815,61.842622,70.315290,78.705573,87.016233,95.250020,103.409667,111.497873,119.517297,127.470547,135.360178,143.188678,150.958470,158.671906,166.331262,173.938737,181.496454,189.006456,196.470706,203.891090,211.269417,218.607418,225.906750,233.168997,240.395672,247.588220,254.748020,261.876386},
{-0.000000,9.180868,18.241086,27.185799,36.019997,44.748506,53.375988,61.906929,70.345643,78.696269,86.962773,95.148950,103.258426,111.294664,119.260969,127.160489,134.996224,142.771030,150.487624,158.148590,165.756386,173.313349,180.821698,188.283542,195.700884,203.075629,210.409585,217.704468,224.961910,232.183461,239.370593,246.524704,253.647124,260.739115},
{-0.000000,9.362086,18.535053,27.535455,36.378216,45.076736,53.643016,62.087784,70.420625,78.650102,86.783872,94.828790,102.791006,110.676047,118.488894,126.234050,133.915594,141.537234,149.102351,156.614034,164.075120,171.488214,178.855723,186.179868,193.462713,200.706173,207.912033,215.081959,222.217510,229.320147,236.391242,243.432083,250.443886,257.427797},
{-0.000000,9.710525,19.079390,28.159067,36.992784,45.615983,54.057675,62.341638,70.487418,78.511155,86.426260,94.243944,101.973649,109.623388,117.200010,124.709414,132.156720,139.546403,146.882400,154.168197,161.406904,168.601305,175.753909,182.866990,189.942613,196.982645,203.988888,210.962865,217.906074,224.819877,231.705542,238.564251,245.397106,252.205138},
{-0.000000,10.330476,19.971375,29.105943,37.859353,46.317975,54.542488,62.576469,70.451969,78.193123,85.818525,93.342798,100.777664,108.132680,115.415754,122.633520,129.791606,136.894843,143.947410,150.952958,157.914700,164.835481,171.717839,178.564047,185.376155,192.155993,198.905328,205.625621,212.318311,218.984698,225.625978,232.243261,238.837574,245.409875},
{-0.000000,11.480989,21.323301,30.345825,38.866892,47.040947,54.956019,62.667979,70.214971,77.624441,84.916913,92.108222,99.210889,106.235012,113.188883,120.079407,126.912407,133.692848,140.425009,147.112603,153.758882,160.366685,166.938641,173.476933,179.983625,186.460552,192.909374,199.331600,205.728608,212.101658,218.451908,224.780425,231.088194,237.376128},
{0.735123,13.951579,23.528456,32.195357,40.338976,48.127854,55.655290,62.979645,70.140359,77.165594,84.076279,90.888468,97.614796,104.265414,110.848620,117.371302,123.839247,130.257375,136.629901,142.960507,149.252360,155.508274,161.730728,167.921931,174.083857,180.218280,186.326802,192.410874,198.471818,204.510839,210.529043,216.527445,222.506981,228.468516},
{2.847280,17.232953,26.737938,35.060609,42.782359,50.127008,57.205946,64.083795,70.802100,77.389608,83.867283,90.251021,96.553219,102.783806,108.950854,115.061048,121.119989,127.132428,133.102433,139.033520,144.928750,150.790808,156.622065,162.424622,168.200357,173.950954,179.677929,185.382652,191.066368,196.730213,202.375223,208.002347,213.612461,219.206370},
{5.201234,19.844046,29.420186,37.621411,45.097435,52.120422,58.832370,65.316678,71.626337,77.796989,83.853783,89.815113,95.694907,101.503952,107.250829,112.942497,118.584689,124.182208,129.739123,135.258922,140.744622,146.198857,151.623940,157.021922,162.394624,167.743679,173.070556,178.376574,183.662937,188.930739,194.180976,199.414563,204.632338,209.835074},
{7.673699,21.929842,31.397626,39.476758,46.784228,53.590388,60.042881,66.232682,72.219846,78.046172,83.741784,89.329077,94.825097,100.243091,105.593529,110.884824,116.123828,121.316195,126.466644,131.579154,136.657115,141.703440,146.720659,151.710962,156.676302,161.618387,166.538746,171.438745,176.319613,181.182459,186.028293,190.858030,195.672513,200.472508},
{10.199223,23.635981,32.755220,40.579795,47.660583,54.241690,60.459424,66.399663,72.120754,77.664452,83.061752,88.336336,93.506683,98.587514,103.590703,108.525961,113.401323,118.223481,122.998088,127.729934,132.423121,137.081177,141.707157,146.303722,150.873202,155.417652,159.938883,164.438510,168.917972,173.378555,177.821426,182.247628,186.658115,191.053744},
{12.748968,25.081836,33.630073,41.025947,47.748652,54.010393,59.929998,65.582763,71.020361,76.280218,81.390541,86.373205,91.245648,96.021964,100.713768,105.330665,109.881008,114.371527,118.808234,123.196259,127.540061,131.843540,136.110105,140.342789,144.544263,148.716905,152.862839,156.983967,161.082002,165.158486,169.214807,173.252229,177.271900,181.274870},
{15.316118,26.357575,34.151814,40.949689,47.161846,52.969703,58.474510,63.740347,68.811143,73.718705,78.487120,83.135095,87.677608,92.126918,96.493139,100.784797,105.009146,109.172419,113.280030,117.336703,121.346598,125.313392,129.240360,133.130429,136.986224,140.810114,144.604236,148.370534,152.110769,155.826566,159.519382,163.190574,166.841381,170.472949},
{17.907290,27.526392,34.421369,40.474591,46.031948,51.246267,56.202749,60.955157,65.540263,69.984641,74.308298,78.526859,82.652695,86.695946,90.665002,94.566923,98.407713,102.192553,105.925838,109.611805,113.253633,116.854518,120.417211,123.944207,127.437747,130.899845,134.332326,137.736997,141.115282,144.468662,147.798405,151.105747,154.391821,157.657660},
{20.537903,28.628679,34.504870,39.689398,44.465510,48.958813,53.239426,57.351636,61.325692,65.183478,68.941422,72.612307,76.206277,79.731555,83.195028,86.602430,89.958614,93.267794,96.533562,99.759084,102.947217,106.100381,109.220802,112.310473,115.371183,118.404555,121.412058,124.395051,127.354758,130.292280,133.208764,136.105091,138.982184,141.840879},
{23.229982,29.684556,34.430686,38.634626,42.516172,46.173967,49.663180,53.018800,56.264782,59.418430,62.492829,65.498080,68.442358,71.332136,74.172844,76.969011,79.724792,82.443161,85.127095,87.779137,90.401487,92.996104,95.564732,98.108937,100.630145,103.129560,105.608408,108.067034,110.508463,112.931517,115.337700,117.727757,120.102387,122.462244}};

   
   
   
   }
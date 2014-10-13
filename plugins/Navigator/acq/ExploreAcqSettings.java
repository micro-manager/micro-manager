package acq;

/**
 * Container for settings specific to explore acquisition
 * @author Henry
 */
public class ExploreAcqSettings {

   public final double zTop_, zBottom_, zStep_;
   public final String dir_, name_;

   public ExploreAcqSettings(double zTop, double zBottom, double zStep, String dir, String name) {
      zTop_ = zTop;
      zBottom_ = zBottom;
      zStep_ = zStep;
      dir_ = dir;
      name_ = name;      
   }

}

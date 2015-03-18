package imagedisplay;

/**
 * This class handles notifications of the current incoming image rate (data 
 * rate) and displayed image rate.
 */
class FPSEvent {
   private double dataFPS_;
   private double displayFPS_;

   public FPSEvent(double dataFPS, double displayFPS) {
      dataFPS_ = dataFPS;
      displayFPS_ = displayFPS;
   }

   public double getDataFPS() {
      return dataFPS_;
   }

   public double getDisplayFPS() {
      return displayFPS_;
   }
}

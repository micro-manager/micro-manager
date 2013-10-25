package HDF;

/**
 *
 * @author henrypinkard
 */
public class PipelineImage {
   
   public int channel, slice, frame, width, height;
   public Object pixels;
   public String time;
   public long[][] histograms;
   public String acqDate;
   
   public PipelineImage(Object pix, int chnl, int slce, int frm, int wdth, int hgt, 
           String tme,  String date) {
      channel = chnl;
      slice = slce;
      frame = frm;
      pixels = pix;
      width = wdth;
      height = hgt;
      time = tme;
      acqDate = date;
   }
}

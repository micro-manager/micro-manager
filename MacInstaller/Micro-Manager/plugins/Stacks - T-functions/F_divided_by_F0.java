import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import java.io.File;
import ij.measure.*;

public class F_divided_by_F0 implements PlugIn {

	public void run(String arg) {
		if (IJ.versionLessThan("1.18o"))
				return;

		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		ImageProcessor ip = imp.getProcessor();
		Calibration cal = imp.getCalibration().copy();
		double xScale = cal.pixelWidth ;
		double yScale = cal.pixelHeight;
		double tScale = cal.pixelDepth;

		Roi roi = imp.getRoi();
		if (!(roi!=null && roi.getType()==roi.LINE) )
			{IJ.error("Straight line selection required."); return;}

		Line line = (Line)roi;
		double newXscale = Math.sqrt((yScale*yScale)+(xScale*xScale));
		cal.pixelWidth=(tScale);
		cal.pixelHeight=(newXscale);
		cal.setUnit("units");

		GenericDialog gd = new GenericDialog("F÷F0");
		gd.addNumericField("How many frames to average?",6,0);
		gd.showDialog();

       	if (gd.wasCanceled()) return ;
	int ageFrames=  gd.getNextChoiceIndex();
	
	
  String path= System.getProperty("user.dir")+File.separator+"macros"+File.separator;
	String macroString = "run="+path+"FdivF0.txt how="+ageFrames;
		IJ.run("Run... ",macroString  );
	ImagePlus imp2 = WindowManager.getCurrentImage();
	imp2.setCalibration(cal);
	imp2.getWindow().repaint();


	}

}

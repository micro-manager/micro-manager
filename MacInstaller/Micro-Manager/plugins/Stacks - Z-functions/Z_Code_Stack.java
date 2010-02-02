import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.measure.Calibration;

public class Z_Code_Stack implements PlugIn {
	
	ImageStack depthCoded;
	ImageProcessor ipc3;

	public void run(String arg) {
    	ImagePlus imp1 = WindowManager.getCurrentImage();
        	if (imp1==null) {
            		IJ.noImage();
            		return;
        		}
        	int stackSize = imp1.getStackSize();
	if (stackSize<2) {
            		IJ.error("Reslicer", "Stack required");
            		return;
        		}
	Calibration cal = imp1.getCalibration();
	int width = imp1.getWidth();
	int height = imp1.getHeight();
	IJ.newImage("New", "8-bit Black", 1, 1, stackSize );
	IJ.run("Spectrum");
	ImagePlus imp2 = WindowManager.getCurrentImage();
	ImageWindow winimp2 = imp2.getWindow();
	ImageStack img1 = imp1.getStack();
	ImageStack img2 = imp2.getStack();
	ImageStack dcStack = new ImageStack (width,height);
	double val = 0;

	for (int i=1; i<=stackSize ; i++)
		{imp2.setSlice(i);
		val = ((double)255)*((double)i/(double)stackSize );
		IJ.run("Add...", "slice value="+val);
		}

	ImageProcessor ip2 = imp2.getProcessor();
	IJ.run("RGB Color");
	ImageProcessor ip1 = imp1.getProcessor();
	int grey=0, r=0, g=0, b=0;	
	double newGrey = 0;
	int [] rgb = new int [3];
	int [] rgb2 = new int [3];
	for (int n=1; n<=stackSize; n++)
		{ipc3=  new ColorProcessor(width, height);
		ip1 = img1.getProcessor(n);
		imp2.setSlice(n);
          		ColorProcessor ipc2 =  (ColorProcessor) imp2.getProcessor();

		for (int x=0; x<width; x++)
			{for (int y=0; y<height; y++)	
				{
				ipc2.getPixel(0,0,rgb);
							   	
				rgb[0]=(int)(((double)ip1.getPixel(x,y)/(double)255)*(double)rgb[0]);
				rgb[1]=(int)(((double)ip1.getPixel(x,y)/(double)255)*(double)rgb[1]);
				rgb[2]=(int)(((double)ip1.getPixel(x,y)/(double)255)*(double)rgb[2]);
				ipc3.putPixel(x,y,rgb);
						
				}
			}
		dcStack.addSlice("depth coded", ipc3);
		}
	new ImagePlus("Depth Coded Stack", dcStack).show();
	ImagePlus impD = WindowManager.getCurrentImage();
	ImageWindow winD = impD.getWindow();
	
	impD.setCalibration(cal);
	imp2.changes = false;
	winimp2.close();
	System.gc();
WindowManager.setCurrentWindow(winD);

	}

}

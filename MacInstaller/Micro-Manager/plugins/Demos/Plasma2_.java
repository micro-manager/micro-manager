import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import ij.plugin.*;

/*	This is an ImageJ plugin version of the "Plasma" applet at
		http://rsb.info.nih.gov/plasma
	It creates an animated display by summing four sine waves into an array.
*/
public class Plasma2_ implements PlugIn {

	boolean noUpdates = false;
	boolean showFPS = true;
	int width =600;
	int height = 450;
	int w,h,size;
	MemoryImageSource source;
	Thread runThread;
	long firstFrame, frames, fps;
	IndexColorModel icm;
	int[] waveTable;
	byte[][] paletteTable;
	byte[] pixels;
	boolean running = true;
	ImagePlus imp;

	public void run(String arg) {
		init();
	}

	public void init() {
		w = width/3;
		h = height/3;
		pixels = new byte[width*height];
		size = (int) ((w+h)/2)*4;
		waveTable = new int[size];
		paletteTable = new byte[3][256];
		calculatePaletteTable();
		//source=new MemoryImageSource(width, height, icm, pixels, 0, width);
		//source.setAnimated(true);
		//source.setFullBufferUpdates(true);
		//img=createImage(source);
		calculateWaveTable();
		imp = new ImagePlus("Plasma", new ByteProcessor(width, height, pixels, icm));
		imp.show();
		firstFrame=System.currentTimeMillis();
		run();
	}
	
	void calculateWaveTable() {
		for(int i=0;i<size;i++)
			waveTable[i]=(int)(32*(1+Math.sin(((double)i*2*Math.PI)/size)));
	}

	int FadeBetween(int start,int end,int proportion) {
		return ((end-start)*proportion)/128+start;
	}

	void calculatePaletteTable() {
		for(int i=0;i<128;i++) {
			paletteTable[0][i]=(byte)FadeBetween(0,255,i);
			paletteTable[1][i]=(byte)0;
			paletteTable[2][i]=(byte)FadeBetween(255,0,i);
		}
		for(int i=0;i<128;i++) {
			paletteTable[0][i+128]=(byte)FadeBetween(255,0,i);
			paletteTable[1][i+128]=(byte)0;
			paletteTable[2][i+128]=(byte)FadeBetween(0,255,i);
		}
		icm = new IndexColorModel(8, 256, paletteTable[0], paletteTable[1], paletteTable[2]);
	}

	public void run() {
		int x,y;
		int index, index2, index3;
		int tempval;
		int spd1=2,spd2=5,spd3=1,spd4=4;
		int pos1=0,pos2=0,pos3=0,pos4=0;
		int tpos1,tpos2,tpos3,tpos4;
		int inc1=6,inc2=3,inc3=3,inc4=9;
		byte result;

		while(running) {
			tpos1=pos1; tpos2=pos2;
			for(y=0; y<h; y++) {
				tpos3=pos3; tpos4=pos4;
				tpos1%=size; tpos2%=size;
				tempval=waveTable[tpos1] + waveTable[tpos2];
				index = y*width*3;
				index2 = index + width;
				index3 = index2 + width; 
				for(x=0; x<w; x++) {
					tpos3%=size; tpos4%=size;
					result = (byte)(tempval + waveTable[tpos3] + waveTable[tpos4]);
					pixels[index++]=result;
					pixels[index++]=result;
					pixels[index++]=result;
					pixels[index2++]=result;
					pixels[index2++]=result;
					pixels[index2++]=result;
					pixels[index3++]=result;
					pixels[index3++]=result;
					pixels[index3++]=result;
					tpos3+=inc3;  tpos4+=inc4;
				}
				index += w*2;
				tpos1+=inc1; tpos2+=inc2;
			}
			pos1+=spd1; pos2+=spd2; pos3+=spd3; pos4+=spd4;
			imp.updateAndDraw();
			showFPS();
		}
	}

	void showFPS() {
		frames++;
		if (System.currentTimeMillis()>firstFrame+4000) {
			firstFrame=System.currentTimeMillis();
			fps=frames;
			frames=0;
		}
		IJ.showStatus((int)((fps+0.5)/4) + " fps");
	}

}

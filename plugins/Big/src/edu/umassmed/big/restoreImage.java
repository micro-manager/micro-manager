/*
 * 
 * Karl Bellve
 * Biomedical Imaging Group
 * University of Massachusetts Medical School
 * Karl.Bellve@umassmed.edu
 * http://big.umassmed.edu/
 *
 */

package edu.umassmed.big;

public class restoreImage {
	// generate JNI header 
	// from directory (/storage/big1/kdb/projects/workspace/BIG/bin)
	// javah -d ../../epr/src/ -jni edu.umassmed.big.restoreImage
	private int  nXYpadding = -1, nZpadding = -1;
	private native int epr_setKey(String key);
	private native int epr_setCudaDevice(int nCuda_Device);
	private native int epr_setPadding(int nXYpad,int nZpad);
	private native int epr_setImage(short[]pImage_as_Short, int X, int Y,int Z); 
	private native int epr_setPSF(short[]pPSF_as_Short, int X, int Y,int Z); 
	private native void epr_setDebug(int debug); 
	//public native int epr_execute(short[] OBJECT, float alpha, float convergence, float fscale, int max_iterations, int[] iterations, float[] residuals, float[] converged);
	private native int epr_execute(short[] OBJECT, float alpha, float convergence, float fscale, int max_iterations);
	
	
	public restoreImage(String key)
	{
		System.out.println("restoreImage: Setting up restoration");
		
		System.out.println("restoreImage: Setting key");
		epr_setKey(key);
		
		System.out.println("restoreImage: Set CUDA Device: 1");
		epr_setCudaDevice(1);
		
	};
	public restoreImage(String key,int nCuda)
	{
		System.out.println("restoreImage: Setting up restoration");
		
		System.out.println("restoreImage: Setting key");
		epr_setKey(key);
		
		System.out.println("restoreImage: Selecting CUDA Device: " + nCuda);
		epr_setCudaDevice(nCuda);
		
	};
	
	public void setPadding(int XY, int Z) {
		if (XY >= 0) nXYpadding = XY;
		else nXYpadding = -1;
		if (Z >= 0) nZpadding = Z;
		else nZpadding = -1;
		epr_setPadding(XY,Z);
	}
	
	public int setImage(short[]pImage_as_Short, int X, int Y,int Z){
		return(epr_setImage(pImage_as_Short, X,Y,Z)); 	
	}
	public int setPSF(short[]pPSF_as_Short, int X, int Y,int Z) {
		if (nXYpadding == -1) {
			// padding wasn't set, so let's set default padding
			System.out.println("restoreImage: Setting up default padding");
			setPadding(nXYpadding, nZpadding);
		}
		return(epr_setPSF(pPSF_as_Short, X,Y,Z)); 	
	}

	public void setDebug(int debug){
		epr_setDebug(debug); 	
	}
	
	public int execute(short[] OBJECT, float alpha, float convergence, float fscale, int max_iterations){
		return(epr_execute(OBJECT, alpha, convergence, fscale, max_iterations));	
	}
	static {
		System.loadLibrary("epr");
	}
		
		
}
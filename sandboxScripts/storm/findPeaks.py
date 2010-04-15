from scipy.ndimage import measurements
import time
from scipy import weave
from scipy.optimize import minpack
from numpy import linalg
from tkFileDialog import askopenfilenames, askdirectory
from scipy import argmin
import Image
from numpy import *
from pylab import *

useNLLS = True

hw = 5
border = 10
xp,yp = mgrid[0:(2*hw+1),0:(2*hw+1)]

maxFinderCode = """
	#include <limits.h>
	long maxValue = LONG_MIN;
	int x_max = -1;
	int y_max = -1;
	
	for (int i=0; i<nx; ++i)
	{
		for (int j=0; j<ny; ++j)
			{
				if (pixels(i,j) > maxValue)
				{
					maxValue = pixels(i,j);
					x_max = i;
					y_max = j;
				}
			}
	}
	
	py::tuple results(2);
	results[0] = x_max;
	results[1] = y_max;
	return_val = results;
			
"""
			
gaussianFunctionCode = """
	double _2sigma = 2*pow(p(4),2);
	
	for (int i=0;i<nx;++i)
		for(int j=0;j<ny;++j)
			result(i,j) = p(0) + p(1)*exp(-(pow(i-p(2),2) + pow(j-p(3),2))/_2sigma);

"""



def findMax(pixels):
	tA = time.time()
	nx,ny = pixels.shape
	result = weave.inline(maxFinderCode,('pixels','nx','ny'), 
		type_converters=weave.converters.blitz)
	#print(time.time()-tA)
	return result

def findNextMolecule(imgTemp, threshold, hw = hw, border = border):
	t3 = time.time()
	#xm,ym = measurements.maximum_position(imgTemp[hw:-hw,hw:-hw])
	xm,ym = findMax(imgTemp[border:-border,border:-border])
	#print("maximum_position")
	#print(time.time()-t3)
	xm += border
	ym += border
	#print(xm,ym,imgTemp[xm,ym])
	if imgTemp[xm,ym]>threshold:
		#print "exceeds threshold"
		patch = imgTemp[(-hw+xm):(hw+1+xm),(-hw+ym):(hw+1+ym)]
		if len(patch) > 0:
			if useNLLS:
				params = fitGaussianNLLS(xp,yp,patch)
			else:
				params = fitGaussianLLS(xp,yp,patch)
			#print(params)
			if abs(params[2]-hw)>hw/2. or abs(params[3]-hw)>hw/2.: #reject wild eccentrics
				return None
			floatPatch = patch.astype("float")
			floatPatch -= gaussianFunction(params, xp, yp) - params[0]
			floatPatch = clip(floatPatch,0,65535)
			imgTemp[-hw+xm:hw+1+xm,-hw+ym:hw+1+ym] = floatPatch
			
			return params[2]+xm-hw, params[3]+ym-hw
	return None
	
	
def gaussianFunction(p, xp, yp):
	nx, ny = xp.shape
	result = zeros((nx,ny))
	weave.inline(gaussianFunctionCode, ('p', 'nx', 'ny', 'result'), 
		type_converters=weave.converters.blitz)
	return result
	
def residualFunction(p, xp, yp, patch):
	return (gaussianFunction(p, xp, yp) - patch).flatten()

def fitGaussianNLLS(xp,yp,patch):
	#print("patch: ",patch)
	paramsGuess = array([0,patch[hw,hw],hw,hw,1])
	tA = time.time()
	(paramsOut, cov, infodict, msg, ier) = minpack.leastsq(residualFunction,paramsGuess,(xp,yp,patch), full_output=1, ftol = .1, xtol = .1)
	#print("leastsq")
	#print("nfev = %d" % infodict['nfev'])
	#print(time.time()-tA)
	return paramsOut
	

def fitGaussianLLS(xp,yp,patch):
	#print(patch)
	tA = time.time()
	n = len(xp.flatten())
	A = vstack((xp.flatten(),yp.flatten(),log(patch.flatten()),ones((1,n)))).transpose()
	B = (xp.flatten()**2 + yp.flatten()**2).transpose()
	#print A
	#print B
	a = linalg.lstsq(A,B)[0]
	result = array((0.0, exp(-(a[3]+a[0]**2/4+a[1]**2/4)/a[2]), a[0]/2, a[1]/2, sqrt(-a[2]/2)))
	#print(time.time()-tA)
	return result
	
	
def findMolecules(img, threshold, hw = hw, border = border):

	#print(hw)
	molList = []
	imgTemp = img.copy()
	
	t1 = time.time()
	q=0
	while True:
		q=q+1
		tA = time.time()
		mol = findNextMolecule(img, threshold, hw, border)
		#print(q,mol)
		#print(time.time()-tA)
		if mol != None:
			molList.append(mol)
		else:
			break
	t2 = time.time()
	n = len(molList)
	return molList

def getArrayFromImageFile(filename):
	peaksImage = Image.open(filename)
	return asarray(peaksImage.getdata(), dtype = "uint16").reshape(peaksImage.size)
	
	
def molDist(m1, m2):
	return sqrt((m1[0]-m2[0])**2 + (m1[1]-m2[1])**2)
	
matchMoleculeCode = """
double minDistSq;
int bestMolecule;
double curDistSq;
double dx;
double dy;

for (int i=0;i<n1;++i)
{
	minDistSq = dist*dist;
	bestMolecule = -1;
	for (int j=0;j<n2;++j)
	{
		dx = x1(i,0)-x2(j,0);
		dy = x1(i,1)-x2(j,1);
		curDistSq = dx*dx + dy*dy;
		if (curDistSq < minDistSq)
		{
			bestMolecule = j;
			result(i) = j;
		}
	}
	//return_val = result;
}
"""
	
	
def matchMolecules(molList1, molList2, dist):
	#print molList1
	pairs = []
	t1 = time.time()
	molList2Array = array(molList2)
	for m1 in molList1:
		x,y = m1
		curDistsSq = (molList2Array[:,0]-x)**2 + (molList2Array[:,1]-y)**2
		minIndex = argmin(curDistsSq)
		if curDistsSq[minIndex] < dist:
			pairs.append((m1,molList2[minIndex]))
	print "total time %g" % (time.time()-t1)
	return pairs

def matchMoleculesOld(molList1, molList2, dist):
	pairs = []
	t1 = time.time()
	for m1 in molList1:
		bestMolecule = None
		for m2 in molList2:
			curDist = molDist(m1,m2)
			if curDist < dist:
				bestMolecule = m2
				minDist = curDist
		if bestMolecule is not None:
			pairs.append((m1,bestMolecule))
	print "total time %g" % (time.time()-t1)
	return pairs			
	
	
def generateQuadraticTransformFromPointPairs(pairs):
	pairsArray = array(pairs)
	l = len(pairsArray)
	srcs = pairsArray[:,0,:]
	dests = pairsArray[:,1,:]
	srcsQ = hstack((srcs*srcs,srcs,ones((l,1))))
	qtrans = linalg.lstsq(srcsQ,dests)[0].transpose()
	return qtrans
	
def generateAffineTransformFromPointPairs(pairs, plotData=False, plotTitle=""):
	pairsArray = array(pairs)
	l = len(pairsArray)
	srcs = pairsArray[:,0,:]
	#print pairs
	#print l
	#print len(srcs)
	#print srcs
	srcs = hstack((srcs,ones((l,1))))
	dests = pairsArray[:,1,:]
	dests = hstack((dests,ones((l,1))))
	transform = linalg.lstsq(srcs,dests)[0].transpose()
	
	if plotData:
		figure()
		plot(srcs[:,0],srcs[:,1],'g+')
		plot(dests[:,0],dests[:,1],'rx')
		title(plotTitle)
		srcsT = array(matrix(srcs)*transform.transpose())
		figure()
		plot(srcsT[:,0],srcsT[:,1],'b+')
		plot(dests[:,0],dests[:,1],'rx')
		title(plotTitle)
		figure()
		quiver(dests[:,0],dests[:,1],1*(srcs[:,0]-dests[:,0]),1*(srcs[:,1]-dests[:,1]),color='black',units='x',angles='xy',scale=0.01)
		title(plotTitle)
		figure()
		quiver(dests[:,0],dests[:,1],1*(srcsT[:,0]-dests[:,0]),1*(srcsT[:,1]-dests[:,1]),color='black',units='x',angles='xy',scale=0.01)
		title(plotTitle)
		figure()
		plot(srcsT[:,0]-dests[:,0],srcsT[:,1]-dests[:,1],'k,')
		title(plotTitle)
	
	return transform
	
def getScaleFactorFromAffineTransform(transform):
	return sqrt(abs(det(transform)))
	
def processTwoChannelImage():
	fileNames = askopenfilenames(defaultextension = ".tif", initialdir = "~/Documents/stormData", parent = None, title = "Please choose two image files.")	
	img0 = getArrayFromImageFile(fileNames[0])
	img1 = getArrayFromImageFile(fileNames[1])
	mols0 = findMolecules(img0, 5000)
	mols1 = findMolecules(img1, 5000)
	#print mols0
	pairs = matchMolecules(mols0,mols1,3)
	transform = generateAffineTransformFromPointPairs(pairs, True, fileNames[0])
	transform2 = generateQuadraticTransformFromPointPairs(pairs)
	print(transform2)
	print("Scaling Factor: %g" % getScaleFactorFromAffineTransform(transform))
	return transform
	
def processImageSeries():
	dirPath = askdirectory()
	rawFiles = os.listdir(dirPath)
	tiffFiles = [dirPath + "/" + rawFile for rawFile in rawFiles if rawFile.endswith(".tif")]
	startFile = tiffFiles[0]
	img0 = getArrayFromImageFile(startFile)
	mols0 = findMolecules(img0, 8000)
	traj = []
	
	for tiffFile in tiffFiles:
		img = getArrayFromImageFile(tiffFile)
		mols = findMolecules(img, 8000)
		pairs = matchMolecules(mols0,mols,1)
		transform = generateAffineTransformFromPointPairs(pairs, False)
		print("Scaling Factor: %g" % getScaleFactorFromAffineTransform(transform))
		traj.append((transform[0,2],transform[1,2]))

	trajA = array(traj)

	figure()
	plot(trajA[:,0],trajA[:,1])
	title("XY trajectory for\n"+dirPath)
	xlabel("x")
	ylabel("y")
	return trajA

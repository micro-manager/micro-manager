from scipy.ndimage import measurements
import time
from scipy import weave
from scipy.optimize import minpack
from numpy import linalg

useNLLS = True

hw = 3

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

def findNextMolecule():
	t3 = time.time()
	#xm,ym = measurements.maximum_position(imgTemp[hw:-hw,hw:-hw])
	xm,ym = findMax(imgTemp[hw:-hw,hw:-hw])
	#print("maximum_position")
	#print(time.time()-t3)
	xm += hw
	ym += hw
	if imgTemp[xm,ym]>threshold:
		#print "exceeds threshold"
		patch = imgTemp[(-hw+xm):(hw+1+xm),(-hw+ym):(hw+1+ym)]
		if len(patch) > 0:
			if useNLLS:
				params = fitGaussianNLLS(xp,yp,patch)
			else:
				params = fitGaussianLLS(xp,yp,patch)
			#print(params)
			imgTemp[-hw+xm:hw+1+xm,-hw+ym:hw+1+ym] -= gaussianFunction(params, xp, yp)
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
	
threshold = 1200

xp,yp = mgrid[0:(2*hw+1),0:(2*hw+1)]


molList = []
imgTemp = img.copy()

t1 = time.time()
while True:
	tA = time.time()
	mol = findNextMolecule()
	#print("mol total")
	#print(time.time()-tA)
	if mol != None:
		molList.append(mol)
	else:
		break
t2 = time.time()
n = len(molList)
print("%f s per molecule" % ((t2-t1)/n,))

#fakeMolList = sort(zip(x0s, y0s),0)
#detectedMolList = sort(molList,0)
#print detectedMolList - fakeMolList

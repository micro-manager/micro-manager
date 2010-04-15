from numpy import *
from scipy import *
from pylab import *
from scipy import stats

w = 512
h = 512

A0 = 300. #average intensity
b = 1000. #background
sigma = 1.5
N = 20.

img = b*ones((w,h),int16)
[x,y] = mgrid[0:512,0:512]

x0s = w*random(N)
y0s = h*random(N)

"""This is an approximation, because it takes point-values instead
of integrating the value for each pixel."""

for x0,y0 in zip(x0s,y0s):
	#print x0,y0
	z0 = A0*exp(-((x-x0)**2 + (y-y0)**2)/(2*sigma**2))
	img += z0
	#x0r = round(x0,0)
	#y0r = round(y0,0)
	#print(x0r,y0r)
	#img[x0r-5:x0r+6][y0r-5:y0r+6] += z0[x0r-5:x0r+6][y0r-5:y0r+6]
	

img = stats.poisson.rvs(img)

figure()
imshow(img, cmap = cm.gray, interpolation = None)
ion()

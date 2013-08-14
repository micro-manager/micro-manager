from pylab import *
ion()

import MMCorePy
mmc = MMCorePy.CMMCore()
mmc.loadDevice("cam","DemoCamera","DCam")
mmc.initializeDevice("cam")

print "Test acquire and display of monochrome images."

figure()
mmc.setCameraDevice("cam")
mmc.snapImage()
im1 = mmc.getImage()
imshow(im1,cmap = cm.gray)

"""
print "Test acquire and display of RGB images."

figure()
mmc.setCameraDevice("rgbcam")
mmc.snapImage()
im2 = mmc.getRGB32Image()
imshow(im2)
"""

print "Test MMCore.registerCallback():"

class PyMMEventCallBack(MMCorePy.MMEventCallback):
	def onPropertiesChanged(self):
		print "PyMMEventCallBack onPropertiesChanged() called"

callback = PyMMEventCallBack()
mmc.registerCallback(callback)
mmc.setCameraDevice("cam")
mmc.setProperty("cam","ScanMode","1")

print "Test MMCore.getLastImageMD():"

mmc.startSequenceAcquisition(1,1,False)
while(mmc.isSequenceRunning()):
	pass
md = MMCorePy.Metadata()
img = mmc.getLastImageMD(0,0,md)
print img
print md.Dump()

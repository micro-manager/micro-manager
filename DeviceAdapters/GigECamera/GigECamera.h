///////////////////////////////////////////////////////////////////////////////
// FILE:          GigECamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   An adapter for Gigbit-Ethernet cameras using an
//                SDK from JAI, Inc.  Users and developers will
//                need to download and install the JAI SDK and control tool.
//
// AUTHOR:        David Marshburn, UNC-CH, marshbur@cs.unc.edu, Jan. 2011
//

#ifndef _GIGECAMERA_H_
#define _GIGECAMERA_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include "../../MMDevice/Debayer.h"

#include <string>
#include <map>

#include "JAISDK.h"

#include "GigENodes.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103


const char* const g_Keyword_Binning_Vertical	= "Binning Vertical";
const char* const g_Keyword_Binning_Horizontal	= "Binning Horizontal";
const char* const g_Keyword_Image_Width			= "Image Width";
const char* const g_Keyword_Image_Height		= "Image Height";
const char* const g_Keyword_Camera_Choice		= "Available Cameras";
const char* const g_Keyword_Sensor_Width		= "Sensor Width";
const char* const g_Keyword_Sensor_Height		= "Sensor Height";
const char* const g_Keyword_Image_Width_Max		= "Image Width Max";
const char* const g_Keyword_Image_Height_Max	= "Image Height Max";
const char* const g_Keyword_Pixel_Format		= "Pixel Format";
const char* const g_Keyword_Pixel_Size			= "Pixel Size";
const char* const g_Keyword_Pixel_Color_Filter	= "Pixel Color Filter";
const char* const g_Keyword_Frame_Rate			= "Acquisition Frame Rate";
const char* const g_Keyword_Acquisition_Mode	= "Acquisition Mode";

#define NODE_NAME_WIDTH         (int8_t*)"Width"
#define NODE_NAME_HEIGHT        (int8_t*)"Height"
#define NODE_NAME_PIXELFORMAT   (int8_t*)"PixelFormat"
#define NODE_NAME_GAIN          (int8_t*)"GainRaw"
#define NODE_NAME_ACQSTART      (int8_t*)"AcquisitionStart"
#define NODE_NAME_ACQSTOP       (int8_t*)"AcquisitionStop"
#define NODE_NAME_EXPMODE		(int8_t*)"ExposureMode"
#define NODE_NAME_SHUTTERMODE	(int8_t*)"ShutterMode"
#define NODE_NAME_TIMED			(int8_t*)"Timed"
#define NODE_NAME_EXPOSURETIMEABS (int8_t*)"ExposureTimeAbs"

extern const char* g_CameraDeviceName;

// the largest possible pixel in the GenICam spec is 6 bytes (16-bit rgb)
#define LARGEST_PIXEL_IN_BYTES 6

//////////////////////////////////////////////////////////////////////////////
// CGigECamera class
//////////////////////////////////////////////////////////////////////////////
class CGigECamera : public CCameraBase<CGigECamera>
{
public:
	CGigECamera();
	~CGigECamera();

	////////////////
	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	/**
	* Obtains device name.
	* Required by the MM::Device API.
	*/
	void GetName( char* name ) const {  CDeviceUtils::CopyLimitedString( name, g_CameraDeviceName );  }

	/////////////////
	// MMCamera API
	// ------------
	int SnapImage();

	/**
	* Returns pixel data.
	* Required by the MM::Camera API.
	* GetImageBuffer will be called shortly after SnapImage returns.
	* Use it to wait for camera read-out and transfer of data into memory
	* Return a pointer to a buffer containing the image data
	* The calling program will assume the size of the buffer based on the values
	* obtained from GetImageBufferSize(), which in turn should be consistent with
	* values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
	* The calling program allso assumes that camera never changes the size of
	* the pixel buffer on its own. In other words, the buffer can change only if
	* appropriate properties are set (such as binning, pixel type, etc.)
	* Multi-Channel cameras should return the content of the first channel in this call.
	*
	*/
	const unsigned char* GetImageBuffer();

	/**
	* Returns the number of components in this image.  This is '1' for grayscale cameras,
	* and '4' for RGB cameras.
	*/
	unsigned GetNumberOfComponents() const;

	/**
    * Returns the name for each component
    */
	int GetComponentName(unsigned comp, char* name);

	/**
	* Returns the size in bytes of the image buffer.
	* Required by the MM::Camera API.
	* For multi-channel cameras, return the size of a single channel
	*/
	long GetImageBufferSize() const {  return img_.Width() * img_.Height() * GetImageBytesPerPixel();  }

	/**
	* Returns image buffer X-size in pixels.
	* Required by the MM::Camera API.
	*/
	unsigned GetImageWidth() const {  return img_.Width();  }

	/**
	* Returns image buffer Y-size in pixels.
	* Required by the MM::Camera API.
	*/
	unsigned GetImageHeight() const {  return img_.Height();  }

	/**
	* Returns image buffer pixel depth in bytes.
	* Required by the MM::Camera API.
	*/
	unsigned GetImageBytesPerPixel() const {  return img_.Depth();  }

	/**
	* Returns the bit depth (dynamic range) of the pixel.
	* This does not affect the buffer size, it just gives the client application
	* a guideline on how to interpret pixel values.
	* Required by the MM::Camera API.
	*/
	unsigned GetBitDepth() const;

	/**
	* Returns the current exposure setting in milliseconds.
	* Required by the MM::Camera API.
	*/
	double GetExposure() const;

	/**
	* Sets exposure in milliseconds.
	* Required by the MM::Camera API.
	*/
	void SetExposure( double exp ) {  SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));  }

	/**
	* Returns the current binning factor.
	* Required by the MM::Camera API.
	*/
	int GetBinning() const;

	/**
	* Sets binning factor.
	* Required by the MM::Camera API.
	*/
	int SetBinning( int binSize ) {  return SetProperty( MM::g_Keyword_Binning, CDeviceUtils::ConvertToString( binSize ) );  }

   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

	// ROI-related functions
	int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
	int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
	int ClearROI();

	// sequence-acquisition-related functions
	int PrepareSequenceAcqusition() { return DEVICE_OK; }
	int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
	int StopSequenceAcquisition();
	bool IsCapturing();

	// pixel-size-related functions
	// the GenICam spec and the JAI sdk have no way to query sensor pixel size.
	double GetNominalPixelSizeUm() const {return 1.0;}
	double GetPixelSizeUm() const {return 1.0 * GetBinning();}

	// action interface
	// ----------------
	int OnCameraChoice( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnBinning( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnBinningV( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnBinningH( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnImageWidth( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnImageHeight( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnImageWidthMax( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnImageHeightMax( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnGain( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnPixelType( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnReadoutTime( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnExposure( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnTemperature( MM::PropertyBase* pProp, MM::ActionType eAct );
	int OnFrameRate( MM::PropertyBase* pProp, MM::ActionType eAct );
	int onAcquisitionMode( MM::PropertyBase* pProp, MM::ActionType eAct );

protected:
	int SetUpBinningProperties();
	void GenerateSyntheticImage(ImgBuffer& img, double exp);
	int ResizeImageBuffer();
	void UpdateExposureRange();

	void EnumerateAllNodesToLog();
   void EnumerateAllFeaturesToLog();
   void EnumerateAllFeaturesToLog( int8_t* parentNodeName, int indentCount );
   std::string StringForNodeType( J_NODE_TYPE nodeType );
   std::string StringForAccessMode( J_NODE_ACCESSMODE accessMode );

	// members from umanager
	ImgBuffer img_;
	double readoutUs_;
	MM::MMTime readoutStartTime_;
	bool stopOnOverflow_;
	long scanMode_;
	unsigned int bitDepth_;
	bool color_;
	unsigned roiX_;
	unsigned roiY_;

	// members used with the JAI library
	FACTORY_HANDLE hFactory;
	CAM_HANDLE hCamera;
	bool cameraOpened;
	bool cameraInitialized;
	THRD_HANDLE hThread;
	std::string cameraName;
	bool snapImageDone;
	bool snapOneImageOnly;
	bool doContinuousAcquisition;
	bool stopContinuousAcquisition;
	bool continuousAcquisitionDone;
	std::map< std::string, std::string > cameraNameMap;
	std::map< std::string, std::string > frameRateMap;
	std::map< std::string, std::string > pixelFormatMap;
	std::map< std::string, std::string > acqModeMap;

	// which node to use for Exposure property
	bool useExposureTime;
	bool useExposureTimeAbs;
	bool useExposureTimeAbsInt;

	// other members
	GigENodes* nodes;
	unsigned char* buffer_;
	size_t bufferSizeBytes;

	void SnapImageCallback( J_tIMAGE_INFO* );
	J_STATUS_TYPE setupImaging();

	/**
	* Sets color_, bitDepth_ and byteDepth parameters dependent of the pixel type format
	* this routine can be used to handle special pixel type formats and decide how to convert them
	* it is either called in ResizeImageBuffer
	*/
	int testIfPixelFormatResultsInColorImage(uint32_t &byteDepth);

	/**
	* aquires the image from GigEDevice
	* does the conversion from Bayer image to color or grayscale image depending of the results of
	* testIfPixelFormatResultsInColorImage
	*/
	int aquireImage(J_tIMAGE_INFO* imageBuffer, unsigned char* buffer);
};

#endif //_GigECAMERA_H_

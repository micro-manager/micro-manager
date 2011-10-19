///////////////////////////////////////////////////////////////////////////////
// FILE:          GigECamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   An adapter for Gigbit-Ethernet cameras using an
//				  SDK from JAI, Inc.  Users and developers will 
//				  need to download and install the JAI SDK and control tool.
//                
// AUTHOR:        David Marshburn, UNC-CH, marshbur@cs.unc.edu, Jan. 2011
//

#ifndef _GIGECAMERA_H_
#define _GIGECAMERA_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>

#include <Jai_Factory.h>

#include "GigENodes.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103


namespace MM 
{
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
	const char* const g_Keyword_Frame_Rate			= "Acquisition Frame Rate";
} 

extern const char* g_CameraDeviceName;
extern const char* g_PixelType_8bit;
extern const char* g_PixelType_8bitSigned;
extern const char* g_PixelType_10bit;
extern const char* g_PixelType_10bitPacked;
extern const char* g_PixelType_12bit;
extern const char* g_PixelType_12bitPacked;
extern const char* g_PixelType_14bit;
extern const char* g_PixelType_16bit;

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
	const unsigned char* GetImageBuffer();

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
	unsigned GetBitDepth() const {  return bitDepth_;  }

	/**
	* Returns the size in bytes of the image buffer.
	* Required by the MM::Camera API.
	*/
	long GetImageBufferSize() const {  return img_.Width() * img_.Height() * GetImageBytesPerPixel();  }

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


protected:
	int SetAllowedBinning();
	void GenerateSyntheticImage(ImgBuffer& img, double exp);
	int ResizeImageBuffer();
	void UpdateExposureRange();

	void enumerateAllNodesToLog();

	// members from umanager
	ImgBuffer img_;
	double readoutUs_;
	MM::MMTime readoutStartTime_;
	bool stopOnOverflow_;
	long scanMode_;
	int bitDepth_;
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

	// other members
	GigENodes* nodes;
	unsigned char* buffer;
	size_t bufferSizeBytes;

	void SnapImageCallback( J_tIMAGE_INFO* );
	J_STATUS_TYPE setupImaging();

};

#endif //_GigECAMERA_H_

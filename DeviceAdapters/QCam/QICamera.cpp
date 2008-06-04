///////////////////////////////////////////////////////////////////////////////
// FILE:          QICamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Micro-Manager plugin for QImaging cameras using the QCam API.
//                
// AUTHOR:        QImaging
//
// COPYRIGHT:     Copyright (C) 2007 Quantitative Imaging Corporation (QImaging).
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// CVS:           $Id: QICamera.cpp,v 1.23 2007/06/01 16:58:26 maustin Exp $
//

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#elif __APPLE_CC__
	#include <errno.h>
#endif

#include "QICamera.h"
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>
using namespace std;

#define			PRINT_FUNCTION_NAMES			1

// camera device name
const char* g_CameraDeviceName = "QCamera";


// windows DLL entry code
#ifdef WIN32
   BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                          DWORD  ul_reason_for_call, 
                          LPVOID /*lpReserved*/
		   			 )
   {
   	switch (ul_reason_for_call)
   	{
   	case DLL_PROCESS_ATTACH:
  	   case DLL_THREAD_ATTACH:
   	case DLL_THREAD_DETACH:
   	case DLL_PROCESS_DETACH:
   		break;
   	}
       return TRUE;
   }
#endif

 
bool ReadoutSpeedStringSortPredicate(const std::string& lhs, const std::string& rhs)
{
	double			lhsFloat, rhsFloat;


	// convert the string to a float to see what
	// number it is
	lhsFloat = atof(lhs.c_str());
	rhsFloat = atof(rhs.c_str());

	return lhsFloat > rhsFloat;
}


void QCAMAPI PreviewCallback
(
	void*				userPtr,			// User defined
	unsigned long		userData,			// User defined
	QCam_Err			/*errcode*/,			// Error code
	unsigned long		flags				// Combination of flags (see QCam_qcCallbackFlags)
)
{
	QICamera		*theCamera = (QICamera*)userPtr;

	
	if ((flags & qcCallbackDone) == qcCallbackDone)
	{
		// set the current image number
		theCamera->SetCurrentFrameNumber(userData);

		// the exposure is done, so unblock the SnapImage call
		theCamera->ExposureDone();
	}
}


#ifdef __APPLE_CC__
#include <sys/time.h>
struct timespec* FillInTimespec (struct timespec * time, unsigned long long inNanoseconds)
{
	struct				timeval currSysTime;
	int64_t				nanosecs, secs;
	const int64_t		NANOSEC_PER_SEC = 1000000000;
	const int64_t		NANOSEC_PEC_USEC = 1000;

	// get the current system time
	gettimeofday(&currSysTime, NULL);

	// figure out how many seconds and nanoseconds to add to the current time
	nanosecs = inNanoseconds;
	if (nanosecs >= NANOSEC_PER_SEC)
	{
      secs = currSysTime.tv_sec + (inNanoseconds / NANOSEC_PER_SEC);      
    }
	else
    {
      secs = currSysTime.tv_sec;
    }
	
	nanosecs = (currSysTime.tv_usec * NANOSEC_PEC_USEC) + (inNanoseconds % NANOSEC_PER_SEC);

	// fill in the structure with absolute time values
	time->tv_nsec = (long)nanosecs;
	time->tv_sec = (long)secs;

	return time;
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::InitializeModuleData\n");
	#endif

	AddAvailableDeviceName(g_CameraDeviceName, "QImaging universal camera adapter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::CreateDevice\n");
	#endif

	if (deviceName == 0)
	{
		return 0;
	}

	// decide which device class to create based on the deviceName parameter
	if (strcmp(deviceName, g_CameraDeviceName) == 0)
	{
	  // create camera
	  return new QICamera();
	}

	// ...supplied name not recognized
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::DeleteDevice\n");
	#endif

	delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// QICamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * QICamera constructor.
 * Setup default all variables and create device properties required to exist
 * before intialization. In this case, no such properties were required. All
 * properties will be created in the Initialize() method.
 *
 * As a general guideline Micro-Manager devices do not access hardware in the
 * the constructor. We should do as little as possible in the constructor and
 * perform most of the initialization in the Initialize() method.
 */
QICamera::QICamera()
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::QICamera\n");
	#endif

	m_isInitialized = false;
	m_isBusy = false;

	// create the condition so we can wait for a frame in SnapImage
#ifdef WIN32
	m_waitCondition = CreateEvent(NULL, FALSE, FALSE, NULL);
#elif __APPLE_CC__
	pthread_mutex_init(&m_waitMutex, NULL);
	pthread_cond_init(&m_waitCondition, NULL);
#endif

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
}

/**
 * QICamera destructor.
 * If this device used as intended within the Micro-Manager system,
 * Shutdown() will be always called before the destructor. But in any case
 * we need to make sure that all resources are properly released even if
 * Shutdown() was not called.
 */
QICamera::~QICamera()
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::~QICamera\n");
	#endif

#ifdef WIN32
	CloseHandle(m_waitCondition);
#elif __APPLE_CC__
	pthread_cond_signal(&m_waitCondition);
	pthread_mutex_destroy(&m_waitMutex);
	pthread_cond_destroy(&m_waitCondition);
#endif
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void QICamera::GetName(char* name) const
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::GetName\n");
	#endif

   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool QICamera::Busy()
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::Busy\n");
	#endif

	return m_isBusy;
}

/**
 * Intializes the hardware.
 * Required by the MM::Device API.
 * Typically we access and initialize hardware at this point.
 * Device properties are typically created here as well, except
 * the ones we need to use for defining initialization parameters.
 * Such pre-initialization properties are created in the constructor.
 */
int QICamera::Initialize()
{
	const unsigned short		CAMERA_STRING_LENGTH = 256;

	QCam_Err					err;
	QCam_CamListItem			cameraList[1];
	unsigned long				numOfCameras;
	char						cameraStr[CAMERA_STRING_LENGTH];
	unsigned short				major, minor, build;
	char						qcamVersionStr[256];
	char						cameraIDStr[256];
	unsigned long				ccdType;
	unsigned long				cameraType;

	
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::Initialize\n");
	#endif

	// check
	if (m_isInitialized == true)
	{
	  return DEVICE_OK;
	}

	// init the driver
	err = QCam_LoadDriver();
	CHECK_ERROR(err);

	// get a list of connected cameras
	// we are only looking for the first camera
	numOfCameras = sizeof(cameraList) / sizeof(QCam_CamListItem);

	err = QCam_ListCameras(cameraList, &numOfCameras);
	CHECK_ERROR(err);

	// check if we found a camera
	if (numOfCameras == 0)
	{
		QCam_ReleaseDriver();
	
		SetErrorText(ERR_NO_CAMERAS_FOUND, "No cameras found.  Please connect a QImaging camera and turn it on before using the QCam profile");
		return ERR_NO_CAMERAS_FOUND;
	}

	// open the first camera
	err = QCam_OpenCamera(cameraList[0].cameraId, &m_camera);
	CHECK_ERROR(err);
	
	// read the camera settings
	err = QCam_ReadDefaultSettings(m_camera, &m_settings);
	CHECK_ERROR(err);

	// only mono cameras are supported in the uManager 1.0 release
	err = QCam_GetInfo(m_camera, qinfCcdType, &ccdType);
	CHECK_ERROR(err);

	if (ccdType != qcCcdMonochrome)
	{
		SetErrorText(DEVICE_NOT_SUPPORTED, "Only monochrome cameras are supported in uManager.");
		LogMessage("Only monochrome cameras are supported in uManager", false);

		return DEVICE_NOT_SUPPORTED;
	}

	// turn post processing on
	/*err = QCam_SetParam(&m_settings, qprmDoPostProcessing, true);
	CHECK_ERROR(err);

	err = QCam_SetParam(&m_settings, qprmPostProcessBayerAlgorithm, qcBayerInterpAvg4);
	CHECK_ERROR(err);

	err = QCam_SetParam(&m_settings, qprmPostProcessImageFormat, qfmtRgb24);
	CHECK_ERROR(err);

	err = QCam_SendSettingsToCam(m_camera, &m_settings);
	CHECK_ERROR(err);*/


   // set property list
   // -----------------
   
	// NAME
	int nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
	if (DEVICE_OK != nRet)
		return nRet;

	// DESCRIPTION
	
   	// get the QCam version number
	err = QCam_LibVersion(&major, &minor, &build);
	CHECK_ERROR(err);

	sprintf(qcamVersionStr, "QCam %u.%u.%u", major, minor, build);

	nRet = CreateProperty(MM::g_Keyword_Description, qcamVersionStr, MM::String, true);
	if (DEVICE_OK != nRet)
		 return nRet;

	// CAMERA NAME

	// get the camera model string
	err = QCam_GetCameraModelString(m_camera, cameraStr, CAMERA_STRING_LENGTH);
	if (err != qerrSuccess)
	{
		// the function is not supported, so use a lookup table
		err = QCam_GetInfo(m_camera, qinfCameraType, &cameraType);
		CHECK_ERROR(err);

		switch(cameraType)
		{
			case qcCameraRet1300B:
				strcpy(cameraStr, "Retiga 1300B");
				break;

			case qcCameraRet1350B:
				strcpy(cameraStr, "Retiga 1350B");
				break;

			case qcCameraQICamB:
				strcpy(cameraStr, "QICAM");
				break;

			case qcCameraMicroPub:
				strcpy(cameraStr, "Micropublisher");
				break;

			case qcCameraRetIT:
				strcpy(cameraStr, "Retiga Intensified");
				break;

			case qcCameraQICamIR:
				strcpy(cameraStr, "QICAM IR");
				break;

			case qcCameraRet4000R:
				strcpy(cameraStr, "Retiga 4000R");
				break;

			case qcCameraRet2000R:
				strcpy(cameraStr, "Retiga 2000R");
				break;

			case qcCameraRoleraXR:
				strcpy(cameraStr, "Rolera XR");
				break;

			case qcCameraRetigaSRV:
				strcpy(cameraStr, "Retiga SRV");
				break;

			case qcCameraRoleraMGi:
				strcpy(cameraStr, "Rolera MGi");
				break;

			case qcCameraRet4000RV:
				strcpy(cameraStr, "Retiga 4000RV");
				break;

			case qcCameraGo1:
				strcpy(cameraStr, "Go 1");
				break;

			case qcCameraGo3:
				strcpy(cameraStr, "Go 3");
				break;

			case qcCameraGo5:
				strcpy(cameraStr, "Go 5");
				break;

			case qcCameraGo21:
				strcpy(cameraStr, "Go 21");
				break;

	/*		case qcCameraRoleraOne:
				strcpy(cameraStr, "Rolera One");
				break;
            */

			default:
				strcpy(cameraStr, "Unknown");
				break;
		}
	}

	nRet = CreateProperty(MM::g_Keyword_CameraName, cameraStr, MM::String, true);
	assert(nRet == DEVICE_OK);	      

	// CAMERA ID

	// get the camera serial string
	err = QCam_GetSerialString(m_camera, cameraStr, CAMERA_STRING_LENGTH);
	CHECK_ERROR(err);

	// if the string is empty, fill it with "N/A"
	if (strcmp(cameraStr, "") == 0)
	{
		strcpy(cameraStr, "N/A");
	}

	sprintf(cameraIDStr, "Serial number %s", cameraStr);

	nRet = CreateProperty(MM::g_Keyword_CameraID, cameraIDStr, MM::String, true);
	assert(nRet == DEVICE_OK);

	// EXPOSURE
	nRet = SetupExposure();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// BINNING
	nRet = SetupBinning();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// GAIN
	nRet = SetupGain();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// OFFSET
	nRet = SetupOffset();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// READOUT SPEED
	nRet = SetupReadoutSpeed();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// BIT DEPTH
	nRet = SetupBitDepth();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// COOLER
	nRet = SetupCooler();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// REGULATED COOLING
	nRet = SetupRegulatedCooling();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// EM GAIN
	nRet = SetupEMGain();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// IT GAIN
	nRet = SetupITGain();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// setup the two image buffers for queuing
	// frames
	nRet = SetupFrames();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

	// start streaming images
	nRet = StartStreamingImages();
	if (DEVICE_OK != nRet)
	{
		return nRet;
	}

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   // setup the buffer
   // ----------------
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      return nRet;

   m_isInitialized = true;

   // initialize image buffer
   return SnapImage();
}

/**
 * Shuts down (unloads) the device.
 * Required by the MM::Device API.
 * Ideally this method will completely unload the device and release all resources.
 * Shutdown() may be called multiple times in a row.
 * After Shutdown() we should be allowed to call Initialize() again to load the device
 * without causing problems.
 */
int QICamera::Shutdown()
{
	QCam_Err			err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::Shutdown\n");
	#endif

	// check
	if (m_isInitialized == false)
	{
		return DEVICE_OK;
	}

	// abort all frames
	err = QCam_Abort(m_camera);
	CHECK_ERROR(err);
	
	// turn off streaming
	err = QCam_SetStreaming(m_camera, false);
    CHECK_ERROR(err);

	// reset the condition
#ifdef WIN32
	ResetEvent(m_waitCondition);
#endif

	// close the camera
	err = QCam_CloseCamera(m_camera);
	CHECK_ERROR(err);

	// release the driver
	QCam_ReleaseDriver();

	// free up the memory used by the frames
	free(m_frame1->pBuffer);
	free(m_frame2->pBuffer);

	m_isInitialized = false;
	return DEVICE_OK;
}


void QICamera::ConvertReadoutSpeedToString(QCam_qcReadoutSpeed inSpeed, char *outString)
{	
	float				readoutSpeed;


	switch(inSpeed)
	{
		case qcReadout48M:
			readoutSpeed = 48.0;
			break;

		case qcReadout40M:
			readoutSpeed = 40.0;
			break;
	
		case qcReadout24M:
			readoutSpeed = 24.0;
			break;

		case qcReadout20M:
			readoutSpeed = 20.0;
			break;
			
		case qcReadout10M:
			readoutSpeed = 10.0;
			break;
			
		case qcReadout5M:
			readoutSpeed = 5.0;
			break;
			
		case qcReadout2M5:
			readoutSpeed = 2.5;
			break;

		case qcReadout1M:
			readoutSpeed = 1.0;
			break;

		default:
			readoutSpeed = 20.0;
			break;
	}

	sprintf(outString, "%.1f", readoutSpeed);
}


void QICamera::ConvertReadoutSpeedToEnum(const char *inSpeed, QCam_qcReadoutSpeed *outSpeed)
{
	double			value;


	// convert the string to a float
	value = atof(inSpeed);

	if (value == 48.0)
	{
		*outSpeed = qcReadout48M;
	}
	else if (value == 40.0)
	{
		*outSpeed = qcReadout40M;
	}
	else if (value == 24.0)
	{
		*outSpeed = qcReadout24M;
	}
	else if (value == 20.0)
	{
		*outSpeed = qcReadout20M;
	}
	else if (value == 10.0)
	{
		*outSpeed = qcReadout10M;
	}
	else if (value == 5.0)
	{
		*outSpeed = qcReadout5M;
	}
	else if (value == 2.5)
	{
		*outSpeed = qcReadout2M5;
	}
	else if (value == 1.0)
	{
		*outSpeed = qcReadout1M;
	}
	else 
	{
		// default
		*outSpeed = qcReadout20M;
	}
}


int QICamera::SetupFrames()
{
	unsigned long			biggestImageSize;
	unsigned long			ccdWidth, ccdHeight;
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::SetupFrames\n");
	#endif

	// get the ccd width and height
	err = QCam_GetInfo(m_camera, qinfCcdWidth, &ccdWidth);
	CHECK_ERROR(err);
	err = QCam_GetInfo(m_camera, qinfCcdHeight, &ccdHeight);
	CHECK_ERROR(err);

	// calculate the biggest image size possible
	biggestImageSize = QCam_CalcImageSize(qfmtMono16, ccdWidth, ccdHeight);

	// allocate memory for two alternating frames
	m_frame1 = new QCam_Frame;
	m_frame2 = new QCam_Frame;
	
	m_frame1->bufferSize = biggestImageSize;
	m_frame1->pBuffer = malloc(biggestImageSize);
	
	m_frame2->bufferSize = biggestImageSize;
	m_frame2->pBuffer = malloc(biggestImageSize);
	
	// put the camera in software trigger mode
	err = QCam_SetParam(&m_settings, qprmTriggerType, qcTriggerSoftware);
	CHECK_ERROR(err);
	err = QCam_SendSettingsToCam(m_camera, &m_settings);
	CHECK_ERROR(err);

	return DEVICE_OK;
}


int QICamera::StartStreamingImages()
{
	QCam_Err			err;
	
	
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::StartStreamingImages\n");
	#endif
	
	// abort all frames
	err = QCam_Abort(m_camera);
	CHECK_ERROR(err);
	
	// turn on streaming
	err = QCam_SetStreaming(m_camera, true);
    CHECK_ERROR(err);
    
	// start streaming images
	// queue up two images so we always have an image
	// in the queue
    err = QCam_QueueFrame(m_camera,
                           m_frame1,
                           PreviewCallback,
                           qcCallbackDone | qcCallbackExposeDone,
                           this,
                           1);
	CHECK_ERROR(err);
	
	err = QCam_QueueFrame(m_camera,
                           m_frame2,
                           PreviewCallback,
                           qcCallbackDone | qcCallbackExposeDone,
                           this,
                           2);
	CHECK_ERROR(err);

	return DEVICE_OK;
}


void QICamera::SetCurrentFrameNumber(unsigned long inFrameNumber)
{
	QCam_Frame				*currentFrame;
	unsigned short			bytesPerPixel;


	// which frame was received?
	switch(inFrameNumber)
	{
		case 1:
			currentFrame = m_frame1;
			break;

		case 2:
			currentFrame = m_frame2;
			break;

		default:
			assert(false);
			currentFrame = NULL;
			break;
	}

	if (currentFrame != NULL)
	{
		// get the bytes per pixel
		bytesPerPixel = currentFrame->bits / 8;
		if (currentFrame->bits % 8)
		{
			bytesPerPixel ++;
		}

		// convert to an ImgBuffer
		m_snappedImageBuffer.Resize(currentFrame->width, currentFrame->height, bytesPerPixel);
		m_snappedImageBuffer.SetPixels(currentFrame->pBuffer);

		// requeue the frame
		QCam_QueueFrame(m_camera,
						currentFrame,
						PreviewCallback,
						qcCallbackDone | qcCallbackExposeDone,
						this,
						inFrameNumber);
	}
}


void QICamera::ExposureDone()
{
	// we have a new image
#ifdef WIN32
	SetEvent(m_waitCondition);
#elif __APPLE_CC__
	pthread_cond_signal(&m_waitCondition);
#endif
}


int QICamera::SetupExposure()
{
	CPropertyAction				*propertyAction;
	char						tempStr[256];
	unsigned long long			exposure;
	float						exposureAsFloat;
	QCam_Err					err;
	int							nRet;


	// convert the nanosecond exposure time into a millisecond string
	err = QCam_GetParam64(&m_settings, qprm64Exposure, &exposure);
	CHECK_ERROR(err);

	exposureAsFloat = (float)exposure / 1000000.0f;
	sprintf(tempStr, "%f", exposureAsFloat);

	propertyAction = new CPropertyAction (this, &QICamera::OnExposure);
	nRet = CreateProperty(MM::g_Keyword_Exposure, tempStr, MM::Float, false, propertyAction);
	assert(nRet == DEVICE_OK);

	return DEVICE_OK;
}


int QICamera::SetupBinning()
{
	unsigned long				binningTable[32];
	int							binningTableSize = sizeof(binningTable) / sizeof (unsigned long);
	int							counter;
	vector<string>				binValues;
	QCam_Err					err;
	CPropertyAction				*propertyAction;
	int							nRet;
	char						tempStr[256];


	err = QCam_GetParamSparseTable(&m_settings, qprmBinning, binningTable, &binningTableSize);
	
	// if symmetrical binning is not available, try just vertical
	if (err == qerrNotSupported)
	{
		err = QCam_GetParamSparseTable(&m_settings, qprmVerticalBinning, binningTable, &binningTableSize);
		CHECK_ERROR(err);
	}
	
	// go through each binning mode
	for (counter = 0; counter < binningTableSize; counter ++)
	{
		// convert the integer to a string
		sprintf(tempStr, "%lu", binningTable[counter]);

		// add the binning value to the vector
		binValues.push_back(tempStr);
	}

	// create the action 
	propertyAction = new CPropertyAction (this, &QICamera::OnBinning);
	nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, propertyAction);
	assert(nRet == DEVICE_OK);

	// set the allowed values
	nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
	if (nRet != DEVICE_OK)
	{
		return nRet;
	}

	return DEVICE_OK;
}


int QICamera::SetupGain()
{
	CPropertyAction				*propertyAction;
	char						tempStr[256];
	unsigned long				gain, gainMin, gainMax;
	float						gainAsFloat;
	QCam_Err					err;
	int							nRet;


	// convert the normalized gain into a float
	err = QCam_GetParam(&m_settings, qprmNormalizedGain, &gain);
	CHECK_ERROR(err);

	// get the min value
	err = QCam_GetParamMin(&m_settings, qprmNormalizedGain, &gainMin);
	CHECK_ERROR(err);

	// get the max value
	err = QCam_GetParamMax(&m_settings, qprmNormalizedGain, &gainMax);
	CHECK_ERROR(err);

	// setup the gain property
	gainAsFloat = (float)gain / 1000000.0f;
	sprintf(tempStr, "%f", gainAsFloat);

	propertyAction = new CPropertyAction (this, &QICamera::OnGain);
	nRet = CreateProperty(MM::g_Keyword_Gain, tempStr, MM::Float, false, propertyAction);
	assert(nRet == DEVICE_OK);

	// create the min property
	gainAsFloat = (float)gainMin / 1000000.0f;
	sprintf(tempStr, "%f", gainAsFloat);
	nRet = CreateProperty(g_Keyword_Gain_Min, tempStr, MM::Float, true);
	assert(nRet == DEVICE_OK); 

	// create a max property
	gainAsFloat = (float)gainMax / 1000000.0f;
	sprintf(tempStr, "%f", gainAsFloat);
	nRet = CreateProperty(g_Keyword_Gain_Max, tempStr, MM::Float, true);
	assert(nRet == DEVICE_OK); 

	return DEVICE_OK;
}


int QICamera::SetupOffset()
{
	CPropertyAction				*propertyAction;
	char						tempStr[256];
	signed long					offset, offsetMin, offsetMax;
	QCam_Err					err;
	int							nRet;


	// convert the normalized gain into a float
	err = QCam_GetParamS32(&m_settings, qprmS32AbsoluteOffset, &offset);
	CHECK_ERROR(err);

	// get the min/max
	err = QCam_GetParamS32Min(&m_settings, qprmS32AbsoluteOffset, &offsetMin);
	CHECK_ERROR(err);

	err = QCam_GetParamS32Max(&m_settings, qprmS32AbsoluteOffset, &offsetMax);
	CHECK_ERROR(err);

	// create the main property
	sprintf(tempStr, "%ld", offset);
	propertyAction = new CPropertyAction (this, &QICamera::OnOffset);
	nRet = CreateProperty(MM::g_Keyword_Offset, tempStr, MM::Integer, false, propertyAction);
	assert(nRet == DEVICE_OK);

	// create the min property
	sprintf(tempStr, "%ld", offsetMin);
	nRet = CreateProperty(g_Keyword_Offset_Min, tempStr, MM::Integer, true);
	assert(nRet == DEVICE_OK); 

	// create the max property
	sprintf(tempStr, "%ld", offsetMax);
	nRet = CreateProperty(g_Keyword_Offset_Max, tempStr, MM::Integer, true);
	assert(nRet == DEVICE_OK); 

	return DEVICE_OK;
}


int QICamera::SetupReadoutSpeed()
{
	unsigned long				readoutTable[32];
	int							readoutTableSize = sizeof(readoutTable) / sizeof (unsigned long);
	int							counter;
	vector<string>				readoutValues;
	QCam_Err					err;
	CPropertyAction				*propertyAction;
	int							nRet;
	char						tempStr[256];
	char						defaultSpeedStr[256];
	unsigned long				defaultSpeedEnum;


	err = QCam_GetParamSparseTable(&m_settings, qprmReadoutSpeed, readoutTable, &readoutTableSize);
	CHECK_ERROR(err);
	
	// go through each readout speed
	for (counter = 0; counter < readoutTableSize; counter ++)
	{
		// convert the readout speed enum to a string
		ConvertReadoutSpeedToString((QCam_qcReadoutSpeed)readoutTable[counter], tempStr);

		// add the binning value to the vector
		readoutValues.push_back(tempStr);
	}

	// sort the vector since the values may be out of order
	std::sort(readoutValues.begin(), readoutValues.end(), ReadoutSpeedStringSortPredicate);

	// create the default speed string
	err = QCam_GetParam(&m_settings, qprmReadoutSpeed, &defaultSpeedEnum);
	CHECK_ERROR(err);

	ConvertReadoutSpeedToString((QCam_qcReadoutSpeed)defaultSpeedEnum, defaultSpeedStr);

	// create the action 
	propertyAction = new CPropertyAction (this, &QICamera::OnReadoutSpeed);
	nRet = CreateProperty(MM::g_Keyword_ReadoutTime, defaultSpeedStr, MM::Float, false, propertyAction);
	assert(nRet == DEVICE_OK);

	// set the allowed values
	nRet = SetAllowedValues(MM::g_Keyword_ReadoutTime, readoutValues);
	if (nRet != DEVICE_OK)
	{
		return nRet;
	}

	return DEVICE_OK;	
}


int QICamera::SetupBitDepth()
{
	QCam_Err					err;
	unsigned long				maxBitDepth;
	CPropertyAction				*propertyAction;
	int							nRet;
	char						tempStr[256];
	vector<string>				pixelTypeValues;


	// get the max bit depth
	err = QCam_GetInfo(m_camera, qinfBitDepth, &maxBitDepth);
	CHECK_ERROR(err);

	sprintf(tempStr, "%lubit", maxBitDepth);

	// create the action
	propertyAction = new CPropertyAction (this, &QICamera::OnBitDepth);
	nRet = CreateProperty(MM::g_Keyword_PixelType, "8bit", MM::String, false, propertyAction);
	assert(nRet == DEVICE_OK);

	// set the allowed values
	pixelTypeValues.push_back("8bit");
	pixelTypeValues.push_back(tempStr);		// 10, 12, 14 or 16 bit
	nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
	if (nRet != DEVICE_OK)
		return nRet;

	return DEVICE_OK;
}


int QICamera::SetupCooler()
{
	QCam_Err					err;
	unsigned long				isCoolerAvailable;
	CPropertyAction				*propertyAction;
	int							nRet;
	char						tempStr[256];
	vector<string>				coolerValues;


	// check if the camera has a cooler
	err = QCam_GetInfo(m_camera, qinfCooled, &isCoolerAvailable);
	if (err == qerrNotSupported)
	{
		return DEVICE_OK;
	}

	// if so, setup a parameter with On/Off values
	if (isCoolerAvailable == 1)
	{
		// create the property
		propertyAction = new CPropertyAction (this, &QICamera::OnCooler);
		nRet = CreateProperty(g_Keyword_Cooler, "On", MM::String, false, propertyAction);
		assert(nRet == DEVICE_OK);

		strcpy(tempStr, "On");
		coolerValues.push_back(tempStr);

		strcpy(tempStr, "Off");
		coolerValues.push_back(tempStr);

		nRet = SetAllowedValues(g_Keyword_Cooler, coolerValues);
		if (nRet != DEVICE_OK)
		{
			return nRet;
		}
	}

	return DEVICE_OK;
}


int QICamera::SetupRegulatedCooling()
{
	QCam_Err					err;
	unsigned long				isRegulatedCoolingAvailable;
	signed long					minCoolingTemp, maxCoolingTemp;
	CPropertyAction				*propertyAction;
	int							nRet;
	char						minTempStr[256];
	char						maxTempStr[256];


	// check if the camera has a cooler
	err = QCam_GetInfo(m_camera, qinfRegulatedCooling, &isRegulatedCoolingAvailable);
	if (err == qerrNotSupported)
	{
		return DEVICE_OK;
	}

	if (isRegulatedCoolingAvailable == 1)
	{
		// get the min cooling temp available
		err = QCam_GetParamS32Min(&m_settings, qprmS32RegulatedCoolingTemp, &minCoolingTemp);
		CHECK_ERROR(err);

		sprintf(minTempStr, "%ld", minCoolingTemp);

		// get the max cooling temp available
		err = QCam_GetParamS32Max(&m_settings, qprmS32RegulatedCoolingTemp, &maxCoolingTemp);
		CHECK_ERROR(err);

		sprintf(maxTempStr, "%ld", maxCoolingTemp);

		// create the actual temperature property
		propertyAction = new CPropertyAction (this, &QICamera::OnRegulatedCooling);
		nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "0", MM::Integer, false, propertyAction);
		assert(nRet == DEVICE_OK); 
		
		// create a min temperature property
		nRet = CreateProperty(g_Keyword_CCDTemperature_Min, minTempStr, MM::Integer, true);
		assert(nRet == DEVICE_OK); 

		// create a max temperature property
		nRet = CreateProperty(g_Keyword_CCDTemperature_Max, maxTempStr, MM::Integer, true);
		assert(nRet == DEVICE_OK); 
	}

	return DEVICE_OK;
}


int QICamera::SetupEMGain()
{
	CPropertyAction				*propertyAction;
	char						tempStr[256];
	unsigned long				emGain, emGainMin, emGainMax;
	QCam_Err					err;
	int							nRet;
	unsigned long				isEMGainAvailable;					


	// check if the camera supports EM gain
	err = QCam_GetInfo(m_camera, qinfEMGain, &isEMGainAvailable);
	if ((err == qerrNotSupported) || (isEMGainAvailable == 0))
	{
		return DEVICE_OK;
	}

	// get the min value
	err = QCam_GetParamMin(&m_settings, qprmEMGain, &emGainMin);
	CHECK_ERROR(err);

	// get the max value
	err = QCam_GetParamMax(&m_settings, qprmEMGain, &emGainMax);
	CHECK_ERROR(err);

	// convert the em gain into a float
	err = QCam_GetParam(&m_settings, qprmEMGain, &emGain);
	CHECK_ERROR(err);

	sprintf(tempStr, "%lu", emGain);

	propertyAction = new CPropertyAction (this, &QICamera::OnEMGain);
	nRet = CreateProperty(MM::g_Keyword_EMGain, tempStr, MM::Integer, false, propertyAction);
	assert(nRet == DEVICE_OK);

	// create the min property
	sprintf(tempStr, "%lu", emGainMin);
	nRet = CreateProperty(g_Keyword_EMGain_Min, tempStr, MM::Integer, true);
	assert(nRet == DEVICE_OK); 

	// create the max property
	sprintf(tempStr, "%lu", emGainMax);
	nRet = CreateProperty(g_Keyword_EMGain_Max, tempStr, MM::Integer, true);
	assert(nRet == DEVICE_OK); 

	return DEVICE_OK;
}


int QICamera::SetupITGain()
{
	CPropertyAction				*propertyAction;
	char						tempStr[256];
	unsigned long long			itGain, itGainMin,itGainMax;
	QCam_Err					err;
	int							nRet;
	unsigned long				itModel;		
	float						itGainAsFloat;


	// check if the camera supports EM gain
	err = QCam_GetInfo(m_camera, qinfIntensifierModel, &itModel);
	if (err == qerrNotSupported)
	{
		return DEVICE_OK;
	}

	// get the min value
	err = QCam_GetParam64Min(&m_settings, qprm64NormIntensGain, &itGainMin);
	CHECK_ERROR(err);

	// get the max value
	err = QCam_GetParam64Max(&m_settings, qprm64NormIntensGain, &itGainMax);
	CHECK_ERROR(err);

	// convert the intensifier gain into a float
	err = QCam_GetParam64(&m_settings, qprm64NormIntensGain, &itGain);
	CHECK_ERROR(err);

	// setup the intensified gain property
	itGainAsFloat = (float)itGain / 1000000.0f;
	sprintf(tempStr, "%f", itGainAsFloat);

	propertyAction = new CPropertyAction (this, &QICamera::OnITGain);
	nRet = CreateProperty(g_Keyword_ITGain, tempStr, MM::Float, false, propertyAction);
	assert(nRet == DEVICE_OK);

	// create the min property
	itGainAsFloat = (float)itGainMin / 1000000.0f;
	sprintf(tempStr, "%f", itGainAsFloat);
	nRet = CreateProperty(g_Keyword_ITGain_Min, tempStr, MM::Float, true);
	assert(nRet == DEVICE_OK); 

	// create the max property
	itGainAsFloat = (float)itGainMax / 1000000.0f;
	sprintf(tempStr, "%f", itGainAsFloat);
	nRet = CreateProperty(g_Keyword_ITGain_Max, tempStr, MM::Float, true);
	assert(nRet == DEVICE_OK); 

	return DEVICE_OK;
}


/**
 * Performs exposure and grabs a single image.
 * Required by the MM::Camera API.
 */
int QICamera::SnapImage()
{
	unsigned long long		exposure;
	unsigned long long		timeoutMS;
	unsigned long long		timeoutNS;
	QCam_Err				qerr;
	

	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::SnapImage\n");
	#endif

	// get the exposure time in nanoseconds
	qerr = QCam_GetParam64(&m_settings, qprm64Exposure, &exposure);
	CHECK_ERROR(qerr);

	// set the timeout to exposure plus 5 seconds
	// (note: one timeout needs to be in milliseconds)
	timeoutNS = exposure + 5000000000LL;
	timeoutMS = (exposure + 5000000000LL) / 1000000; 

	// trigger a frame
	qerr = QCam_Trigger(m_camera);
	CHECK_ERROR(qerr);

#ifdef WIN32
	DWORD			retVal;

	// wait for the condition or a timeout
	retVal = WaitForSingleObject(m_waitCondition, (DWORD)timeoutMS);

	if(retVal != WAIT_OBJECT_0 )
	{
        return DEVICE_ERR;
	}
#elif __APPLE_CC__
	timespec		timeoutSpec;
	int				err;

	FillInTimespec(&timeoutSpec, timeoutNS);
	
	// wait for the condition or a timeout
	err = pthread_cond_timedwait(&m_waitCondition, &m_waitMutex, &timeoutSpec);
	if (err == ETIMEDOUT)
	{
		return DEVICE_ERR;
	}
	else if (err != 0)
	{
		return DEVICE_ERR;
	}
	
	err = pthread_mutex_unlock(&m_waitMutex);
	if (err != 0)
	{
		return DEVICE_ERR;
	}
#endif

	// if we got here, everything was ok
	return DEVICE_OK;
}

/**
 * Returns pixel data.
 * Required by the MM::Camera API.
 * The calling program will assume the size of the buffer based on the values
 * obtained from GetImageBufferSize(), which in turn should be consistent with
 * values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
 * The calling program allso assumes that camera never changes the size of
 * the pixel buffer on its own. In other words, the buffer can change only if
 * appropriate properties are set (such as binning, pixel type, etc.)
 */
const unsigned char* QICamera::GetImageBuffer()
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::GetImageBuffer\n");
	#endif

	return m_snappedImageBuffer.GetPixels();
}

/**
 * Returns image buffer X-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned QICamera::GetImageWidth() const
{
	QCam_Err			err;
	unsigned long		imageWidth;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::GetImageWidth\n");
	#endif

	// check
	if (m_isInitialized == false)
	{
		return 0;
	}

	// get the image width
	err = QCam_GetInfo(m_camera, qinfImageWidth, &imageWidth);
	CHECK_ERROR(err);

	return imageWidth;
}

/**
 * Returns image buffer Y-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned QICamera::GetImageHeight() const
{
	QCam_Err			err;
	unsigned long		imageHeight;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::GetImageHeight\n");
	#endif

	// check
	if (m_isInitialized == false)
	{
		return 0;
	}

	// get the image width
	err = QCam_GetInfo(m_camera, qinfImageHeight, &imageHeight);
	CHECK_ERROR(err);

	return imageHeight;
}

/**
 * Returns image buffer pixel depth in bytes.
 * Required by the MM::Camera API.
 */
unsigned QICamera::GetImageBytesPerPixel() const
{
	QCam_Err			err;
	unsigned int		bytesPerPixel;
	unsigned long		imageFormat;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::GetImageBytesPerPixel\n");
	#endif

	// check
	if (m_isInitialized == false)
	{
		return 0;
	}

	// get the image format
	err = QCam_GetParam(&m_settings, qprmImageFormat, &imageFormat);
	CHECK_ERROR(err);

	// see which one it is
	if (QCam_is16bit(imageFormat) == false)
	{
	   // 8 bit
	   bytesPerPixel = 1;
	}
	else
	{
	   // 16 bit
	   bytesPerPixel = 2;
	}

	return bytesPerPixel;
} 

/**
 * Returns the bit depth (dynamic range) of the pixel.
 * This does not affect the buffer size, it just gives the client application
 * a guideline on how to interpret pixel values.
 * Required by the MM::Camera API.
 */
unsigned QICamera::GetBitDepth() const
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::GetBitDepth\n");
	#endif

	return 8 * GetImageBytesPerPixel();
}

/**
 * Returns the size in bytes of the image buffer.
 * Required by the MM::Camera API.
 */
long QICamera::GetImageBufferSize() const
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::GetImageBufferSize\n");
	#endif

	return m_snappedImageBuffer.Width() * m_snappedImageBuffer.Height() * GetImageBytesPerPixel();
}

/**
 * Sets the camera Region Of Interest.
 * Required by the MM::Camera API.
 * This command will change the dimensions of the image.
 * Depending on the hardware capabilities the camera may not be able to configure the
 * exact dimensions requested - but should try do as close as possible.
 * If the hardware does not have this capability the software should simulate the ROI by
 * appropriately cropping each frame.
 * @param x - top-left corner coordinate
 * @param y - top-left corner coordinate
 * @param xSize - width
 * @param ySize - height
 */
int QICamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::SetROI\n");
	#endif

	// set the roi
	err = QCam_SetParam(&m_settings, qprmRoiX, x);
	CHECK_ERROR(err);
	err = QCam_SetParam(&m_settings, qprmRoiY, y);
	CHECK_ERROR(err);
	err = QCam_SetParam(&m_settings, qprmRoiWidth, xSize);
	CHECK_ERROR(err);
	err = QCam_SetParam(&m_settings, qprmRoiHeight, ySize);
	CHECK_ERROR(err);

	// commit it
	err = QCam_SendSettingsToCam(m_camera, &m_settings);
	CHECK_ERROR(err);

	if (xSize == 0 && ySize == 0)
	{
		// effectively clear ROI
		ResizeImageBuffer();
	}
	else
	{
		// apply ROI
		m_snappedImageBuffer.Resize(xSize, ySize);
	}	

	return DEVICE_OK;
}

/**
 * Returns the actual dimensions of the current ROI.
 * Required by the MM::Camera API.
 */
int QICamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	QCam_Err			err;
	unsigned long		roiX, roiY, roiWidth, roiHeight;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::GetROI\n");
	#endif

	// get the roi
	err = QCam_GetParam(&m_settings, qprmRoiX, &roiX);
	CHECK_ERROR(err);
	err = QCam_GetParam(&m_settings, qprmRoiY, &roiY);
	CHECK_ERROR(err);
	err = QCam_GetParam(&m_settings, qprmRoiWidth, &roiWidth);
	CHECK_ERROR(err);
	err = QCam_GetParam(&m_settings, qprmRoiHeight, &roiHeight);
	CHECK_ERROR(err);

	// use local varibles of unsigned long type to avoid
	// compiler warnings about casting unsigned int to unsigned long
	x = roiX;
	y = roiY;
	xSize = roiWidth;
	ySize = roiHeight;

	return DEVICE_OK;
}

/**
 * Resets the Region of Interest to full frame.
 * Required by the MM::Camera API.
 */
int QICamera::ClearROI()
{
	QCam_Err			err;
	unsigned long		maxWidth; 
	unsigned long		maxHeight; 


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::ClearROI\n");
	#endif

	// reset the ROI to be full frame 
	err = QCam_GetInfo(m_camera, qinfCcdWidth, &maxWidth); 
	CHECK_ERROR(err);
	err = QCam_GetInfo(m_camera, qinfCcdHeight, &maxHeight); 
	CHECK_ERROR(err);
	 
	err = QCam_SetParam(&m_settings, qprmRoiX, 0); 
	CHECK_ERROR(err);
	err = QCam_SetParam(&m_settings, qprmRoiY, 0); 
	CHECK_ERROR(err);
	err = QCam_SetParam(&m_settings, qprmRoiWidth, maxWidth); 
	CHECK_ERROR(err);
	err = QCam_SetParam(&m_settings, qprmRoiHeight, maxHeight); 
	CHECK_ERROR(err);

	// commit it
	err = QCam_SendSettingsToCam(m_camera, &m_settings);
	CHECK_ERROR(err);

	ResizeImageBuffer();
	return DEVICE_OK;
}

/**
 * Returns the current exposure setting in milliseconds.
 * Required by the MM::Camera API.
 */
double QICamera::GetExposure() const
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::GetExposure\n");
	#endif
	
	// check
	if (m_isInitialized == false)
	{
		return 0.0;
	}

	char buf[MM::MaxStrLength];
	int ret = GetProperty(MM::g_Keyword_Exposure, buf);
	if (ret != DEVICE_OK)
	{
		return 0.0;
	}
	return atof(buf);
}

/**
 * Sets exposure in milliseconds.
 * Required by the MM::Camera API.
 */
void QICamera::SetExposure(double exp)
{
	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::SetExposure\n");
	#endif

	SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

///////////////////////////////////////////////////////////////////////////////
// QICamera Action handlers
///////////////////////////////////////////////////////////////////////////////


int QICamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double					exposure;
	unsigned long long		exposureAsLongLong;
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnExposure\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(exposure);

		// convert the exposure to nanoseconds 64-bit long
		exposureAsLongLong = (unsigned long long) (exposure * 1000000.0f);

		// set it
		err = QCam_SetParam64(&m_settings, qprm64Exposure, exposureAsLongLong);
		CHECK_ERROR(err);

		// commit it
		err = QCam_SendSettingsToCam(m_camera, &m_settings);
		CHECK_ERROR(err);
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParam64(&m_settings, qprm64Exposure, &exposureAsLongLong);
		CHECK_ERROR(err);

		// convert it to milliseconds
		exposure = exposureAsLongLong / 1000000.0;
		pProp->Set(exposure);
   }

   return DEVICE_OK; 
}

int QICamera::GetBinning () const 
{
   char binMode[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_Binning, binMode);
   return atoi(binMode);
}

int QICamera::SetBinning (int binSize) 
{
   ostringstream os;
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());
}


int QICamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long					binningMode;
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnBinning\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(binningMode);

		// set it (try symmetrical binning first)
		err = QCam_SetParam(&m_settings, qprmBinning, binningMode);
		if (err == qerrNotSupported)
		{
			// try just vertical
			err = QCam_SetParam(&m_settings, qprmVerticalBinning, binningMode);
		}
		CHECK_ERROR(err);

		// commit it
		// (the clear roi call will commit the settings)
		ClearROI();

		//err = QCam_SendSettingsToCam(m_camera, &m_settings);
		//CHECK_ERROR(err);

		// reset the buffer size
		ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParam(&m_settings, qprmBinning, (unsigned long*)&binningMode);
		if (err == qerrNotSupported)
		{
			// try just vertical
			err = QCam_GetParam(&m_settings, qprmVerticalBinning, (unsigned long*)&binningMode);
		}
		CHECK_ERROR(err);

		pProp->Set(binningMode);
   }

   return DEVICE_OK; 
}


int QICamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double					gain;
	unsigned long			gainAsLong;
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnGain\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(gain);

		// convert the exposure to the real normalized gain value
		gainAsLong = (unsigned long) (gain * 1000000.0f);

		// set it
		err = QCam_SetParam(&m_settings, qprmNormalizedGain, gainAsLong);
		CHECK_ERROR(err);

		// commit it
		err = QCam_SendSettingsToCam(m_camera, &m_settings);
		CHECK_ERROR(err);
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParam(&m_settings, qprmNormalizedGain, &gainAsLong);
		CHECK_ERROR(err);

		// convert it to a more readable form
		gain = gainAsLong / 1000000.0;
		pProp->Set(gain);
   }

   return DEVICE_OK; 
}


int QICamera::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long					offset;
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnOffset\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(offset);

		// set it
		err = QCam_SetParamS32(&m_settings, qprmS32AbsoluteOffset, offset);
		CHECK_ERROR(err);

		// commit it
		err = QCam_SendSettingsToCam(m_camera, &m_settings);
		CHECK_ERROR(err);
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParamS32(&m_settings, qprmS32AbsoluteOffset, &offset);
		CHECK_ERROR(err);

		pProp->Set(offset);
   }

   return DEVICE_OK; 
}


int QICamera::OnReadoutSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string				readoutSpeedStr;
	char					readoutSpeedChars[256];
	QCam_qcReadoutSpeed		readoutSpeedEnum;
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnReadoutSpeed\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(readoutSpeedStr);

		// convert the string to a readout speed enum
		ConvertReadoutSpeedToEnum(readoutSpeedStr.c_str(), &readoutSpeedEnum);

		// set it
		err = QCam_SetParam(&m_settings, qprmReadoutSpeed, readoutSpeedEnum);
		CHECK_ERROR(err);

		// commit it
		err = QCam_SendSettingsToCam(m_camera, &m_settings);
		CHECK_ERROR(err);
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParam(&m_settings, qprmReadoutSpeed, (unsigned long*)&readoutSpeedEnum);
		CHECK_ERROR(err);

		ConvertReadoutSpeedToString(readoutSpeedEnum, readoutSpeedChars);

		pProp->Set(readoutSpeedChars);
   }

   return DEVICE_OK; 
}


int QICamera::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string				bitDepthString;
	unsigned long			bitDepth, maxBitDepth;
	unsigned long			imageFormat;
	char					tempStr[256];
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnBitDepth\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(bitDepthString);

		// conver the string to a number
		bitDepth = atoi(bitDepthString.c_str());

		// set it
		if (bitDepth == 8)
		{
			// 8 bit
			err = QCam_SetParam(&m_settings, qprmImageFormat, qfmtMono8);
			CHECK_ERROR(err);
		}
		else
		{
			// 16 bit
			err = QCam_SetParam(&m_settings, qprmImageFormat, qfmtMono16);
			CHECK_ERROR(err);
		}

		// commit it
		err = QCam_SendSettingsToCam(m_camera, &m_settings);
		CHECK_ERROR(err);
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParam(&m_settings, qprmImageFormat, &imageFormat);
		CHECK_ERROR(err);

		// see which one it is
		if (imageFormat == qfmtMono8)
		{
			strcpy(tempStr, "8bit");
		}
		else
		{	
			// get the max bit depth
			err = QCam_GetInfo(m_camera, qinfBitDepth, &maxBitDepth);
			CHECK_ERROR(err);

			sprintf(tempStr, "%lubit", maxBitDepth);
		}

		pProp->Set(tempStr);
   }

   return DEVICE_OK; 
}


int QICamera::OnCooler(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string				coolerString;
	QCam_Err				err;
	unsigned long			value;
	char					tempStr[256];


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnCooler\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(coolerString);

		// convert to a number
		if (coolerString.compare("On") == 0)
		{
			value = true;
		}
		else
		{
			value = false;
		}

		// set it
		err = QCam_SetParam(&m_settings, qprmCoolerActive, value);
		CHECK_ERROR(err);

		// commit it
		err = QCam_SendSettingsToCam(m_camera, &m_settings);
		CHECK_ERROR(err);
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParam(&m_settings, qprmCoolerActive, &value);
		CHECK_ERROR(err);

		if (value == 1)
		{
			strcpy(tempStr, "On");
		}
		else
		{
			strcpy(tempStr, "Off");
		}

		pProp->Set(tempStr);
   }

   return DEVICE_OK; 
}


int QICamera::OnRegulatedCooling(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string				tempString;
	QCam_Err				err;
	long					coolingTemp;
	char					tempStr[256];


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnRegulatedCooling\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(tempString);

		// convert to a number
		coolingTemp = atoi(tempString.c_str());

		// set it
		err = QCam_SetParamS32(&m_settings, qprmS32RegulatedCoolingTemp, coolingTemp);
		CHECK_ERROR(err);

		// commit it
		err = QCam_SendSettingsToCam(m_camera, &m_settings);
		CHECK_ERROR(err);
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParamS32(&m_settings, qprmS32RegulatedCoolingTemp, &coolingTemp);
		CHECK_ERROR(err);

		// convert to a string
		sprintf(tempStr, "%ld", coolingTemp);

		pProp->Set(tempStr);
   }

   return DEVICE_OK; 
}


int QICamera::OnEMGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double					emGain;
	unsigned long			emGainAsLong;
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnEMGain\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(emGain);

		// convert the exposure to the real normalized gain value
		emGainAsLong = (unsigned long) (emGain);// * 1000000.0f);

		// set it
		err = QCam_SetParam(&m_settings, qprmEMGain, emGainAsLong);
		CHECK_ERROR(err);

		// commit it
		err = QCam_SendSettingsToCam(m_camera, &m_settings);
		CHECK_ERROR(err);
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParam(&m_settings, qprmEMGain, &emGainAsLong);
		CHECK_ERROR(err);

		// convert it to a more readable form
		emGain = emGainAsLong;// / 1000000.0;
		pProp->Set(emGain);
   }

   return DEVICE_OK; 
}


int QICamera::OnITGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double					itGain;
	unsigned long long		itGainAsU64;
	QCam_Err				err;


	#ifdef PRINT_FUNCTION_NAMES
		printf("QICamera::OnITGain\n");
	#endif

	// see if the user wants to get or set the property
	if (eAct == MM::AfterSet)
	{	
		pProp->Get(itGain);

		// convert the intensifier gain to a uint64
		itGainAsU64 = (unsigned long long) (itGain * 1000000.0f);

		// set it
		err = QCam_SetParam64(&m_settings, qprm64NormIntensGain, itGainAsU64);
		CHECK_ERROR(err);

		// commit it
		err = QCam_SendSettingsToCam(m_camera, &m_settings);
		CHECK_ERROR(err);
   }
   else if (eAct == MM::BeforeGet)
   {
		// get it
		err = QCam_GetParam64(&m_settings, qprm64NormIntensGain, &itGainAsU64);
		CHECK_ERROR(err);

		// convert it a double
		itGain = (double) itGainAsU64 / 1000000.0f;
		pProp->Set(itGain);
   }

   return DEVICE_OK; 
}

///////////////////////////////////////////////////////////////////////////////
// Private QICamera methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sync internal image buffer size to the chosen property values.
 */
int QICamera::ResizeImageBuffer()
{
	unsigned long			imageWidth, imageHeight;
	QCam_Err				err;
	unsigned long			imageFormat;
	int						byteDepth;


	printf("QICamera::ResizeImageBuffer");

	// get the image width and height
	err = QCam_GetInfo(m_camera, qinfImageWidth, &imageWidth);
	CHECK_ERROR(err);

	err = QCam_GetInfo(m_camera, qinfImageHeight, &imageHeight);
	CHECK_ERROR(err);

	// get the image format
	err = QCam_GetParam(&m_settings, qprmImageFormat, &imageFormat);
	CHECK_ERROR(err);

	if (QCam_is16bit(imageFormat) == false)
	{
	   // 8 bit
	   byteDepth = 1;
	}
	else
	{
	   // 16 bit
	   byteDepth = 2;
	}
   
	m_snappedImageBuffer.Resize(imageWidth, imageHeight, byteDepth);

	return DEVICE_OK;
}

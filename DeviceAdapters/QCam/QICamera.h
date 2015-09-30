///////////////////////////////////////////////////////////////////////////////
// FILE:          QICamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Micro-Manager plugin for QImaging cameras using the QCam API.
//                
// AUTHOR:        QImaging, updated by Jeff R. Kuhn, jrkuhn@vt.edu, Oct 2009
//
// COPYRIGHT:     Copyright (C) 2007 Quantitative Imaging Corporation (QImaging).
//
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
//
// CVS:           $Id: QICamera.h,v 1.15 2007/05/30 20:07:18 maustin Exp $
//

#ifndef _QICAMERA_H_
#define _QICAMERA_H_

#include "DeviceBase.h"
#include "ImgBuffer.h"
#include "PvDebayer.h"
#include "ModuleInterface.h"
#include "DeviceThreads.h"
#include "DeviceEvents.h"
#include <string>
#include <map>

#ifdef WIN32
#include <QCamApi.h>
#include <QCamImgfnc.h>
#define UNUSED
#else
#include <QCam/QCamApi.h>
#include <QCam/QCamImgfnc.h>
#define UNUSED __attribute__((__unused__))
#endif

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_NO_CAMERAS_FOUND			    100
#define ERR_CAMERA_ALREADY_OPENED           1006
#define ERR_SOFTWARE_TRIGGER_FAILED         1004
#define ERR_BUSY_ACQUIRING                  1003
#define ERR_NO_CAMERA_FOUND                 1005
#define ERR_POSTPROCESSING_FAILED           1006

//Unused error codes - reserved for future expansion
//#define ERR_INTERNAL_BUFFER_FULL            1004
//#define ERR_BUFFER_ALLOCATION_FAILED        1001
//#define ERR_INCOMPLETE_SNAP_IMAGE_CYCLE     1002

#define SER_NUM_LEN 64 

//////////////////////////////////////////////////////////////////////////////
// Properties
//
#define g_Keyword_Camera                    "Camera"
#define	g_Keyword_Cooler					"Cooler"
#define	g_Keyword_CCDTemperature_Min		"CCDTemperatureMin"
#define	g_Keyword_CCDTemperature_Max		"CCDTemperatureMax"
#define g_Keyword_Exposure_Min				"ExposureMin"
#define g_Keyword_Exposure_Max				"ExposureMax"
#define g_Keyword_Gain_Min					"GainMin"
#define g_Keyword_Gain_Max					"GainMax"
#define g_Keyword_Offset_Min				"OffsetMin"
#define g_Keyword_Offset_Max				"OffsetMax"
#define g_Keyword_EMGain_Min				"EMGainMin"
#define g_Keyword_EMGain_Max				"EMGainMax"
#define g_Keyword_ITGain					"IntensifierGain"
#define g_Keyword_ITGain_Min				"IntensifierGainMin"
#define g_Keyword_ITGain_Max				"IntensifierGainMax"
#define g_Keyword_TriggerType               "TriggerType"
#define g_Keyword_TriggerDelay              "TriggerDelay"
#define g_Keyword_TriggerDelay_Min          "TriggerDelayMin"
#define g_Keyword_TriggerDelay_Max          "TriggerDelayMax"
#define g_Keyword_Color_Mode                "Color"
#define g_Value_ON                            "ON"
#define g_Value_OFF                           "OFF"
#define g_Keyword_RedScale                  "Color - Red scale"
#define g_Keyword_BlueScale                 "Color - Blue scale"
#define g_Keyword_GreenScale                "Color - Green scale"
#define g_Keyword_CFAmask                   "Color - Sensor CFA Pattern"
#define g_Keyword_InterpolationAlgorithm    "Color - zInterpolation algorithm"
#define g_Keyword_Replication               "8-bit: Nearest Neighbor Replication OR >8-bit: Nearest Neighbor Replication"
#define g_Keyword_Bilinear                  "8-bit: Bilinear OR >8-bit: Average 4 pixels"
#define g_Keyword_SmoothHue                 "8-bit: Smooth Hue OR >8-bit: Bicubic Fast"
#define g_Keyword_AdaptiveSmoothHue         "8-bit: Adaptive Smooth Hue (edge detecting) OR >8-bit: Bicubic"
#define g_Keyword_RGGB                      "R-G-G-B"
#define g_Keyword_BGGR                      "B-G-G-R"
#define g_Keyword_GRBG                      "G-R-B-G"
#define g_Keyword_GBRG                      "G-B-R-G"

//////////////////////////////////////////////////////////////////////////////
// Other constants
//

// Number of buffers in image queue - Here for testing purposes
#define QICAMERA_QUEUE_BUFFERS   3

//////////////////////////////////////////////////////////////////////////////
// Global functions
//

// QCam driver Queue callback function. Must be a "C" function.
void QCAMAPI FrameDoneCallback(void* userPtr, unsigned long userData, QCam_Err errcode, unsigned long flags);

// Helper Functions
void ConvertReadoutSpeedToString(QCam_qcReadoutSpeed inSpeed, char *outString);
void ConvertReadoutSpeedToEnum(const char *inSpeed, QCam_qcReadoutSpeed *outSpeed);
void ConvertReadoutPortToString(QCam_qcReadoutPort inPort, char *outString);
void ConvertReadoutPortToEnum(const char *inSpeed, QCam_qcReadoutPort *outPort);
void ConvertTriggerTypeToString(QCam_qcTriggerType inType, char *outString);
void ConvertTriggerTypeToEnum(const char *inType, QCam_qcTriggerType *outType);


//////////////////////////////////////////////////////////////////////////////
// QIDriver class
//
// Reference-counted access wrapping QCam_LoadDriver and QCam_ReleaseDriver

class QIDriver
{
public:
    // Return display strings for available cameras (empty on any error)
    static std::vector<std::string> AvailableCameras();
    // Get the uniqueId back from a display string
    static unsigned long UniqueIdForCamera(const std::string& displayString);

    // RAII class for access
    class Access
    {
    public:
        Access();
        ~Access();
        operator bool() { return m_error == qerrSuccess; }
        QCam_Err Status() { return m_error; }
    private:
        QCam_Err m_error;
    };

private:
    friend class QIDriver::Access;
    static unsigned s_usageCount;
};


//////////////////////////////////////////////////////////////////////////////
// QICamera class
//////////////////////////////////////////////////////////////////////////////
class QICamera : public CCameraBase<QICamera>  
{
public:
    QICamera();
    ~QICamera();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* name) const;      
    virtual bool Busy();

    // MMCamera API
    // ------------
    int SnapImage();
    int Trigger();  // for software triggering
    const unsigned char* GetImageBuffer();
    unsigned GetImageWidth() const;
    unsigned GetImageHeight() const;
    unsigned GetImageBytesPerPixel() const;
    unsigned GetBitDepth() const;
    int GetBinning() const;
    int SetBinning(int binSize);
    int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
    long GetImageBufferSize() const;
    double GetExposure() const;
    void SetExposure(double exp);
    int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
    int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
    int ClearROI();
    int SetEasyEMGain(unsigned long easyGain);
    // Sequence acquisition interface
    int StartSequenceAcquisition(double interval_ms);
    int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
    int StopSequenceAcquisition();
    int RestartSequenceAcquisition();
    bool IsCapturing() { return m_sthd->IsRunning(); };
    unsigned GetNumberOfComponents() const;
    int GetComponentName(unsigned comp, char* name);

    // action interface
    // ----------------
    int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnReadoutSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCooler(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnRegulatedCooling(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnEMGain(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnITGain(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnEasyEMGain(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
    int OnTriggerType(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnTriggerDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnInterpolationAlgorithm(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnRedScale(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnGreenScale(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnBlueScale(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnCFAmask(MM::PropertyBase* pProp, MM::ActionType eAct);
    int LogError(std::string message, int err, char* file=NULL, int line=0) const;

private:
    //////////////////////////////////////////////////////////////////////////
    // Thread interface
    class QISequenceThread : public MMDeviceThreadBase {
    public:
        QISequenceThread(QICamera* pCam) : m_stop(false), m_numImages(0), 
            m_captureCount(0), m_pCam(pCam), m_isRunning(false) { }

        ~QISequenceThread() {}

        int svc(void);

        void Stop() { m_stop = true; }

        void Start() 
        { 
            m_isRunning = true; 
            m_stop = false; 
            activate(); 
        }

        bool IsRunning() { return m_isRunning; }

        void SetLength(long images) { m_numImages = images; }

        long GetLength(void) { return m_numImages; }

        long GetRemaining(void) { return m_numImages - m_captureCount; }

    private:
        bool                m_stop;         // thread stop requested
        long                m_numImages;    // total number of images to capture
        long                m_captureCount; // current count of images
        QICamera*           m_pCam;         // parent camera
        bool                m_isRunning;    // is the thread running?
    }; // class QISequenceThread

    // Setup
    int SetupExposure();
    int SetupBinning();
    int SetupGain();
    int SetupOffset();
    int SetupReadoutSpeed();
    int SetupReadoutPort();
    int SetupBitDepth();
    int SetupCooler();
    int SetupRegulatedCooling();
    int SetupEMGain();
    int SetupITGain();
    int SetupEMAndEasyEMGain();
    int SetupFrames();
    int SetupTriggerType();
    int SetupTriggerDelay();

    // helper functions
    friend void QCAMAPI FrameDoneCallback(void*, unsigned long, QCam_Err, unsigned long);
    int ResizeImageBuffer();
    int InsertImage(int iFrameBuff);
    int QueueFrame(int iFrameBuff);
    void FrameDone(long frameNumber, QCam_Err errcode);
#ifdef QCAM_EXPOSURE_DONE_REQUIRED
    void ExposureDone(long frameNumber, QCam_Err errcode);
#endif
    int SendSettingsToCamera(QCam_Settings* settings);

    std::auto_ptr<QIDriver::Access> m_driverAccess;

    bool				m_isInitialized;    // Has the camera been initialized (setup)?
    QCam_Handle			m_camera;           // handle to the camera. Used by all QCam_* functions
//    QCam_Settings		m_settings;         // Current settings. Used internally by QCam_* functions
    void *	            m_settings;         // cast to void so we can use older and newer versions of QCam, is cast depending on version
    int                 m_nDriverBuild;     // Current qcam driver build
    QISequenceThread*   m_sthd;             // Pointer to the sequencing thread
    bool                m_softwareTrigger;  // Is the camera in software triggering mode
    bool                m_rgbColor;         // is the camera in the color (debayer) mode
    double              m_dExposure;        // Current exposure setting
    double              m_interval;         // Current sequence capture interval
    unsigned int        m_imageWidth;       // Current capture width
    unsigned int        m_imageHeight;      // Current capture height
    unsigned int        m_bitDepth;         // Current image depth in bits (8bit, 10bit, 12bit, etc..)
    unsigned int        m_maxBitDepth;      // Maximum possible bit depth for camera
    ImgBuffer           m_colorBuffer;      // buffer for color images (if available)
    Debayer             m_debayer;          // debayer processor to convert from b&w to color

    // Frame Buffer for continuous acquisition
    static const int    m_nFrameBuffs = QICAMERA_QUEUE_BUFFERS; // Total frame buffers in the circular queue
    QCam_Frame**        m_frameBuffs;       // Circular queue of individual frames
    bool*               m_frameBuffsAvail;  // TRUE: Buffer can safely be requeued. FALSE: MMCore is accessing the buffer.
    MMEvent             m_frameDoneEvent;   // Signals the sequence thread when a frame was captured
    int		            m_frameDoneBuff;    // Tells the sequence thread which frame was just captured
    MMThreadLock        m_frameDoneLock;    // Locks access to m_frameDoneBuff
    QCam_Frame*         m_processedFrame;   
    
    double           m_redScale;
    double           m_greenScale;
    double           m_blueScale;
   
    int              m_selectedCFAmask;
    int              m_selectedInterpolationAlgorithm;
};

#endif //_QICAMERA_H_                                               

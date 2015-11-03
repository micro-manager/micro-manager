#ifndef __ABSCAMERA_H__
#define __ABSCAMERA_H__

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>   
   #define snprintf _snprintf 
   #pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#endif

#include "MMDevice.h"
#include "MMDeviceConstants.h"
#include "ModuleInterface.h"
#include "DeviceBase.h"
#include "ImgBuffer.h"
#include "DeviceThreads.h"
#include <string>
#include <map>
#include <vector>
#include <algorithm>
using namespace std;

// ---------------------------Camera - API ------------------------------------
#include "camusb_api.h"                       // ABS Camera API
#include "camusb_api_util.h"                  // ABS Camera API utilities
#include "camusb_api_ext.h"                   // ABS Camera API extended
#include "camusb_api_profiles.h"              // ABS Camera API profiles
#include "safeutil.h"                         // macros to delete release pointers safely
#include "ABSDelayLoadDll.h"                  // add support for Delay loading of an dll
#include "ccmfilestd.h"                       // load / save color correction profiles
#include "AbsImgBuffer.h"                     // modified ImgBuffer class

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE          102
#define ERR_UNKNOWN_POSITION      103
#define ERR_IN_SEQUENCE           104
#define ERR_SEQUENCE_INACTIVE     105
#define SIMULATED_ERROR           200
#define ERR_CAMERA_API_BASE       200

//! camera standard resolution item for #CStandardResolutionList
class CStandardResolutionItem
{
public:
  CStandardResolutionItem()
  { qwID = STDRES2_NONE; }
  CStandardResolutionItem( std::string name, u64 id	)
  { strName = name; qwID = id; }

  std::string strName;      // standard resolution name
  u64         qwID;         // standard resolution ID
};

//! color correction item for #CColorCorrectionList
class CColorCorrectionItem 
{
public:
  CColorCorrectionItem( const std::string & name = "" )
  { strName = name;
    memset( &sCCP, 0, sizeof(sCCP) ); }

  CColorCorrectionItem( const std::string & name, const S_CCM & ccm )
  { 
    strName         = name;
    sCCP.bActive    = 1;
    sCCP.bSetMatrix = 1;
    CCCMFile::f32Toi16( (f32*)ccm.fCCM, (i16*)sCCP.wCCMatrix);
  }

  std::string strName;		        // color correction name
  S_COLOR_CORRECTION_PARAMS sCCP; // color correction data
};

//! type defines
typedef std::vector<S_CAMERA_LIST_EX>         CCameraList;              // camera list
typedef std::vector<CStandardResolutionItem>  CStandardResolutionList;  // camera standard resolution to ID list
typedef std::vector<CColorCorrectionItem>     CColorCorrectionList;     // color correction item
typedef std::map<std::string, u16>            CIOPortNameToIndexMap;    // io-port map

typedef std::vector<std::string>                  CStringVector;            // property names to handle transpose functions

//! abs camera class
class CABSCamera : public CCameraBase<CABSCamera>  
{
friend class  CABSCameraSequenceThread;

public:
  CABSCamera();
  ~CABSCamera();

  // MMDevice API
  // ------------
  int   Initialize();
  int   Shutdown();
    
  void  GetName(char* name) const;      
    
  // MMCamera API
  // ------------
  int   SnapImage();
  const unsigned char* GetImageBuffer();
  unsigned GetImageWidth() const;
  unsigned GetImageHeight() const;
  unsigned GetImageBytesPerPixel() const;
  unsigned GetBitDepth() const;
  long  GetImageBufferSize() const;
  double GetExposure() const;
  void  SetExposure(double exp);
  int   SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
  int   GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
  int   ClearROI();

  // MMCamera API - sequence acqusition
  // ----------------------------------
  virtual int StartSequenceAcquisition( long numImages, double interval_ms, bool stopOnOverflow );
  virtual int StopSequenceAcquisition();
  int   InsertImage();
  virtual int ThreadRun (void);

  double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
  double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
  int   GetBinning() const;
  int   SetBinning(int bS);

  int   IsExposureSequenceable(bool& isSequenceable) const 
        { isSequenceable = false; return DEVICE_OK; }

  unsigned  GetNumberOfComponents() const;

  static bool isApiDllAvailable( void );


  int SetProperty(const char* name, const char* value);

  // action interface
  // ----------------
  // floating point read-only properties for testing
  int   OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long);
  int   OnBinning     (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnPixelType   (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnBitDepth    (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnReadoutTime (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnReadoutMode (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnDropPixels  (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnExposure    (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnFramerate   (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnTemperature (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnGain        (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnGainRed     (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnGainGreen   (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnGainGreen1  (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnGainGreen2  (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnGainBlue    (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnGainExtra   (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnSharpen     (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnBlackLevel  (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnProfileLoad (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnProfileSave (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnGamma       (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnHue         (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnSaturation  (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnExposureLongTime(MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnColorCorrection (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnBayerDemosaic   (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnStdResolution   (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnErrorSimulation (MM::PropertyBase* , MM::ActionType );
  int   OnTriggerDevice   (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnSaturatePixels  (MM::PropertyBase* pProp, MM::ActionType eAct);
  // io-ports
  int   OnTriggerInport   (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnTriggerPolarity (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnTriggerDelay    (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnStrobeOutport   (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnStrobePolarity  (MM::PropertyBase* pProp, MM::ActionType eAct);

  int   OnCameraSelection (MM::PropertyBase* pProp, MM::ActionType eAct);

  int   OnAutoExposureOptions     (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnShadingCorrection       (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnShadingCorrectionSetup  (MM::PropertyBase* pProp, MM::ActionType eAct);
  int   OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct);

  string buildCameraDeviceID( unsigned long serialNumber, const string & deviceName );
  string cameraDeviceID( void ) const;
  void  setCameraDeviceID( string cameraDeviceID );

  //! overwrite the current capture mode at live mode for a faster readout
  void  setLiveReadoutMode( bool bOverwriteDefault = false );
  int   GetPropertyString( const char* name, std::string & value );

protected:
  double  getFramerate() const;
  void    setFramerate( double fps );

  string deviceName() const;
  string profileDir() const;
  string ccmDir() const;
  string profilePath( const char * szProfileName );
  
  bool  buildStandardResolutionList( );
  void  fillStandardResolutionList( );
  void  fillProfileList( );
  void  fillCCMList( );
  void  fillIOPorts( );
  
  void  updateShadingCorrectionState( );
  u32   updateCameraTransposeCorrection( );
  int   checkForModifiedCameraParameter();

  u32   loadProfile( const char * szProfileName, const char * szSettingsName );
  u32   saveProfile( const char * szProfileName, const char * szSettingsName );
  
  string getStandardResolutionName( const u64 stdResID ) const;
  
  string findNameById( const u64  & qwID );
  u64   findIdByName( const std::string  & stdResName );

  void  updateIOPorts();
  string ioPortNameByIndex( u32 ioPortIndex );

  void  setAllowedFramerates( );
  void  setAllowedHueSaturation( );
  void  setAllowedGamma( );

  bool  isInitialized( void ) const;
  void  setInitialized( const bool bInitialized );
  bool  isColor( void ) const;
  void  setColor( const bool colorCamera );
  void  getAvailableCameras( CCameraList & cCameraList );
  void  setDeviceNo( int deviceNo );
  u08   deviceNo( void ) const;

  int convertApiErrorCode( unsigned long errorNumber, const char* functionName = 0 );	

  unsigned long getCameraImage( CAbsImgBuffer& img );
  unsigned long getCap( unsigned __int64 functionId, void* & capability );
  int   setAllowedPixelTypes( void );
  int   setAllowedBitDepth( void );
  int   setAllowedExpsoure( void );
  int   setAllowedGainExtra( void );
  int   setAllowedGain( const char* szKeyword_GainChannel );
  int   OnGainCommon          (const char* szKeyword_GainChannel, MM::PropertyBase* pProp, MM::ActionType eAct );
  int   OnHueSaturationCommon (const char* propName, MM::PropertyBase* pProp, MM::ActionType eAct );
  int   OnStrobeCommon        (const char* propName, MM::PropertyBase* pProp, MM::ActionType eAct );
  int   OnTriggerCommon       (const char* propName, MM::PropertyBase* pProp, MM::ActionType eAct );

  void  initTransposeFunctions( bool bInitialize );
  
private:
  int   apiToMMErrorCode( unsigned long apiErrorNumber ) const;
  int   mmToApiErrorCode( unsigned long mmErrorNumber ) const;
  string getFramerateString( void ) const;
  string getDeviceNameString( void ) const;
  string getDeviceNameString( const S_CAMERA_VERSION & sCamVer ) const;

  string                    cameraDeviceID_;
  int                       deviceNo_;
  bool                      colorCamera_;
  string                    last16BitDepth_;
  double                    temperatureUnit_;
  int                       temperatureIndex_;
  unsigned long long        qwSupportedFunctions_;
  const char*               m_BayerDemosaicMode;
  bool                      bMirrorX_;
  bool                      bMirrorY_;
  bool                      bSwapXY_;
  std::string               strProfile_;        // last used profile name	
  std::vector<std::string>  strProfileLst_;     // list of available profiles
  CStandardResolutionList   stdResolutionLst_;  // list of supported standard resolutions with IDs
  CColorCorrectionList      colCorLst_;         // list of supported color corrections
  CIOPortNameToIndexMap     ioPortMap_;         // map of all io-port names to theire port index

  std::string               triggerPortName_;
  std::string               triggerPortPolarity_;
  long                      triggerPortDelay_;
  std::string               strobePortName_;
  std::string               strobePortPolarity_;
  S_CAMERA_VERSION          cameraVersion_;

  CStringVector             transposePropertyNames_;

private:
  static const double nominalPixelSizeUm_;

  bool  isSupported( unsigned long long qwFunctionID );
  int   SetAllowedBinning();
  void  generateEmptyImage( CAbsImgBuffer& img );
  int   ResizeImageBuffer();

  double        dPhase_;
  CAbsImgBuffer img_;  
  double        framerate_;

  bool          initialized_;
  double        readoutUs_;
  MM::MMTime    readoutStartTime_;
  string        readoutMode_;
  int           bitDepth_;
  unsigned      roiX_;
  unsigned      roiY_;
  MM::MMTime    sequenceStartTime_;
  long          imageCounter_;
  long          binSize_;
  long          cameraCCDXSize_;
  long          cameraCCDYSize_;
  std::string   triggerDevice_;

  bool          dropPixels_;
  bool          saturatePixels_;
  double        fractionOfPixelsToDropOrSaturate_;

  double        testProperty_[10];
  int           nComponents_;

  string        exposureLongTime_;

  bool          stopOnOverflow_;
  long          numImages_;
  double        interval_ms_;
  volatile bool abortGetImageFired_;
  volatile bool bFirstExposureLongTimeImage_;

  //CABSCameraSequenceThread* thread_;
 // CABSCameraSequenceThread* thd_;
};
/*
// image aquisition thread class
class CABSCameraSequenceThread : public CCameraBase<CABSCamera>::BaseSequenceThread
{
  friend class CABSCamera;
  enum { default_numImages=1, default_intervalMS = 100 };

public:
  CABSCameraSequenceThread( CCameraBase<CABSCamera> * pCam );
  ~CABSCameraSequenceThread();

private:
  //! thread main function => acquire images and put 
  //! them into the circular buffer
  int svc(void) throw();
}; 
*/
#endif // __ABSCAMERA_H__

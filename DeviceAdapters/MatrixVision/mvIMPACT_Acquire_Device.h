//-----------------------------------------------------------------------------
#ifndef MVIMPACT_ACQUIRE_DEVICE_H
#define MVIMPACT_ACQUIRE_DEVICE_H MVIMPACT_ACQUIRE_DEVICE_H
//-----------------------------------------------------------------------------
///////////////////////////////////////////////////////////////////////////////
// FILE:          mvIMPACT_Acquire_Device.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   MATRIX VISION mvIMPACT Acquire camera module.
//
// AUTHOR:        Stefan Battmer
// COPYRIGHT:     Stefan Battmer MATRIX VISION GmbH, Oppenweiler, 2016
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

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <map>
#include <mvIMPACT_CPP/mvIMPACT_acquire.h>
#include <mvIMPACT_CPP/mvIMPACT_acquire_GenICam.h>
#include <set>
#include <string>
#include <vector>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_LOW_LEVEL_MODE_FAILED    10007
#define ERR_INVALID_MODE             10008

typedef std::map<std::string, mvIMPACT::acquire::Component> DeviceFeatureContainer;
typedef std::set<std::string> StringSet;

//-----------------------------------------------------------------------------
class mvIMPACT_Acquire_Device : public CCameraBase<mvIMPACT_Acquire_Device>
//-----------------------------------------------------------------------------
{
public:
   explicit mvIMPACT_Acquire_Device( mvIMPACT::acquire::Device* pDev );
   virtual ~mvIMPACT_Acquire_Device();

   // Device API
   // ---------
   virtual unsigned GetBitDepth( void ) const;
   virtual const unsigned char* GetImageBuffer( void );
   virtual long GetImageBufferSize( void ) const;
   virtual unsigned GetImageBytesPerPixel( void ) const;
   virtual unsigned GetImageHeight( void ) const;
   virtual unsigned GetImageWidth( void ) const;
   virtual void GetName( char* pszName ) const
   {
      CDeviceUtils::CopyLimitedString( pszName, pDev_->serial.read().c_str() );
   }
   virtual unsigned GetNumberOfComponents( void ) const;
   virtual int Initialize( void );
   virtual int Shutdown( void );
   virtual int SnapImage( void );

   virtual double GetExposure( void ) const;
   virtual void SetExposure( double exp );

   virtual int GetROI( unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize );
   virtual int SetROI( unsigned x, unsigned y, unsigned xSize, unsigned ySize );
   virtual int ClearROI( void );

   virtual int IsExposureSequenceable( bool& seq ) const
   {
      seq = false;
      return DEVICE_OK;
   }
   virtual int GetBinning( void ) const
   {
      char buf[MM::MaxStrLength];
      const int ret = GetProperty( MM::g_Keyword_Binning, buf );
      return ( ret == DEVICE_OK ) ? atoi( buf ) : 1;
   }
   virtual int SetBinning( int binSize )
   {
      return SetProperty( MM::g_Keyword_Binning, CDeviceUtils::ConvertToString( binSize ) );
   }

   virtual int PrepareSequenceAcqusition( void )
   {
      return DEVICE_OK;
   }
   virtual int StartSequenceAcquisition( double interval )
   {
      return StartSequenceAcquisition( LONG_MAX, interval, false );
   }
   virtual int StartSequenceAcquisition( long numImages, double interval_ms, bool stopOnOverflow );
   virtual int StopSequenceAcquisition( void );
   virtual bool IsCapturing( void );

private:
   void ClearRequestQueue( void );
   template<typename _Ty, typename _TValMM, typename _TValIA>
   int CreateIntPropertyFromDriverFeature( mvIMPACT::acquire::ComponentIterator it, const std::string& fullName, const bool boReadOnly );
   Request* GetCurrentRequest( void ) const
   {
      return ( pCurrentRequest_ && pCurrentRequest_->isOK() ) ? pCurrentRequest_ : pCurrentRequestBufferLayout_;
   }
   int InsertImage( void );
   int OnBinning( MM::PropertyBase* pProp, MM::ActionType eAct );
   int OnPixelType( MM::PropertyBase* pProp, MM::ActionType eAct );
   int OnPropertyChanged( MM::PropertyBase* pProp, MM::ActionType eAct );
   void PopulateMap( DeviceFeatureContainer& map, mvIMPACT::acquire::ComponentIterator it, const std::string& currentPath, int searchMode = 0 );
   void RefreshCaptureBufferLayout( void );
   int RunSequenceOnThread( MM::MMTime startTime );

   // binning
   std::vector<int64_type> GetSupportedBinningValues_GenICam( PropertyI64 prop ) const;
   int SetUpBinningProperties_DeviceSpecific( void );
   int SetUpBinningProperties_GenICam( void );
   void SetBinningProperty( Property prop, const std::string& value, int& ret, bool& boMustRefreshCaptureBufferLayout );

   // common interface objects
   mvIMPACT::acquire::Device* pDev_;
   mvIMPACT::acquire::FunctionInterface* pFI_;
   Request* pCurrentRequest_;
   Request* pCurrentRequestBufferLayout_;
   mvIMPACT::acquire::ImageRequestControl* pIRC_;
   mvIMPACT::acquire::ImageDestination* pID_;
   // device specific interface layout
   mvIMPACT::acquire::CameraSettingsBlueDevice* pCS_;
   static const double s_exposureTimeConvertFactor_; // mvIMPACT Acquire uses 'us' while micro-manager uses 'ms'
   // GenICam interface layout
   mvIMPACT::acquire::GenICam::ImageFormatControl* pIFC_;
   mvIMPACT::acquire::GenICam::AcquisitionControl* pAC_;

   DeviceFeatureContainer features_;
   StringSet featuresToIgnore_;

   MM::MMTime sequenceStartTime_;
   bool stopOnOverflow_;
   MM::MMTime readoutStartTime_;

   friend class MySequenceThread;
   MySequenceThread* pSequenceThread_;
};

//-----------------------------------------------------------------------------
class MySequenceThread : public MMDeviceThreadBase
//-----------------------------------------------------------------------------
{
   friend class mvIMPACT_Acquire_Device;
   enum
   {
      default_numImages = 1,
      default_intervalMS = 100
   };
public:
   explicit MySequenceThread( mvIMPACT_Acquire_Device* pmvIMPACT_Acquire_Device );
   virtual ~MySequenceThread() {}
   void Stop( void );
   void Start( long numImages, double intervalMs );
   bool IsStopped( void );
   void Suspend( void );
   bool IsSuspended( void );
   void Resume( void );
   double GetIntervalMs( void )
   {
      return intervalMs_;
   }
   void SetLength( long images )
   {
      numImages_ = images;
   }
   long GetLength( void ) const
   {
      return numImages_;
   }
   long GetImageCounter( void )
   {
      return imageCounter_;
   }
   MM::MMTime GetStartTime( void )
   {
      return startTime_;
   }
   MM::MMTime GetActualDuration( void )
   {
      return actualDuration_;
   }
private:
   virtual int svc( void ) throw();

   double intervalMs_;
   long numImages_;
   long imageCounter_;
   bool stop_;
   bool suspend_;
   mvIMPACT_Acquire_Device* pmvIMPACT_Acquire_Device_;
   MM::MMTime startTime_;
   MM::MMTime actualDuration_;
   MM::MMTime lastFrameTime_;
   MMThreadLock stopLock_;
   MMThreadLock suspendLock_;
};

#endif // MVIMPACT_ACQUIRE_DEVICE_H

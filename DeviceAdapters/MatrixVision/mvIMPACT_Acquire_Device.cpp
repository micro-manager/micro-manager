///////////////////////////////////////////////////////////////////////////////
// FILE:          mvIMPACT_Acquire_Device.cpp
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

#include <algorithm>
#include <cassert>
#include <limits>
#include "mvIMPACT_Acquire_Device.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

#undef min // otherwise we can't work with the 'numeric_limits' template here as Windows defines a macro 'min'
#undef max // otherwise we can't work with the 'numeric_limits' template here as Windows defines a macro 'max'

using namespace mvIMPACT::acquire;
using namespace std;

class mvIMPACT_Acquire_Device;

//=============================================================================
//=========================== internal macros =================================
//=============================================================================
#define LOG_MVIMPACT_ACQUIRE_EXCEPTION(EXCEPTION) \
   { \
      ostringstream oss; \
      oss << string( __FUNCTION__ ) << "(" << __LINE__ << "): " << EXCEPTION.what() << "(" << EXCEPTION.getErrorCodeAsString() << ")."; \
      LogMessage( oss.str() ); \
   }

#define LOGGED_MVIMPACT_ACQUIRE_CALL(FUNC, PARAMS) \
   { \
      const int mvIAResult = FUNC PARAMS; \
      if( mvIAResult != DMR_NO_ERROR ) \
      { \
         ostringstream oss; \
         oss << "Calling '" << string( #FUNC ) << string( #PARAMS ) << " failed(Result: " << mvIAResult << "(" << ImpactAcquireException::getErrorCodeAsString( mvIAResult ) << "))."; \
         LogMessage( oss.str() ); \
         result = DEVICE_ERR; \
      } \
   }

#define MICRO_MANAGER_INTERFACE_PROLOGUE \
   int result = DEVICE_OK; \
   try \
   {

#define MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT \
   try \
   {

#define MICRO_MANAGER_INTERFACE_EPILOGUE \
   } \
   catch( const ImpactAcquireException& e ) \
   { \
      LOG_MVIMPACT_ACQUIRE_EXCEPTION( e ) \
      result = DEVICE_ERR; \
   } \
   return result;

#define MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT \
   } \
   catch( const ImpactAcquireException& e ) \
   { \
      LOG_MVIMPACT_ACQUIRE_EXCEPTION( e ) \
   }

//=============================================================================
//=========================== static variables ================================
//=============================================================================
static DeviceManager* s_pDevMgr = 0;

// constants for naming pixel types (allowed values of the "PixelType" property)
static const char* s_PixelType_8bit = "8bit";
static const char* s_PixelType_16bit = "16bit";
static const char* s_PixelType_32bitRGB = "32bitRGB";
//static const char* s_PixelType_64bitRGB = "64bitRGB";

//=============================================================================
//=================== internal functions ======================================
//=============================================================================
//-----------------------------------------------------------------------------
template<class _Ty>
void DeleteElement( _Ty& data )
//-----------------------------------------------------------------------------
{
   delete data;
   data = 0;
}

//-----------------------------------------------------------------------------
static void UpdateDeviceList( void )
//-----------------------------------------------------------------------------
{
   if( !s_pDevMgr )
   {
      s_pDevMgr = new DeviceManager();
   }

   const unsigned int devCnt = s_pDevMgr->deviceCount();
   for( unsigned int i = 0; i < devCnt; i++ )
   {
      RegisterDevice( s_pDevMgr->getDevice( i )->serial.readS().c_str(), MM::CameraDevice, s_pDevMgr->getDevice( i )->product.readS().c_str() );
   }
}

//-----------------------------------------------------------------------------
template<typename _Ty, typename _TValMM, typename _TValIA>
void SetPropVal_AfterSet( MM::PropertyBase* pProp, HOBJ hObj )
//-----------------------------------------------------------------------------
{
   _Ty prop( hObj );
   if( prop.hasDict() )
   {
      string value;
      pProp->Get( value );
      prop.writeS( value );
   }
   else
   {
      _TValMM value = 0;
      pProp->Get( value );
      const _TValIA MIN_VAL = ( prop.hasMinValue() ) ? prop.getMinValue() : ( ( prop.type() == ctPropFloat ) ? -1 * numeric_limits<_TValIA>::max() : numeric_limits<_TValIA>::min() ); // DBL_MIN is NOT what you'd expect it to be!
      const _TValIA MAX_VAL = ( prop.hasMaxValue() ) ? prop.getMaxValue() : numeric_limits<_TValIA>::max();
      prop.write( ( ( static_cast<_TValIA>( value ) < MIN_VAL ) ? MIN_VAL : ( ( static_cast<_TValIA>( value ) > MAX_VAL ) ? MAX_VAL : static_cast<_TValIA>( value ) ) ) );
   }
}

//-----------------------------------------------------------------------------
template<typename _Ty, typename _TValMM>
void SetPropVal_BeforeGet( MM::PropertyBase* pProp, HOBJ hObj )
//-----------------------------------------------------------------------------
{
   _Ty prop( hObj );
   if( prop.hasDict() )
   {
      pProp->Set( prop.readS().c_str() );
   }
   else
   {
      pProp->Set( static_cast<_TValMM>( prop.read() ) );
   }
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
//-----------------------------------------------------------------------------
MODULE_API void InitializeModuleData( void )
//-----------------------------------------------------------------------------
{
   UpdateDeviceList();
}

//-----------------------------------------------------------------------------
MODULE_API MM::Device* CreateDevice( const char* pDeviceName )
//-----------------------------------------------------------------------------
{
   if( pDeviceName )
   {
      const string serial( pDeviceName );
      Device* pDev = s_pDevMgr->getDeviceBySerial( serial );
      if( !pDev )
      {
         UpdateDeviceList();
         pDev = s_pDevMgr->getDeviceBySerial( serial );
      }

      if( pDev )
      {
         return new mvIMPACT_Acquire_Device( pDev );
      }
   }
   return 0;
}

//-----------------------------------------------------------------------------
MODULE_API void DeleteDevice( MM::Device* pDevice )
//-----------------------------------------------------------------------------
{
   DeleteElement( pDevice );
}

//=============================================================================
//==================== Implementation mvIMPACT_Acquire_Device =================
//=============================================================================
const double mvIMPACT_Acquire_Device::s_exposureTimeConvertFactor_ = 1000.;

//-----------------------------------------------------------------------------
mvIMPACT_Acquire_Device::mvIMPACT_Acquire_Device( mvIMPACT::acquire::Device* pDev ) : pDev_( pDev ),
   pFI_( 0 ), pCurrentRequest_( 0 ), pCurrentRequestBufferLayout_( 0 ), pIRC_( 0 ), pID_( 0 ), pCS_( 0 ),
   pIFC_( 0 ), pAC_( 0 ), features_(), featuresToIgnore_(), sequenceStartTime_( 0 ), stopOnOverflow_( false ),
   readoutStartTime_( 0 ), pSequenceThread_( 0 )
//-----------------------------------------------------------------------------
{
   int result = DEVICE_OK;
   InitializeDefaultErrorMessages();

   result = CreateProperty( MM::g_Keyword_Name, pDev_->serial.read().c_str(), MM::String, true );
   assert( result == DEVICE_OK );
   result = CreateProperty( MM::g_Keyword_Description, pDev_->product.read().c_str(), MM::String, true );
   assert( result == DEVICE_OK );
   result = CreateProperty( MM::g_Keyword_CameraID, pDev_->serial.read().c_str(), MM::String, true );
   assert( result == DEVICE_OK );

   pSequenceThread_ = new MySequenceThread( this );
}

//-----------------------------------------------------------------------------
mvIMPACT_Acquire_Device::~mvIMPACT_Acquire_Device()
//-----------------------------------------------------------------------------
{
   Shutdown();
   DeleteElement( pSequenceThread_ );
}

//-----------------------------------------------------------------------------
void mvIMPACT_Acquire_Device::ClearRequestQueue( void )
//-----------------------------------------------------------------------------
{
   pFI_->imageRequestReset( 0, 0 );
   // extract and unlock all requests that are now returned as 'aborted'
   int requestNr = INVALID_ID;
   while( ( requestNr = pFI_->imageRequestWaitFor( 0 ) ) >= 0 )
   {
      Request* pRequest = pFI_->getRequest( requestNr );
      pRequest->unlock();
   }
}

//-----------------------------------------------------------------------------
template<typename _Ty, typename _TValMM, typename _TValIA>
int mvIMPACT_Acquire_Device::CreateIntPropertyFromDriverFeature( ComponentIterator it, const string& fullName, const bool boReadOnly )
//-----------------------------------------------------------------------------
{
   int result = DEVICE_OK;
   _Ty prop( it );
   if( prop.hasDict() )
   {
      result = CreateStringProperty( fullName.c_str(), prop.readS().c_str(), boReadOnly, new CPropertyAction( this, &mvIMPACT_Acquire_Device::OnPropertyChanged ) );
      vector<string> allowedValues;
      prop.getTranslationDictStrings( allowedValues );
      SetAllowedValues( fullName.c_str(), allowedValues );
   }
   else
   {
      result = CreateIntegerProperty( fullName.c_str(), static_cast<_TValMM>( prop.read() ), boReadOnly, new CPropertyAction( this, &mvIMPACT_Acquire_Device::OnPropertyChanged ) );
      const _TValIA minValue = prop.hasMinValue() ? prop.getMinValue() : numeric_limits<_TValIA>::min();
      const _TValIA maxValue = prop.hasMaxValue() ? prop.getMaxValue() : numeric_limits<_TValIA>::max();
      SetPropertyLimits( fullName.c_str(), static_cast<_TValMM>( minValue ), static_cast<_TValMM>( maxValue ) );
   }
   return result;
}

//-----------------------------------------------------------------------------
double mvIMPACT_Acquire_Device::GetExposure( void ) const
//-----------------------------------------------------------------------------
{
   double exposureTime = 0.;
   MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT
   if( pAC_ )
   {
      if( pAC_->exposureTime.isValid() )
      {
         exposureTime = pAC_->exposureTime.read();
      }
   }
   else if( pCS_ && pCS_->expose_us.isValid() )
   {
      exposureTime = static_cast<double>( pCS_->expose_us.read() );
   }
   exposureTime /= s_exposureTimeConvertFactor_;
   MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT
   return exposureTime;
}

//-----------------------------------------------------------------------------
void mvIMPACT_Acquire_Device::SetExposure( double exp )
//-----------------------------------------------------------------------------
{
   MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT
   exp *= s_exposureTimeConvertFactor_;
   if( pAC_ )
   {
      if( pAC_->exposureTime.isValid() )
      {
         pAC_->exposureTime.write( exp );
      }
   }
   else if( pCS_ && pCS_->expose_us.isValid() )
   {
      pCS_->expose_us.write( static_cast<int>( exp ) );
   }
   MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::GetROI( unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize )
//-----------------------------------------------------------------------------
{
   MICRO_MANAGER_INTERFACE_PROLOGUE
   if( pIFC_ )
   {
      x = static_cast<unsigned>( pIFC_->offsetX.read() );
      y = static_cast<unsigned>( pIFC_->offsetY.read() );
      xSize = static_cast<unsigned>( pIFC_->width.read() );
      ySize = static_cast<unsigned>( pIFC_->height.read() );
   }
   else if( pCS_ )
   {
      x = static_cast<unsigned>( pCS_->aoiStartX.read() );
      y = static_cast<unsigned>( pCS_->aoiStartY.read() );
      xSize = static_cast<unsigned>( pCS_->aoiWidth.read() );
      ySize = static_cast<unsigned>( pCS_->aoiHeight.read() );
   }
   MICRO_MANAGER_INTERFACE_EPILOGUE
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::SetROI( unsigned x, unsigned y, unsigned xSize, unsigned ySize )
//-----------------------------------------------------------------------------
{
   MICRO_MANAGER_INTERFACE_PROLOGUE
   if( pIFC_ )
   {
      // in the GenICam interface the max. width/height might be smaller than the new
      // desired width/height until we reset the offsets!
      pIFC_->offsetX.write( 0 );
      pIFC_->offsetY.write( 0 );
      pIFC_->width.write( static_cast<int64_type>( xSize ) );
      pIFC_->height.write( static_cast<int64_type>( ySize ) );
      pIFC_->offsetX.write( static_cast<int64_type>( x ) );
      pIFC_->offsetY.write( static_cast<int64_type>( y ) );
   }
   else if( pCS_ )
   {
      pCS_->aoiStartX.write( static_cast<int>( x ) );
      pCS_->aoiStartY.write( static_cast<int>( y ) );
      pCS_->aoiWidth.write( static_cast<int>( xSize ) );
      pCS_->aoiHeight.write( static_cast<int>( ySize ) );
   }
   RefreshCaptureBufferLayout();
   MICRO_MANAGER_INTERFACE_EPILOGUE
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::ClearROI( void )
//-----------------------------------------------------------------------------
{
   MICRO_MANAGER_INTERFACE_PROLOGUE
   if( pIFC_ )
   {
      pIFC_->offsetX.write( pIFC_->offsetX.getMinValue() );
      pIFC_->offsetY.write( pIFC_->offsetY.getMinValue() );
      pIFC_->width.write( pIFC_->width.getMaxValue() );
      pIFC_->height.write( pIFC_->height.getMaxValue() );
   }
   else if( pCS_ )
   {
      pCS_->aoiStartX.restoreDefault();
      pCS_->aoiStartY.restoreDefault();
      pCS_->aoiWidth.restoreDefault();
      pCS_->aoiHeight.restoreDefault();
   }
   RefreshCaptureBufferLayout();
   MICRO_MANAGER_INTERFACE_EPILOGUE
}

//-----------------------------------------------------------------------------
unsigned mvIMPACT_Acquire_Device::GetBitDepth( void ) const
//-----------------------------------------------------------------------------
{
   unsigned result = 0;
   MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT
   result = static_cast<unsigned>( GetCurrentRequest()->imageChannelBitDepth.read() );
   MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT
   return result;
}

//-----------------------------------------------------------------------------
const unsigned char* mvIMPACT_Acquire_Device::GetImageBuffer( void )
//-----------------------------------------------------------------------------
{
   unsigned char* pResult = 0;
   MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT
   pResult = reinterpret_cast<unsigned char*>( GetCurrentRequest()->imageData.read() );
   MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT
   return pResult;
}

//-----------------------------------------------------------------------------
long mvIMPACT_Acquire_Device::GetImageBufferSize( void ) const
//-----------------------------------------------------------------------------
{
   unsigned result = 0;
   MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT
   result = static_cast<unsigned>( GetCurrentRequest()->imageSize.read() );
   MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT
   return result;
}

//-----------------------------------------------------------------------------
unsigned mvIMPACT_Acquire_Device::GetImageBytesPerPixel( void ) const
//-----------------------------------------------------------------------------
{
   unsigned result = 0;
   MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT
   result = static_cast<unsigned>( GetCurrentRequest()->imageBytesPerPixel.read() );
   MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT
   return result;
}

//-----------------------------------------------------------------------------
unsigned mvIMPACT_Acquire_Device::GetImageHeight( void ) const
//-----------------------------------------------------------------------------
{
   unsigned result = 0;
   MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT
   result = static_cast<unsigned>( GetCurrentRequest()->imageHeight.read() );
   MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT
   return result;
}

//-----------------------------------------------------------------------------
unsigned mvIMPACT_Acquire_Device::GetImageWidth( void ) const
//-----------------------------------------------------------------------------
{
   unsigned result = 0;
   MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT
   result = static_cast<unsigned>( GetCurrentRequest()->imageWidth.read() );
   MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT
   return result;
}

//-----------------------------------------------------------------------------
unsigned mvIMPACT_Acquire_Device::GetNumberOfComponents( void ) const
//-----------------------------------------------------------------------------
{
   unsigned result = 1;
   MICRO_MANAGER_INTERFACE_PROLOGUE_NO_RESULT
   result = static_cast<unsigned>( ( GetCurrentRequest()->imageChannelCount.read() == 1 ) ? 1 : 4 );
   MICRO_MANAGER_INTERFACE_EPILOGUE_NO_RESULT
   return result;
}

//-----------------------------------------------------------------------------
void mvIMPACT_Acquire_Device::PopulateMap( DeviceFeatureContainer& m, ComponentIterator it, const string& currentPath, int searchMode /* = 0 */ )
//-----------------------------------------------------------------------------
{
   while( it.isValid() )
   {
      string fullName( currentPath );
      if( fullName != "" )
      {
         fullName += "/";
      }
      fullName += it.name();
      if( featuresToIgnore_.empty() || ( featuresToIgnore_.find( fullName ) == featuresToIgnore_.end() ) )
      {
         if( it.isList() && ( !( searchMode & smIgnoreLists ) ) )
         {
            // results in an endless loop because VisionPro will call GetFeatureName with index 0
            // again whenever GetFeatureType returns 'category'. This however can only work correctly
            // when ALL features have unique names as in GenICam. mvIMPACT Acquire can not guarantee this,
            // thus we must use a flat hierarchy.
            //m.insert( make_pair( fullName, Component(it) ) );
            PopulateMap( m, it.firstChild(), fullName, searchMode );
         }
         else if( it.isProp() && ( !( searchMode & smIgnoreProperties ) ) )
         {
            int result = DEVICE_OK;
            const bool boReadOnly = !it.isWriteable();
            switch( it.type() )
            {
            case ctPropInt:
               m.insert( make_pair( fullName, Component( it ) ) );
               result = CreateIntPropertyFromDriverFeature<PropertyI, long, int>( it, fullName, boReadOnly );
               break;
            case ctPropInt64:
               m.insert( make_pair( fullName, Component( it ) ) );
               result = CreateIntPropertyFromDriverFeature<PropertyI64, long, int64_type>( it, fullName, boReadOnly );
               break;
            case ctPropFloat:
               {
                  m.insert( make_pair( fullName, Component( it ) ) );
                  PropertyF prop( it );
                  if( prop.hasDict() )
                  {
                     result = CreateStringProperty( fullName.c_str(), prop.readS().c_str(), boReadOnly, new CPropertyAction( this, &mvIMPACT_Acquire_Device::OnPropertyChanged ) );
                     vector<string> allowedValues;
                     prop.getTranslationDictStrings( allowedValues );
                     SetAllowedValues( fullName.c_str(), allowedValues );
                  }
                  else
                  {
                     result = CreateFloatProperty( fullName.c_str(), prop.read(), boReadOnly, new CPropertyAction( this, &mvIMPACT_Acquire_Device::OnPropertyChanged ) );
                     const double minValue = prop.hasMinValue() ? prop.getMinValue() : -1. * numeric_limits<double>::max(); // DBL_MIN is NOT what you'd expect it to be!
                     const double maxValue = prop.hasMaxValue() ? prop.getMaxValue() : numeric_limits<double>::max();
                     SetPropertyLimits( fullName.c_str(), minValue, maxValue );
                  }
               }
               break;
            case ctPropString:
               {
                  m.insert( make_pair( fullName, Component( it ) ) );
                  PropertyS prop( it );
                  result = CreateStringProperty( fullName.c_str(), prop.read().c_str(), boReadOnly, new CPropertyAction( this, &mvIMPACT_Acquire_Device::OnPropertyChanged ) );
               }
               break;
            case ctPropPtr: // ignore pointer properties as these can't be handled in a meaningful way
            default:
               break;
            }
            assert( result == DEVICE_OK );
         }
         else if( it.isMeth() && ( !( searchMode & smIgnoreMethods ) ) )
         {
            m.insert( make_pair( fullName, Component( it ) ) );
         }
      }
      else
      {
         ostringstream oss;
         oss << string( __FUNCTION__ ) << "(" << __LINE__ << "): Feature '" << fullName << "' skipped as it is listed in the ignore table.";
         LogMessage( oss.str(), true );
      }
      ++it;
   }
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::Initialize( void )
//-----------------------------------------------------------------------------
{
   MICRO_MANAGER_INTERFACE_PROLOGUE
   if( !pDev_->isOpen() )
   {
      // if possible use the 'GenICam' interface layout
      if( pDev_->interfaceLayout.isValid() && ( pDev_->interfaceLayout.read() != dilGenICam ) )
      {
         vector<TDeviceInterfaceLayout> validInterfaceLayouts;
         pDev_->interfaceLayout.getTranslationDictValues( validInterfaceLayouts );
         if( find( validInterfaceLayouts.begin(), validInterfaceLayouts.end(), dilGenICam ) != validInterfaceLayouts.end() )
         {
            pDev_->interfaceLayout.write( dilGenICam );
         }
      }
      pDev_->open();
      pFI_ = new FunctionInterface( pDev_ );
      pIRC_ = new ImageRequestControl( pDev_ );
      pID_ = new ImageDestination( pDev_ );
      const TDeviceInterfaceLayout interfaceLayout = pDev_->interfaceLayout.isValid() ? pDev_->interfaceLayout.read() : dilDeviceSpecific;
      switch( interfaceLayout )
      {
      case dilGenICam:
         if( !pIFC_ )
         {
            pIFC_ = new mvIMPACT::acquire::GenICam::ImageFormatControl( pDev_ );
            SetUpBinningProperties_GenICam();
         }
         if( !pAC_ )
         {
            pAC_ = new mvIMPACT::acquire::GenICam::AcquisitionControl( pDev_ );
            if( pAC_->exposureTime.isValid() )
            {
               result = CreateFloatProperty( MM::g_Keyword_Exposure, pAC_->exposureTime.read() / s_exposureTimeConvertFactor_, false );
               assert( result == DEVICE_OK );
               SetPropertyLimits( MM::g_Keyword_Exposure, pAC_->exposureTime.getMinValue() / s_exposureTimeConvertFactor_, pAC_->exposureTime.getMaxValue() / s_exposureTimeConvertFactor_ );
            }
         }
         break;
      case dilDeviceSpecific:
         if( !pCS_ )
         {
            pCS_ = new mvIMPACT::acquire::CameraSettingsBlueDevice( pDev_ );
            SetUpBinningProperties_DeviceSpecific();
         }
         if( pCS_->expose_us.isValid() )
         {
            result = CreateFloatProperty( MM::g_Keyword_Exposure, static_cast<double>( pCS_->expose_us.read() ) / s_exposureTimeConvertFactor_, false );
            assert( result == DEVICE_OK );
            SetPropertyLimits( MM::g_Keyword_Exposure, static_cast<double>( pCS_->expose_us.getMinValue() ) / s_exposureTimeConvertFactor_, static_cast<double>( pCS_->expose_us.getMaxValue() ) / s_exposureTimeConvertFactor_ );
         }
         break;
      }

      // pixel type
      CPropertyAction* pAct = new CPropertyAction( this, &mvIMPACT_Acquire_Device::OnPixelType );
      result = CreateStringProperty( MM::g_Keyword_PixelType, s_PixelType_8bit, false, pAct );
      assert( result == DEVICE_OK );
      vector<string> pixelTypeValues;
      pixelTypeValues.push_back( s_PixelType_8bit );
      pixelTypeValues.push_back( s_PixelType_16bit );
      pixelTypeValues.push_back( s_PixelType_32bitRGB );
      //pixelTypeValues.push_back( s_PixelType_64bitRGB );
      result = SetAllowedValues( MM::g_Keyword_PixelType, pixelTypeValues );
      if( result != DEVICE_OK )
      {
         return result;
      }

      // all other mvIMPACT Acquire properties
      featuresToIgnore_.insert( "ImageDestination/PixelFormat" ); // needed to configure the mvIMPACT Acquire output in a way Micro-Manager can digest!
      DeviceComponentLocator locator( pDev_, dltSetting, "Base" );
      locator.bindSearchBase( locator.hObj(), "Camera" );
      const int searchMode = smIgnoreMethods;
      PopulateMap( features_, ComponentIterator( locator.searchbase_id() ).firstChild(), "Camera", searchMode );
      // not every device might offer selectable input channels
      if( locator.findComponent( "Connector" ) != INVALID_ID )
      {
         locator.bindSearchBase( locator.hObj(), "Connector" );
         PopulateMap( features_, ComponentIterator( locator.searchbase_id() ).firstChild(), "Connector", searchMode );
      }
      locator.bindSearchBase( locator.hObj(), "ImageDestination" );
      PopulateMap( features_, ComponentIterator( locator.searchbase_id() ).firstChild(), "ImageDestination", searchMode );
      locator.bindSearchBase( locator.hObj(), "ImageProcessing" );
      PopulateMap( features_, ComponentIterator( locator.searchbase_id() ).firstChild(), "ImageProcessing", searchMode );

      RefreshCaptureBufferLayout();
   }
   MICRO_MANAGER_INTERFACE_EPILOGUE
}

//-----------------------------------------------------------------------------
void mvIMPACT_Acquire_Device::RefreshCaptureBufferLayout( void )
//-----------------------------------------------------------------------------
{
   pFI_->getCurrentCaptureBufferLayout( *pIRC_, &pCurrentRequestBufferLayout_ );
   if( pCurrentRequest_ )
   {
      int result = DEVICE_OK;
      LOGGED_MVIMPACT_ACQUIRE_CALL( pCurrentRequest_->unlock, () );
      pCurrentRequest_ = 0;
   }
   const TImageDestinationPixelFormat previousDestinationPixelFormat = pID_->pixelFormat.read();
   switch( pCurrentRequestBufferLayout_->imagePixelFormat.read() )
   {
   case ibpfMono8:
      pID_->pixelFormat.write( idpfMono8 );
      break;
   case ibpfMono10:
   case ibpfMono12:
   case ibpfMono12Packed_V1:
   case ibpfMono12Packed_V2:
   case ibpfMono14:
   case ibpfMono16:
   case ibpfMono32:
      pID_->pixelFormat.write( idpfMono16 );
      break;
   case ibpfRGBx888Packed:
   case ibpfRGBx888Planar:
   case ibpfBGR888Packed:
   case ibpfRGB888Packed:
   case ibpfRGB888Planar:
   case ibpfYUV411_UYYVYY_Packed:
   case ibpfYUV422Packed:
   case ibpfYUV422_UYVYPacked:
   case ibpfYUV422Planar:
   case ibpfYUV444Packed:
   case ibpfYUV444_UYVPacked:
   case ibpfYUV444Planar:
      pID_->pixelFormat.write( idpfRGBx888Packed );
      break;
   // All these could be handled using RGB64 of micro-manager. Unfortunately we deliver RGB48...
   case ibpfBGR101010Packed_V2:
   case ibpfYUV422_10Packed:
   case ibpfYUV422_UYVY_10Packed:
   case ibpfRGB101010Packed:
   case ibpfRGB121212Packed:
   case ibpfRGB141414Packed:
   case ibpfRGB161616Packed:
   case ibpfYUV444_10Packed:
   case ibpfYUV444_UYV_10Packed:
      pID_->pixelFormat.write( idpfRGBx888Packed );
      break;
   case ibpfAuto:
   case ibpfRaw:
      pID_->pixelFormat.write( idpfMono8 );
      break;
      // do NOT add a default here! Whenever the compiler complains it is
      // missing not every format is handled here, which means that at least
      // one has been forgotten and that should be fixed!
   }
   if( previousDestinationPixelFormat != pID_->pixelFormat.read() )
   {
      // in case we did just change the destination pixel format we need to query the
      // current output format once again as the buffer layout might be different now!
      pFI_->getCurrentCaptureBufferLayout( *pIRC_, &pCurrentRequestBufferLayout_ );
   }
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::OnPixelType( MM::PropertyBase* pProp, MM::ActionType eAct )
//-----------------------------------------------------------------------------
{
   int result = DEVICE_ERR;
   switch( eAct )
   {
   case MM::AfterSet:
      {
         if( IsCapturing() )
         {
            result = DEVICE_CAMERA_BUSY_ACQUIRING;
         }
         else
         {
            string pixelType;
            pProp->Get( pixelType );
            if( //( pixelType.compare( s_PixelType_64bitRGB ) == 0 ) ||
               ( pixelType.compare( s_PixelType_32bitRGB ) == 0 ) ||
               ( pixelType.compare( s_PixelType_16bit ) == 0 ) ||
               ( pixelType.compare( s_PixelType_8bit ) == 0 ) )
            {
               result = DEVICE_OK;
            }
            else
            {
               // on error switch to default pixel type
               pProp->Set( s_PixelType_8bit );
               result = DEVICE_INVALID_PROPERTY_VALUE;
            }
            RefreshCaptureBufferLayout();
         }
      }
      break;
   case MM::BeforeGet:
      {
         long bytesPerPixel = GetImageBytesPerPixel();
         if( bytesPerPixel == 1 )
         {
            pProp->Set( s_PixelType_8bit );
            result = DEVICE_OK;
         }
         else if( bytesPerPixel == 2 )
         {
            pProp->Set( s_PixelType_16bit );
            result = DEVICE_OK;
         }
         else if( bytesPerPixel == 4 )
         {
            pProp->Set( s_PixelType_32bitRGB );
            result = DEVICE_OK;
         }
         else if( bytesPerPixel == 8 )
         {
            //pProp->Set(s_PixelType_64bitRGB);
            result = DEVICE_INVALID_PROPERTY_VALUE;
         }
         else
         {
            pProp->Set( s_PixelType_8bit );
            result = DEVICE_INVALID_PROPERTY_VALUE;
         }

      }
      break;
   default:
      break;
   }
   return result;
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::OnPropertyChanged( MM::PropertyBase* pProp, MM::ActionType eAct )
//-----------------------------------------------------------------------------
{
   MICRO_MANAGER_INTERFACE_PROLOGUE
   if( pProp )
   {
      const DeviceFeatureContainer::const_iterator it = features_.find( pProp->GetName() );
      if( it != features_.end() )
      {
         Component comp( it->second );
         switch( eAct )
         {
         case MM::AfterSet:
            switch( comp.type() )
            {
            case ctPropInt:
               SetPropVal_AfterSet<PropertyI, long, int>( pProp, it->second );
               break;
            case ctPropInt64:
               SetPropVal_AfterSet<PropertyI64, long, int64_type>( pProp, it->second );
               break;
            case ctPropFloat:
               SetPropVal_AfterSet<PropertyF, double, double>( pProp, it->second );
               break;
            case ctPropString:
               {
                  PropertyS prop( it->second );
                  string value;
                  pProp->Get( value );
                  prop.write( value );
               }
               break;
            }
            break;
         case MM::BeforeGet:
            switch( comp.type() )
            {
            case ctPropInt:
               SetPropVal_BeforeGet<PropertyI, long>( pProp, it->second );
               break;
            case ctPropInt64:
               SetPropVal_BeforeGet<PropertyI64, long>( pProp, it->second );
               break;
            case ctPropFloat:
               SetPropVal_BeforeGet<PropertyF, double>( pProp, it->second );
               break;
            case ctPropString:
               {
                  PropertyS prop( it->second );
                  pProp->Set( prop.readS().c_str() );
               }
               break;
            }
            break;
         default:
            break;
         }
      }
      else
      {
         ostringstream oss;
         oss << string( __FUNCTION__ ) << "(" << __LINE__ << "): Feature '" << pProp->GetName() << "' could not be located!";
         LogMessage( oss.str() );
         result = DEVICE_INVALID_PROPERTY;
      }
   }
   else
   {
      ostringstream oss;
      oss << string( __FUNCTION__ ) << "(" << __LINE__ << "): Invalid pProp pointer detected!";
      LogMessage( oss.str() );
      result = DEVICE_INVALID_PROPERTY;
   }
   MICRO_MANAGER_INTERFACE_EPILOGUE
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::Shutdown( void )
//-----------------------------------------------------------------------------
{
   MICRO_MANAGER_INTERFACE_PROLOGUE
   if( pDev_->isOpen() )
   {
      StopSequenceAcquisition();
      ClearRequestQueue();
      DeleteElement( pFI_ );
      DeleteElement( pIRC_ );
      DeleteElement( pID_ );
      DeleteElement( pCS_ );
      DeleteElement( pIFC_ );
      DeleteElement( pAC_ );
      features_.clear();
      pDev_->close();
   }
   MICRO_MANAGER_INTERFACE_EPILOGUE
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::SnapImage( void )
//-----------------------------------------------------------------------------
{
   int result = DEVICE_ERR;
   if( pDev_->isOpen() && pFI_ )
   {
      LOGGED_MVIMPACT_ACQUIRE_CALL( pFI_->imageRequestSingle, () )
      const int requestNr = pFI_->imageRequestWaitFor( -1 );
      if( pFI_->isRequestNrValid( requestNr ) )
      {
         Request* pRequest = pFI_->getRequest( requestNr );
         if( pRequest->isOK() )
         {
            swap( pCurrentRequest_, pRequest );
            readoutStartTime_ = GetCurrentMMTime();
            result = DEVICE_OK;
         }
         if( pRequest )
         {
            LOGGED_MVIMPACT_ACQUIRE_CALL( pRequest->unlock, () )
         }
      }
      else
      {
         ostringstream oss;
         oss << string( __FUNCTION__ ) << "(" << __LINE__ << "): imageRequestWaitFor failed: " << ImpactAcquireException::getErrorCodeAsString( requestNr ) << "(" << requestNr << ")";
         LogMessage( oss.str() );
      }
   }
   else if( !pDev_->isOpen() )
   {
      ostringstream oss;
      oss << string( __FUNCTION__ ) << "(" << __LINE__ << "): Device is not open!";
      LogMessage( oss.str() );
   }
   else if( !pFI_ )
   {
      ostringstream oss;
      oss << string( __FUNCTION__ ) << "(" << __LINE__ << "): No valid pointer to function interface!";
      LogMessage( oss.str() );
   }
   return result;
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::StopSequenceAcquisition( void )
//-----------------------------------------------------------------------------
{
   if( !pSequenceThread_->IsStopped() )
   {
      pSequenceThread_->Stop();
      pSequenceThread_->wait();
      ClearRequestQueue();
   }
   return DEVICE_OK;
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::StartSequenceAcquisition( long numImages, double interval_ms, bool stopOnOverflow )
//-----------------------------------------------------------------------------
{
   if( IsCapturing() )
   {
      return DEVICE_CAMERA_BUSY_ACQUIRING;
   }

   const int result = GetCoreCallback()->PrepareForAcq( this );
   if( result == DEVICE_OK )
   {
      sequenceStartTime_ = GetCurrentMMTime();
      pSequenceThread_->Start( numImages, interval_ms );
      stopOnOverflow_ = stopOnOverflow;
   }
   return result;
}

//-----------------------------------------------------------------------------
bool mvIMPACT_Acquire_Device::IsCapturing( void )
//-----------------------------------------------------------------------------
{
   return !pSequenceThread_->IsStopped();
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::InsertImage( void )
//-----------------------------------------------------------------------------
{
   Metadata md;
   char label[MM::MaxStrLength];
   GetLabel( label );
   md.put( "Camera", label );
   md.put( MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString( sequenceStartTime_.getMsec() ) );
   MM::MMTime timeStamp = readoutStartTime_;
   md.put( MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString( ( timeStamp - sequenceStartTime_ ).getMsec() ) );
   md.put( MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString( static_cast<long>( pCurrentRequest_->infoFrameID.read() ) ) );
   md.put( MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( pCurrentRequest_->imageOffsetX.read() ) );
   md.put( MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( pCurrentRequest_->imageOffsetY.read() ) );
   const unsigned char* pI = GetImageBuffer();
   const unsigned int w = GetImageWidth();
   const unsigned int h = GetImageHeight();
   const unsigned int b = GetImageBytesPerPixel();
   int result = GetCoreCallback()->InsertImage( this, pI, w, h, b, md.Serialize().c_str(), false );
   if( !stopOnOverflow_ && ( result == DEVICE_BUFFER_OVERFLOW ) )
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer( this );
      // don't process this same image again...
      result = GetCoreCallback()->InsertImage( this, pI, w, h, b, md.Serialize().c_str(), false );
   }
   return result;
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::RunSequenceOnThread( MM::MMTime startTime )
//-----------------------------------------------------------------------------
{
   int result = SnapImage();
   if( result == DEVICE_OK )
   {
      result = InsertImage();
   }
   return result;
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::SetUpBinningProperties_DeviceSpecific( void )
//-----------------------------------------------------------------------------
{
   // We always provide the Binning property!
   CPropertyAction* pAct = new CPropertyAction( this, &mvIMPACT_Acquire_Device::OnBinning );
   vector<string> supportedBinningModes;
   string currentValue( "Off" );
   if( pCS_ && pCS_->binningMode.isValid() )
   {
      pCS_->binningMode.getTranslationDictStrings( supportedBinningModes );
      currentValue = pCS_->binningMode.readS();
   }
   else
   {
      supportedBinningModes.push_back( "Off" );
   }

   int nRet = CreateProperty( MM::g_Keyword_Binning, currentValue.c_str(), MM::String, false, pAct );
   if( nRet == DEVICE_OK )
   {
      nRet = SetAllowedValues( MM::g_Keyword_Binning, supportedBinningModes );
   }
   return nRet;
}

//-----------------------------------------------------------------------------
vector<int64_type> mvIMPACT_Acquire_Device::GetSupportedBinningValues_GenICam( PropertyI64 prop ) const
//-----------------------------------------------------------------------------
{
   vector<int64_type> values;
   if( prop.isValid() )
   {
      if( prop.hasDict() )
      {
         prop.getTranslationDictValues( values );
      }
      else if( prop.hasMinValue() && prop.hasMaxValue() )
      {
         const int64_type min = prop.getMinValue();
         const int64_type max = prop.getMaxValue();
         for( int64_type i = min; i <= max; i *= 2 )
         {
            values.push_back( i );
         }
         sort( values.begin(), values.end() );
      }
   }
   if( values.empty() )
   {
      values.push_back( 1 );
   }
   return values;
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::SetUpBinningProperties_GenICam( void )
//-----------------------------------------------------------------------------
{
   // note that the GenICam spec separates vertical and horizontal binning and does
   // not provide a single, unified binning property. The various OnBinning methods
   // will do their best to provide this illusion of a unified binning when possible.

   // We always provide the Binning property, regardless of support for non-unity binning.
   CPropertyAction* pAct = new CPropertyAction( this, &mvIMPACT_Acquire_Device::OnBinning );
   vector<int64_type> binningVerticalValues = GetSupportedBinningValues_GenICam( pIFC_->binningVertical );
   const vector<int64_type>::size_type binningVerticalValueCount = binningVerticalValues.size();
   vector<int64_type> binningHorizontalValues = GetSupportedBinningValues_GenICam( pIFC_->binningHorizontal );
   const vector<int64_type>::size_type binningHorizontalValueCount = binningHorizontalValues.size();
   vector<string> supportedBinningModes;
   for( vector<int64_type>::size_type i = 0; i < binningVerticalValueCount; i++ )
   {
      for( vector<int64_type>::size_type j = 0; j < binningHorizontalValueCount; j++ )
      {
         ostringstream oss;
         oss << binningHorizontalValues[j] << "x" << binningVerticalValues[i];
         supportedBinningModes.push_back( oss.str() );
      }
   }

   ostringstream currentValue;
   if( pIFC_ )
   {
      currentValue << ( pIFC_->binningHorizontal.isValid() ? pIFC_->binningHorizontal.readS() : "1" )
                   << "x"
                   << ( pIFC_->binningVertical.isValid() ? pIFC_->binningVertical.readS() : "1" );
   }
   else
   {
      currentValue << "1x1";
   }

   int nRet = CreateProperty( MM::g_Keyword_Binning, currentValue.str().c_str(), MM::String, false, pAct );
   if( nRet == DEVICE_OK )
   {
      nRet = SetAllowedValues( MM::g_Keyword_Binning, supportedBinningModes );
   }
   return nRet;
}

//-----------------------------------------------------------------------------
void mvIMPACT_Acquire_Device::SetBinningProperty( Property prop, const string& value, int& ret, bool& boMustRefreshCaptureBufferLayout )
//-----------------------------------------------------------------------------
{
   if( prop.isValid() )
   {
      if( prop.isWriteable() )
      {
         try
         {
            if( prop.readS() != value )
            {
               prop.writeS( value );
               boMustRefreshCaptureBufferLayout = true;
            }
         }
         catch( const ImpactAcquireException& e )
         {
            LOG_MVIMPACT_ACQUIRE_EXCEPTION( e )
            ret = DEVICE_ERR;
         }
      }
      else
      {
         ostringstream oss;
         oss << "'" << prop.name() << "' doesn't seem to be writable at the moment! Cannot change this parameter.";
         LogMessage( oss.str() );
      }
   }
}

//-----------------------------------------------------------------------------
int mvIMPACT_Acquire_Device::OnBinning( MM::PropertyBase* pProp, MM::ActionType eAct )
//-----------------------------------------------------------------------------
{
   int ret = DEVICE_OK;
   switch( eAct )
   {
   case MM::AfterSet:
      {
         if( IsCapturing() )
         {
            return DEVICE_CAMERA_BUSY_ACQUIRING;
         }

         // try to set the vertical and horizontal binning
         string value;
         pProp->Get( value );
         bool boMustRefreshCaptureBufferLayout = false;
         if( pIFC_ )
         {
            string::size_type separatorPosition = value.find_first_of( "x" );
            if( separatorPosition != string::npos )
            {
               SetBinningProperty( pIFC_->binningVertical, value.substr( separatorPosition + 1 ), ret, boMustRefreshCaptureBufferLayout );
               SetBinningProperty( pIFC_->binningHorizontal, value.substr( 0, separatorPosition ), ret, boMustRefreshCaptureBufferLayout );
            }

         }
         else if( pCS_ )
         {
            SetBinningProperty( pCS_->binningMode, value, ret, boMustRefreshCaptureBufferLayout );
         }
         if( boMustRefreshCaptureBufferLayout )
         {
            RefreshCaptureBufferLayout();
         }
      }
      break;
   case MM::BeforeGet:
      {
         // the user is requesting the current value for the property, so
         // either ask the 'hardware' or let the system return the value
         // cached in the property.
         ret = DEVICE_OK;
      }
      break;
   }
   return ret;
}

//=============================================================================
//==================== Implementation MySequenceThread ========================
//=============================================================================
//-----------------------------------------------------------------------------
MySequenceThread::MySequenceThread( mvIMPACT_Acquire_Device* pmvIMPACT_Acquire_Device ) :
   intervalMs_( default_intervalMS ), numImages_( default_numImages ), imageCounter_( 0 ),
   stop_( true ), suspend_( false ), pmvIMPACT_Acquire_Device_( pmvIMPACT_Acquire_Device ),
   startTime_( 0 ), actualDuration_( 0 ), lastFrameTime_( 0 )
//-----------------------------------------------------------------------------
{
}

//-----------------------------------------------------------------------------
void MySequenceThread::Stop( void )
//-----------------------------------------------------------------------------
{
   MMThreadGuard g( this->stopLock_ );
   stop_ = true;
}

//-----------------------------------------------------------------------------
void MySequenceThread::Start( long numImages, double intervalMs )
//-----------------------------------------------------------------------------
{
   MMThreadGuard g1( this->stopLock_ );
   MMThreadGuard g2( this->suspendLock_ );
   numImages_ = numImages;
   intervalMs_ = intervalMs;
   imageCounter_ = 0;
   stop_ = false;
   suspend_ = false;
   activate();
   actualDuration_ = 0;
   startTime_ = pmvIMPACT_Acquire_Device_->GetCurrentMMTime();
   lastFrameTime_ = 0;
}

//-----------------------------------------------------------------------------
bool MySequenceThread::IsStopped( void )
//-----------------------------------------------------------------------------
{
   MMThreadGuard g( this->stopLock_ );
   return stop_;
}

//-----------------------------------------------------------------------------
void MySequenceThread::Suspend( void )
//-----------------------------------------------------------------------------
{
   MMThreadGuard g( this->suspendLock_ );
   suspend_ = true;
}

//-----------------------------------------------------------------------------
bool MySequenceThread::IsSuspended( void )
//-----------------------------------------------------------------------------
{
   MMThreadGuard g( this->suspendLock_ );
   return suspend_;
}

//-----------------------------------------------------------------------------
void MySequenceThread::Resume( void )
//-----------------------------------------------------------------------------
{
   MMThreadGuard g( this->suspendLock_ );
   suspend_ = false;
}

//-----------------------------------------------------------------------------
int MySequenceThread::svc( void ) throw()
//-----------------------------------------------------------------------------
{
   int result = DEVICE_ERR;
   try
   {
      do
      {
         result = pmvIMPACT_Acquire_Device_->RunSequenceOnThread( startTime_ );
      }
      while( ( DEVICE_OK == result ) && !IsStopped() && ( imageCounter_++ < numImages_ - 1 ) );
      if( IsStopped() )
      {
         pmvIMPACT_Acquire_Device_->LogMessage( "SeqAcquisition interrupted by the user" );
      }
   }
   catch( ... )
   {
      pmvIMPACT_Acquire_Device_->LogMessage( g_Msg_EXCEPTION_IN_THREAD, false );
   }
   stop_ = true;
   actualDuration_ = pmvIMPACT_Acquire_Device_->GetCurrentMMTime() - startTime_;
   pmvIMPACT_Acquire_Device_->OnThreadExiting();
   return result;
}

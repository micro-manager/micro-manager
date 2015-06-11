///////////////////////////////////////////////////////////////////////////////
// FILE:          GigECameraAction.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   An adapter for Gigbit-Ethernet cameras using an
//                SDK from JAI, Inc.  Users and developers will
//                need to download and install the JAI SDK and control tool.
//
// AUTHOR:        David Marshburn, UNC-CH, marshbur@cs.unc.edu, Jan. 2011
//

#include "GigECamera.h"

#include <boost/lexical_cast.hpp>

///////////////////////////////////////////////////////////////////////////////
// CGigECamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Handles "Binning" property.
*/
int CGigECamera::OnBinning( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	int ret = DEVICE_OK;
	switch(eAct)
	{
	case MM::AfterSet:
		{
			if(IsCapturing())
				return DEVICE_CAMERA_BUSY_ACQUIRING;

			// try to set the vertical and horizontal binning
			long binFactor;
			pProp->Get( binFactor );
			if( HasProperty( g_Keyword_Binning_Vertical ) )
				ret |= SetProperty( g_Keyword_Binning_Vertical, CDeviceUtils::ConvertToString( binFactor ) );
			if( HasProperty( g_Keyword_Binning_Horizontal ) )
				ret |= SetProperty( g_Keyword_Binning_Horizontal, CDeviceUtils::ConvertToString( binFactor ) );
		}
		break;
	case MM::BeforeGet:
		{
			// the user is requesting the current value for the property, so
			// either ask the 'hardware' or let the system return the value
			// cached in the property.
			ret=DEVICE_OK;
		}
		break;
	}
	return ret; 
} // OnBinning


int CGigECamera::OnBinningV( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	int ret = DEVICE_OK;
	switch(eAct)
	{
	case MM::AfterSet:
		{
			if(IsCapturing())
				return DEVICE_CAMERA_BUSY_ACQUIRING;

			long binFactor;
			pProp->Get( binFactor );
			int64_t oldBin, oldh;
			nodes->get( oldBin, BINNING_VERTICAL );
			nodes->get( oldh, HEIGHT );
			if( binFactor != (long) oldBin )
			{
				bool retval = nodes->set( binFactor, BINNING_VERTICAL ); 
				if( retval == false )
				{
					// set it back
					nodes->set( oldBin, BINNING_VERTICAL );
					pProp->Set( (long) oldBin );
					ret = DEVICE_INVALID_PROPERTY_VALUE;
				}
				else
				{
					// new limits
					if( HasProperty( g_Keyword_Image_Height ) )
					{
						int64_t high, low;
						// Assuming getMin(), getMax() won't fail if they worked previously
						nodes->getMax( HEIGHT, high );
						nodes->getMin( HEIGHT, low );
						if ( low < high )
							SetPropertyLimits( g_Keyword_Image_Height, (double) low, (double) high );
						else
						{
							ClearAllowedValues( g_Keyword_Image_Height );
							AddAllowedValue( g_Keyword_Image_Height, boost::lexical_cast<std::string>(low).c_str() );
						}

						// new height
						int64_t dim;
						nodes->get( dim, HEIGHT );
						if( dim == oldh ) // this camera doesn't auto-adjust its height w/ binning change
						{
							dim = dim * oldBin / binFactor;
						}
						SetProperty( g_Keyword_Image_Height, CDeviceUtils::ConvertToString( (long) dim ) );
						UpdateProperty( g_Keyword_Image_Height );
						LogMessage( (std::string) "setting v bin to " + boost::lexical_cast<std::string>( binFactor ) 
									+ " and height to " + boost::lexical_cast<std::string>( dim ) 
									+ " (oldBin:  " + boost::lexical_cast<std::string>( oldBin ) + ")  " 
									+ " new limits (" + boost::lexical_cast<std::string>( low ) + 
									+ " " + boost::lexical_cast<std::string>( high ) + ")", true );
					}

					if( HasProperty( g_Keyword_Image_Height_Max ) )
					{
						UpdateProperty( g_Keyword_Image_Height_Max );
					}
					OnPropertiesChanged();
					ret = DEVICE_OK;
				}
				ResizeImageBuffer();
			}
		}
		break;
	case MM::BeforeGet:
		{
			int64_t vBin;
			nodes->get( vBin, BINNING_VERTICAL );
			pProp->Set( (long) vBin );
			ret=DEVICE_OK;
		}
		break;
	}
	return ret; 
} // OnBinningV



int CGigECamera::OnBinningH( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	int ret = DEVICE_OK;

	switch(eAct)
	{
	case MM::AfterSet:
		{
			if(IsCapturing())
				return DEVICE_CAMERA_BUSY_ACQUIRING;

			long binFactor;
			pProp->Get( binFactor );
			int64_t oldBin, oldw;
			nodes->get( oldBin, BINNING_HORIZONTAL );
			nodes->get( oldw, WIDTH );
			if( binFactor != (long) oldBin )
			{
				bool retval = nodes->set( (int64_t) binFactor, BINNING_HORIZONTAL );
				if( retval == false )
				{
					// set it back
					nodes->set( oldBin, BINNING_HORIZONTAL );
					pProp->Set( (long) oldBin );
					ret = DEVICE_INVALID_PROPERTY_VALUE;
				}
				else
				{
					// new limits
					if( HasProperty( g_Keyword_Image_Width ) )
					{
						int64_t high, low;
						// Assuming getMin(), getMax() won't fail if they worked previously
						nodes->getMax( WIDTH, high );
						nodes->getMin( WIDTH, low );
						if ( low < high )
							SetPropertyLimits( g_Keyword_Image_Width, (double) low, (double) high );
						else
						{
							ClearAllowedValues( g_Keyword_Image_Width );
							AddAllowedValue( g_Keyword_Image_Width, boost::lexical_cast<std::string>(low).c_str() );
						}

						int64_t dim;
						nodes->get( dim, WIDTH );
						if( dim == oldw )
						{
							dim = dim * oldBin / binFactor;
						}
						SetProperty( g_Keyword_Image_Width, CDeviceUtils::ConvertToString( (long) dim ) );
						if ( low < high )
							SetPropertyLimits( g_Keyword_Image_Width, (double) low, (double) high );
						else
						{
							ClearAllowedValues( g_Keyword_Image_Width );
							AddAllowedValue( g_Keyword_Image_Width, boost::lexical_cast<std::string>(low).c_str() );
						}
						UpdateProperty( g_Keyword_Image_Width );
						LogMessage( (std::string) "setting h bin to " + boost::lexical_cast<std::string>( binFactor ) 
									+ " and width to " + boost::lexical_cast<std::string>( dim ) 
									+ " (oldBin:  " + boost::lexical_cast<std::string>( oldBin ) + ")  " 
									+ " new limits (" + boost::lexical_cast<std::string>( low ) + 
									+ " " + boost::lexical_cast<std::string>( high ) + ")", true );
					}

					if( HasProperty( g_Keyword_Image_Width_Max ) )
					{
						UpdateProperty( g_Keyword_Image_Width_Max );
					}
					OnPropertiesChanged();
					ret = DEVICE_OK;
				}
				ResizeImageBuffer();
			}
		}
		break;
	case MM::BeforeGet:
		{
			int64_t hBin;
			nodes->get( hBin, BINNING_HORIZONTAL );
			pProp->Set( (long) hBin );
			ret=DEVICE_OK;
		}
		break;
	}
	return ret; 
} // OnBinningH



int CGigECamera::OnImageWidth( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	int nErr = 0;
	if( eAct == MM::AfterSet )
	{
		long lVal;
		pProp->Get( lVal );
		int64_t oldw, w = lVal;
		nodes->get( oldw,	WIDTH );
		bool retval = nodes->set( w, WIDTH );
		if( retval == false )
		{
			pProp->Set( (long) oldw );
			nErr = DEVICE_INVALID_PROPERTY_VALUE;
		}
		else
			ResizeImageBuffer();
		OnPropertiesChanged();
	}
	return nErr;
}


int CGigECamera::OnImageHeight( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	int nErr = 0;
	if( eAct == MM::AfterSet )
	{
		long lVal;
		pProp->Get( lVal );
		int64_t oldh, h = lVal;
		nodes->get( oldh, HEIGHT );
		bool retval = nodes->set( h, HEIGHT );
		if( retval == false )
		{
			pProp->Set( (long) oldh );
			nErr = DEVICE_INVALID_PROPERTY_VALUE;
		}
		else
			ResizeImageBuffer();
		OnPropertiesChanged();
	}
	return nErr;
}


int CGigECamera::OnImageWidthMax( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	if( eAct == MM::BeforeGet )
	{
		int64_t i = 0;
		nodes->get( i, WIDTH_MAX );
		pProp->Set( (long) i );
	}
	return 0;
}


int CGigECamera::OnImageHeightMax( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	if( eAct == MM::BeforeGet )
	{
		int64_t i = 0;
		nodes->get( i, HEIGHT_MAX );
		pProp->Set( (long) i );
	}
	return 0;
}


/**
* Handles "PixelType" property.
*/
int CGigECamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	LogMessage("OnPixelType was called", true);

	int ret = DEVICE_OK;
	switch(eAct)
	{
	case MM::AfterSet:
		{
			if(IsCapturing())
				return DEVICE_CAMERA_BUSY_ACQUIRING;

			std::string displayName, pixelType;
			pProp->Get( displayName );

			std::map<std::string, std::string>::iterator it = pixelFormatMap.find( displayName );
			if( it == pixelFormatMap.end() )
			{
				LogMessage( (std::string) "internal error:  inconsistency in pixel type map (" + pixelType + ")", false );
				return DEVICE_INTERNAL_INCONSISTENCY;
			}
			pixelType = it->second;

			if( nodes->set( pixelType, PIXEL_FORMAT ) )
			{
				LogMessage("OnPixelType: Set pixel type to " + boost::lexical_cast<std::string>(pixelType),false);
				ret=DEVICE_OK;
			}
			else
				return DEVICE_INVALID_PROPERTY_VALUE;

			// in here we check if color mode is present
			ret = ResizeImageBuffer();
			if( ret != DEVICE_OK )
			{
				LogMessage("OnPixelType: ERROR: somthing went wrong, we dont set default pixel type", true);
				return DEVICE_INTERNAL_INCONSISTENCY;
			}
		} 
		break;
	case MM::BeforeGet:
		{
			std::string s;
			nodes->get( s, PIXEL_FORMAT );
			std::map<std::string, std::string>::iterator it = pixelFormatMap.find( s );
			if( it != pixelFormatMap.end() )
				pProp->Set( it->second.c_str() );
			else
				pProp->Set( s.c_str() );
			ret=DEVICE_OK;
			LogMessage("OnPixelType: BeforeGet: try to set type to: " + it->second, true);
		}
		break;
	}
	return ret; 
}

int CGigECamera::onAcquisitionMode( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	int ret = DEVICE_OK;
	switch(eAct)
	{
	case MM::AfterSet:
		{
			if(IsCapturing())
				return DEVICE_CAMERA_BUSY_ACQUIRING;

			std::string displayName, acqMode;
			pProp->Get( displayName );
			std::map<std::string, std::string>::iterator it = acqModeMap.find( displayName );
			if( it == acqModeMap.end() )
			{
				LogMessage( (std::string) "internal error:  inconsistency in acquisition mode map (" + acqMode + ")", false );
				return DEVICE_INTERNAL_INCONSISTENCY;
			}
			acqMode = it->second;

			if (nodes->set( acqMode, ACQUISITION_MODE ))
			{
				ret = DEVICE_OK;
			}
		}
		break;
	case MM::BeforeGet:
		{
			std::string s;
			nodes->get( s, ACQUISITION_MODE );
			std::map<std::string, std::string>::iterator it = acqModeMap.find( s );
			if( it != acqModeMap.end() )
				pProp->Set( it->second.c_str() );
			else
				pProp->Set( s.c_str() );
			ret = DEVICE_OK;
		}
		break;
	}
	return ret;
}

int CGigECamera::OnCameraChoice( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	if( eAct == MM::AfterSet )
	{
		std::string name;
		pProp->Get( name );
		std::map<std::string,std::string>::iterator i = cameraNameMap.find( name );
		if( i == cameraNameMap.end() )
		{
			LogMessage( (std::string) "internal error:  inconsistency in camera name map (" + name + ")", false );
			return DEVICE_INTERNAL_INCONSISTENCY;
		}
		std::string realName = (*i).second;

		if( this->cameraName.compare( realName ) != 0 ) // squash idempotent changes
		{
			LogMessage( (std::string) "Opening camera:  " + realName );
			J_STATUS_TYPE retval = J_Camera_Close( hCamera );				
			retval = J_Camera_Open( hFactory, cstr2jai( realName.c_str() ), &hCamera );
			// XXX this should really refresh all the camera parameters after opening a new camera,
			// possibly enabling or removing some properties.
			if( retval != J_ST_SUCCESS )
			{
				LogMessage( (std::string) "camera open failed (" + realName + ")", false );
				return DEVICE_NATIVE_MODULE_FAILED;
			}
			cameraName = realName;
		}
	}
	else if( eAct == MM::BeforeGet )
	{
		std::map<std::string,std::string>::iterator i = cameraNameMap.find( cameraName );
		if( i == cameraNameMap.end() )
		{
			LogMessage( (std::string) "internal error:  inconsistency in camera name map (" + cameraName + ")", false );
			return DEVICE_INTERNAL_INCONSISTENCY;
		}
		pProp->Set( (*i).second.c_str() );
	}
	return DEVICE_OK;
}


int CGigECamera::OnGain( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	int nRet = DEVICE_OK;
	if( eAct == MM::AfterSet )
	{
		if( nodes->isAvailable( GAIN ) )
		{
			double oldd = 0, newd = 0;
			nodes->get( oldd, GAIN );
			pProp->Get( newd );
			bool retval = nodes->set( newd, GAIN );
			if( retval == false )
			{
				pProp->Set( oldd );
				nRet = DEVICE_INVALID_PROPERTY_VALUE;
			}
		}
		else if( nodes->isAvailable( GAIN_RAW ) )
		{
			int64_t oldd = 0;
			long newd = 0;
			nodes->get( oldd, GAIN_RAW );
			pProp->Get( newd );
			bool retval = nodes->set( newd, GAIN_RAW );
			if( retval == false )
			{
				pProp->Set( (long) oldd );
				nRet = DEVICE_INVALID_PROPERTY_VALUE;
			}
		}
	}
	else if( eAct == MM::BeforeGet )
	{
		if( nodes->isAvailable( GAIN ) )
		{
			double d = 0;
			if( nodes->get( d, GAIN ) )
				pProp->Set( d );
		}
		else if( nodes->isAvailable( GAIN_RAW ) )
		{
			int64_t d;
			if( nodes->get( d, GAIN_RAW ) )
				pProp->Set( (long) d );
		}
	}
	return nRet;
}


int CGigECamera::OnExposure( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	// note that GenICam units of exposure are us; umanager uses ms
	int nRet = DEVICE_OK;
	if( eAct == MM::AfterSet )
	{
		if( nodes->isAvailable( EXPOSURE_TIME ) )
		{
			double oldd = 0, newd = 0;
			nodes->get( oldd, EXPOSURE_TIME ); // us
			pProp->Get( newd );  // ms
			bool retval = nodes->set( newd * 1000.0, EXPOSURE_TIME );  // ms to us
			if( retval == false )
			{
				pProp->Set( oldd / 1000.0 );  // us to ms
				nRet = DEVICE_INVALID_PROPERTY_VALUE;
			}
		}
		else if( nodes->isAvailable( EXPOSURE_TIME_ABS ) )
		{
			double oldd = 0;
			double newd = 0;
			nodes->get( oldd, EXPOSURE_TIME_ABS );  // us
			pProp->Get( newd );  // ms
			bool retval = nodes->set( newd * 1000.0, EXPOSURE_TIME_ABS );  // ms to us
			if( retval == false )
			{
				pProp->Set( oldd / 1000.0 );  // us to ms
				nRet = DEVICE_INVALID_PROPERTY_VALUE;
			}
		}
		else if( nodes->isAvailable( EXPOSURE_TIME_ABS_INT ) )
		{
			int64_t oldd = 0;
			double newd = 0;
			nodes->get( oldd, EXPOSURE_TIME_ABS_INT );  // us
			pProp->Get( newd );  // ms
			bool retval = nodes->set( (long) (newd * 1000.0), EXPOSURE_TIME_ABS_INT );  // ms to us
			if( retval == false )
			{
				pProp->Set( oldd / 1000.0 );  // us to ms
				nRet = DEVICE_INVALID_PROPERTY_VALUE;
			}
		}
	}
	else if( eAct == MM::BeforeGet )
	{
		if( nodes->isAvailable( EXPOSURE_TIME ) )
		{
			double d = 0;
			if( nodes->get( d, EXPOSURE_TIME ) )
				pProp->Set( d / 1000.0 );
		}
		else if( nodes->isAvailable( EXPOSURE_TIME_ABS ) )
		{
			double d = 0;
			if( nodes->get( d, EXPOSURE_TIME_ABS ) )
				pProp->Set( d / 1000.0 );
		}
		else if( nodes->isAvailable( EXPOSURE_TIME_ABS_INT ) )
		{
			int64_t d = 0;
			if( nodes->get( d, EXPOSURE_TIME_ABS_INT ) )
				pProp->Set( d / 1000.0 );
		}

	}
	return nRet;
}


void CGigECamera::UpdateExposureRange()
{
	if( useExposureTime )
	{
		double lowUs, highUs;
		nodes->getMin( EXPOSURE_TIME, lowUs );
		nodes->getMax( EXPOSURE_TIME, highUs );
		SetPropertyLimits( MM::g_Keyword_Exposure, lowUs / 1000.0, highUs / 1000.0 );
		OnPropertiesChanged();
	}
	else if( useExposureTimeAbs )
	{
		double lowUs, highUs;
		nodes->getMax( EXPOSURE_TIME_ABS, lowUs );
		nodes->getMin( EXPOSURE_TIME_ABS, highUs );
		SetPropertyLimits( MM::g_Keyword_Exposure, lowUs / 1000.0, highUs / 1000.0 );
		OnPropertiesChanged();
	}
	else if( useExposureTimeAbsInt )
	{
		int64_t lowUs, highUs;
		nodes->getMax( EXPOSURE_TIME_ABS_INT, lowUs );
		nodes->getMin( EXPOSURE_TIME_ABS_INT, highUs );
		SetPropertyLimits( MM::g_Keyword_Exposure, static_cast<double>( lowUs ) / 1000.0, static_cast<double>( highUs ) / 1000.0 );
		OnPropertiesChanged();
	}
}


int CGigECamera::OnTemperature( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	int nRet = DEVICE_OK;
	if( eAct == MM::BeforeGet )
	{
		double d = 0;
		if( nodes->get( d, TEMPERATURE ) )
			pProp->Set( d );
	}
	else if( eAct == MM::AfterSet )
	{
		double d = 0;
		if( pProp->Get( d ) )
		{
			if( !nodes->set( d, TEMPERATURE ) )
			{
				nodes->get( d, TEMPERATURE );
				pProp->Set( d );
				nRet = DEVICE_CAN_NOT_SET_PROPERTY;
			}
		}
		else
			nRet = DEVICE_ERR;
	}

	return nRet;
}


int CGigECamera::OnFrameRate( MM::PropertyBase* pProp, MM::ActionType eAct )
{
	int nRet = DEVICE_OK;

	if( eAct == MM::BeforeGet )
	{
		if( nodes->isAvailable( ACQUISITION_FRAME_RATE ) )
		{
			double d = 0;
			if( nodes->get( d, ACQUISITION_FRAME_RATE ) )
			{
				pProp->Set( d );
			}

		}
		else if( nodes->isAvailable( ACQUISITION_FRAME_RATE_STR ) )
		{
			std::string name;
			if( nodes->get( name, ACQUISITION_FRAME_RATE_STR ) )
			{
				std::map<std::string,std::string>::iterator i = frameRateMap.find( name );
				if( i == frameRateMap.end() )
				{
					LogMessage( (std::string) "internal error:  inconsistency in frame rate name map (" + name + ")", false );
					return DEVICE_INTERNAL_INCONSISTENCY;
				}
				pProp->Set( i->second.c_str() );
			}
		}
	}
	else if( eAct == MM::AfterSet )
	{
		if( nodes->isAvailable( ACQUISITION_FRAME_RATE ) )
		{
			double d = 0, oldf = 0;
			pProp->Get( d );
			nodes->get( oldf, ACQUISITION_FRAME_RATE );
			if( !nodes->set( d, ACQUISITION_FRAME_RATE ) )
			{
				pProp->Set( oldf );
				nRet = DEVICE_INVALID_PROPERTY_VALUE;
			}
			this->UpdateExposureRange();
		}
		else if( nodes->isAvailable( ACQUISITION_FRAME_RATE_STR ) )
		{
			std::string oldName, newDisplayName;
			nodes->get( oldName, ACQUISITION_FRAME_RATE_STR );
			pProp->Get( newDisplayName );
			std::map<std::string,std::string>::iterator i = frameRateMap.find( newDisplayName );
			if( i == frameRateMap.end() )
			{
				LogMessage( (std::string) "internal error:  inconsistency in frame rate name map (" + newDisplayName + ")", false );
				nRet = DEVICE_INTERNAL_INCONSISTENCY;
			}
			if( !nodes->set( i->second, ACQUISITION_FRAME_RATE_STR ) )
			{
				std::map<std::string,std::string>::iterator i = frameRateMap.find( oldName );
				if( i != frameRateMap.end() )
					pProp->Set( i->second.c_str() );
				nRet = DEVICE_INVALID_PROPERTY_VALUE;
			}
			this->UpdateExposureRange();
		}
	}

	return nRet;
}

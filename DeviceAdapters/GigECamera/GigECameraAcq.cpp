///////////////////////////////////////////////////////////////////////////////
// FILE:          GigECameraAcq.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   An adapter for Gigbit-Ethernet cameras using an
//				  SDK from JAI, Inc.  Users and developers will 
//				  need to download and install the JAI SDK and control tool.
//                
// AUTHOR:        David Marshburn, UNC-CH, marshbur@cs.unc.edu, Jan. 2011
//

#include "GigECamera.h"

#include "boost/lexical_cast.hpp"


// this is/should be called only from JAI-started threads for image acquisition
void CGigECamera::SnapImageCallback( J_tIMAGE_INFO* imageInfo )
{
	//LogMessage( (std::string) "SnapImageCallback:  " 
	//			+ boost::lexical_cast<std::string>( (int) J_BitsPerPixel( imageInfo->iPixelType ) ) + " bits/pixel, " 
	//			+ boost::lexical_cast<std::string>( imageInfo->iSizeX ) + "w x "
	//			+ boost::lexical_cast<std::string>( imageInfo->iSizeY ) + "h" );
	
	if( !( doContinuousAcquisition || snapOneImageOnly ) ) return; // no acquisition requested

	size_t numBytes = J_BitsPerPixel( imageInfo->iPixelType ) * imageInfo->iSizeX * imageInfo->iSizeY / 8;

	if( this->doContinuousAcquisition )
	{
		if( buffer == NULL ) return;
		memcpy( buffer, imageInfo->pImageBuffer, min( numBytes, bufferSizeBytes ) );

		int nRet;
		// process image
      /* NOTE: this is now done automatically in MMCore
		MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);
		if( ip != NULL )
		{
			nRet = ip->Process( buffer, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel() );
			if (nRet != DEVICE_OK)
			{
				LogMessage( (std::string) "process failure " );
			}
		}
      */

		// create metadata
		Metadata md;
		char label[MM::MaxStrLength];
		GetLabel(label);
		MetadataSingleTag mstStartTime( MM::g_Keyword_Metadata_StartTime, label, true );
		mstStartTime.SetValue( boost::lexical_cast< std::string >( imageInfo->iTimeStamp ).c_str() );
		md.SetTag( mstStartTime );

		nRet = GetCoreCallback()->InsertMultiChannel( this, buffer, 1,
			GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel(), &md );

		if( !stopOnOverflow_ && nRet == DEVICE_BUFFER_OVERFLOW )
		{
			// do not stop on overflow - just reset the buffer
			GetCoreCallback()->ClearImageBuffer( this );
			nRet = GetCoreCallback()->InsertMultiChannel( this, buffer, 1, 
				GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel(), &md );
		}
	} // end if doContinuousAcquisition
	else if( this->snapOneImageOnly )
	{
		unsigned char* pixels = img_.GetPixelsRW();
		memcpy( pixels, imageInfo->pImageBuffer, min( numBytes, img_.Width() * img_.Height() * img_.Depth() ) );
	}
	
	// in the case of snapImage-style acquisition, stop
	if( this->snapOneImageOnly || this->stopContinuousAcquisition )
	{
		J_STATUS_TYPE retval = J_Camera_ExecuteCommand( hCamera, "AcquisitionStop" ); 
		if( retval != J_ST_SUCCESS )
		{
			LogMessage( (std::string) "SnapImageCallback:  failed to stop acquisition" );
		}
		snapOneImageOnly = false;
		snapImageDone = true;

		doContinuousAcquisition = false;
		stopContinuousAcquisition = false;
		continuousAcquisitionDone = true;
	}
}


J_STATUS_TYPE CGigECamera::setupImaging( )
{
	J_STATUS_TYPE retval;

	int64_t w, h;
	nodes->get( h, HEIGHT );
	nodes->get( w, WIDTH );

	retval = J_Image_OpenStream( hCamera, 0,
								 reinterpret_cast<J_IMG_CALLBACK_OBJECT>(this),
								 reinterpret_cast<J_IMG_CALLBACK_FUNCTION>(&CGigECamera::SnapImageCallback), 
								 &hThread, (uint32_t) ( w * h * LARGEST_PIXEL_IN_BYTES ) );  
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( (std::string) "setupImaging failed to open the image stream" );
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}


int CGigECamera::StartSequenceAcquisition( long numImages, double interval_ms, bool stopOnOverflow )
{
	LogMessage( (std::string) "Started camera streaming with an interval of " 
				+ boost::lexical_cast<std::string>( interval_ms ) + " ms, for " 
				+ boost::lexical_cast<std::string>( numImages )  + " images." );
	if( doContinuousAcquisition ) return DEVICE_OK;
	if( IsCapturing() ) return DEVICE_CAMERA_BUSY_ACQUIRING;

	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK)
		return ret;

	// make sure the circular buffer is properly sized
	GetCoreCallback()->InitializeImageBuffer(GetNumberOfComponents(), 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());

	stopContinuousAcquisition = false;
	continuousAcquisitionDone = false;
	doContinuousAcquisition = true;
	stopOnOverflow_ = stopOnOverflow;

	setupImaging();
	J_STATUS_TYPE retval = J_Camera_ExecuteCommand( hCamera, "AcquisitionStart" );
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( (std::string) "SnapImage failed to start acquisition" );
		J_Image_CloseStream( hThread );
		return DEVICE_ERR;
	}

	return DEVICE_OK;
}


int CGigECamera::StopSequenceAcquisition()
{
	if( !doContinuousAcquisition ) return DEVICE_OK;
	LogMessage( "StopSequenceAcquisition", true );

	J_STATUS_TYPE retval;

	stopContinuousAcquisition = true;
	MM::MMTime startTime = GetCurrentMMTime();
	double exp = GetExposure();
	while( !continuousAcquisitionDone && !( GetCurrentMMTime() - startTime < MM::MMTime( 50 * exp * 1000 ) ) ) // x1000 scales ms to us.
	{ 
		CDeviceUtils::SleepMs( 1 );
	}
	if( !continuousAcquisitionDone ) // didn't stop in time
	{
		LogMessage( (std::string) "StopSequenceAcquisition stopped the acquisition early because the JAI factory didn't stop soon enough" );
		retval = J_Camera_ExecuteCommand( hCamera, "AcquisitionStop" ); 
		if( retval != J_ST_SUCCESS )
		{
			LogMessage( (std::string) "StopSequenceAcquisition failed to stop acquisition" );
		}
	}

	retval = J_Image_CloseStream( hThread );
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( (std::string) "StopSequenceAcquisition failed to close the image stream" );
		return DEVICE_ERR;
	}
	snapOneImageOnly = false;
	doContinuousAcquisition = false;
	continuousAcquisitionDone = false;
	stopContinuousAcquisition = false;
	return DEVICE_OK;
}


/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CGigECamera::SnapImage()
{
	if( snapOneImageOnly ) return DEVICE_OK;

	this->snapOneImageOnly = true;
	this->snapImageDone = false;

	setupImaging();
	
	J_STATUS_TYPE retval = J_Camera_ExecuteCommand( hCamera, "AcquisitionStart" );
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( (std::string) "SnapImage failed to start acquisition" );
		J_Image_CloseStream( hThread );
		return DEVICE_ERR;
	}
	else // retval == J_ST_SUCCESS
	{
		MM::MMTime startTime = GetCurrentMMTime();
		double exp = GetExposure();
		// wait until the image is acquired (or 20x the expected exposure time has passed)
		while( !snapImageDone && ( GetCurrentMMTime() - startTime < MM::MMTime( 20 * exp * 1000 ) ) ) // x1000 to scale ms -> us
		{
			CDeviceUtils::SleepMs( 1 );
		}
		if( !snapImageDone ) // something happened and we didn't get an image
		{
			LogMessage( (std::string) "SnapImage stopped the acquisition because no image had been returned after a long while" );
			retval = J_Camera_ExecuteCommand( hCamera, "AcquisitionStop" ); 
			if( retval != J_ST_SUCCESS )
			{
				LogMessage( (std::string) "SnapImage failed to stop acquisition" );
			}
			snapOneImageOnly = false;
		}
	}

	retval = J_Image_CloseStream( hThread );
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( (std::string) "SnapImage failed to close the image stream" );
		return DEVICE_ERR;
	}
	

	readoutStartTime_ = GetCurrentMMTime();

	return DEVICE_OK;
}

bool CGigECamera::IsCapturing()
{
   return doContinuousAcquisition;
}
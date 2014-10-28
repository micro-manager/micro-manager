///////////////////////////////////////////////////////////////////////////////
// FILE:          GigECameraAcq.cpp
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

#include "boost/lexical_cast.hpp"


// this is/should be called only from JAI-started threads for image acquisition
void CGigECamera::SnapImageCallback( J_tIMAGE_INFO* imageInfo )
{
	// Skip logging for sequence acquisition
	if( snapOneImageOnly )
	{
		LogMessage( "SnapImageCallback:  "
					+ boost::lexical_cast<std::string>( (int) J_BitsPerPixel( imageInfo->iPixelType ) ) + " bits/pixel, " 
					+ boost::lexical_cast<std::string>( imageInfo->iSizeX ) + "w x "
					+ boost::lexical_cast<std::string>( imageInfo->iSizeY ) + "h", true );
	}
	
	if( !( doContinuousAcquisition || snapOneImageOnly ) ) return; // no acquisition requested


	if( this->doContinuousAcquisition )
	{
		if( buffer_ == NULL )
		{
			LogMessage( "RingBuffer not initialized");
			return;
		}

		// do the actual image aquisition: copy image buffer
		if (DEVICE_OK != aquireImage(imageInfo,buffer_))
		{
			LogMessage( "SnapImageCallback encountered a problem, could not aquire image buffer from device");
			return;
		}

		int nRet;

		// create metadata
		Metadata md;
		char label[MM::MaxStrLength];
		GetLabel(label);
		MetadataSingleTag mstStartTime( MM::g_Keyword_Metadata_StartTime, label, true );
		mstStartTime.SetValue( boost::lexical_cast< std::string >( imageInfo->iTimeStamp ).c_str() );
		md.SetTag( mstStartTime );

		nRet = GetCoreCallback()->InsertImage(this, buffer_,
				GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel(), md.Serialize().c_str());

		if( !stopOnOverflow_ && nRet == DEVICE_BUFFER_OVERFLOW )
		{
			// do not stop on overflow - just reset the buffer
			GetCoreCallback()->ClearImageBuffer( this );
			nRet = GetCoreCallback()->InsertImage(this, buffer_,
				GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel(), md.Serialize().c_str());
		}
	} // end if doContinuousAcquisition
	else if( this->snapOneImageOnly )
	{
		uint8_t* pixels = const_cast<uint8_t*>(img_.GetPixelsRW());

		LogMessage( "SnapImageCallback:  img_.bitDepth " + boost::lexical_cast<std::string>(img_.Depth() ) );

		// do the actual image aquisition: copy image buffer
		if (DEVICE_OK != aquireImage(imageInfo,pixels))
		{
			LogMessage( "SnapImageCallback encountered a problem");
			return;
		}
	}
	
	// in the case of snapImage-style acquisition, stop
	if( this->snapOneImageOnly || this->stopContinuousAcquisition )
	{
		J_STATUS_TYPE retval = J_Camera_ExecuteCommand( hCamera, cstr2jai( "AcquisitionStop" ) ); 
		if( retval != J_ST_SUCCESS )
		{
			LogMessage( "SnapImageCallback:  failed to stop acquisition" );
		}
		snapOneImageOnly = false;
		snapImageDone = true;

		doContinuousAcquisition = false;
		stopContinuousAcquisition = false;
		continuousAcquisitionDone = true;
	}
}
int CGigECamera::aquireImage(J_tIMAGE_INFO* imageInfo, uint8_t *buffer)
{
	J_tIMAGE_INFO BufferInfo;
	uint32_t img_buffer_size = img_.Width() * img_.Height() * img_.Depth();

	if ( color_ )
	{
		// Allocate the buffer to hold converted the image
		if ( J_ST_SUCCESS == J_Image_MallocDIB(imageInfo, &BufferInfo) )
		{
			// Convert the raw image to image format
			if ( J_ST_SUCCESS == J_Image_FromRawToDIBEx(imageInfo, &BufferInfo, BAYER_EXTEND) )
			{
				LogMessage( "aquireImage:  try to copy DIB RGB image, BufferInfo " + boost::lexical_cast<std::string>(BufferInfo.iImageSize) +
					" mm img buffer size " +  boost::lexical_cast<std::string>(img_buffer_size) );

				memcpy( buffer, BufferInfo.pImageBuffer, min( img_buffer_size, BufferInfo.iImageSize ) );
			}
			else
			{
				LogMessage( "J_Image_FromRawToImageEx() failed", true );
				return DEVICE_ERR;
			}

			J_Image_Free(&BufferInfo);
		}
		else
		{
			LogMessage( "J_Image_MallocDIB() failed", true );
			return DEVICE_ERR;
		}
	}
	else
	{
		// Allocates buffer memory for RGB image.
		if (J_ST_SUCCESS == J_Image_Malloc(imageInfo, &BufferInfo))
		{
			// Converts from RAW to full bit image.
			if(J_ST_SUCCESS == J_Image_FromRawToImageEx(imageInfo,&BufferInfo,BAYER_EXTEND))
			{
				J_tIMAGE_INFO YBufferInfo;

				// If the image is already in 8-bit or 16-bit mono,
				// J_Image_MallocEx will fail. In this case, we already have
				// the desired image.
				if (BufferInfo.iPixelType == J_GVSP_PIX_MONO8 ||
						BufferInfo.iPixelType == J_GVSP_PIX_MONO16)
				{
					LogMessage( "aquireImage: copying Y image", true );
					memcpy( buffer, BufferInfo.pImageBuffer, min( img_buffer_size, BufferInfo.iImageSize ) );
				}
				else
				{
					// Allocates buffer memory for Y image.
					if (J_ST_SUCCESS == J_Image_MallocEx(&BufferInfo, &YBufferInfo, PF_Y))
					{
						// Converts from RGB to Y.
						if (J_ST_SUCCESS == J_Image_ConvertImage(&BufferInfo, &YBufferInfo, PF_Y))
						{
							LogMessage( "aquireImage: copying converted Y image", true );
							memcpy( buffer, YBufferInfo.pImageBuffer, min( img_buffer_size, YBufferInfo.iImageSize ) );
						}
						else
						{
							LogMessage( "J_Image_ConvertImage() failed", true );
							return DEVICE_ERR;
						}

						J_Image_Free(&YBufferInfo);
					}
					else
					{
						LogMessage( "J_Image_MallocEx() failed", true );
						return DEVICE_ERR;
					}
				}
			}
			else
			{
				LogMessage( "J_Image_FromRawToImageEx() failed", true );
				return DEVICE_ERR;
			}
			J_Image_Free(&BufferInfo);
		}
		else
		{
			LogMessage( "J_Image_Malloc() failed", true );
			return DEVICE_ERR;
		}
	}


	return DEVICE_OK;
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
		LogMessage( "setupImaging failed to open the image stream" );
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}


int CGigECamera::StartSequenceAcquisition( long numImages, double interval_ms, bool stopOnOverflow )
{
	LogMessage( "Started camera streaming with an interval of "
				+ boost::lexical_cast<std::string>( interval_ms ) + " ms, for " 
				+ boost::lexical_cast<std::string>( numImages )  + " images." );
	if( doContinuousAcquisition ) return DEVICE_OK;
	if( IsCapturing() ) return DEVICE_CAMERA_BUSY_ACQUIRING;

	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK)
		return ret;

	// make sure the circular buffer is properly sized
	GetCoreCallback()->InitializeImageBuffer(1, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());


	stopContinuousAcquisition = false;
	continuousAcquisitionDone = false;
	doContinuousAcquisition = true;
	stopOnOverflow_ = stopOnOverflow;

	setupImaging();
	J_STATUS_TYPE retval = J_Camera_ExecuteCommand( hCamera, cstr2jai( "AcquisitionStart" ) );
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( "Failed to start acquisition" );
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
		LogMessage( "StopSequenceAcquisition stopped the acquisition early because the JAI factory didn't stop soon enough" );
		retval = J_Camera_ExecuteCommand( hCamera, cstr2jai( "AcquisitionStop" ) ); 
		if( retval != J_ST_SUCCESS )
		{
			LogMessage( "StopSequenceAcquisition failed to stop acquisition" );
		}
	}

	retval = J_Image_CloseStream( hThread );
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( "StopSequenceAcquisition failed to close the image stream" );
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
	
	J_STATUS_TYPE retval = J_Camera_ExecuteCommand( hCamera, cstr2jai( "AcquisitionStart" ) );
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( "SnapImage failed to start acquisition" );
		J_Image_CloseStream( hThread );
		return DEVICE_ERR;
	}
	else // retval == J_ST_SUCCESS
	{
		MM::MMTime startTime = GetCurrentMMTime();

		double waitTimeMs = 20.0 * GetExposure();
		const double minWaitTimeMs = 200.0;
		if( waitTimeMs < minWaitTimeMs )
		{
			waitTimeMs = minWaitTimeMs;
		}
		LogMessage( "SnapImage: acquisition started; waiting with timeout of " + boost::lexical_cast<std::string>(waitTimeMs) + " ms" );

		// wait until the image is acquired or 20x the exposure time (min. 100 ms) has past
		MM::MMTime deadline = startTime + MM::MMTime( waitTimeMs * 1000.0 );
		while( !snapImageDone && ( GetCurrentMMTime() < deadline ) ) // x1000 to scale ms -> us
		{
			CDeviceUtils::SleepMs( 1 );
		}
		if( !snapImageDone ) // something happened and we didn't get an image
		{
			LogMessage( "SnapImage stopped the acquisition because no image had been returned after " + boost::lexical_cast<std::string>(waitTimeMs) + " ms" );
			retval = J_Camera_ExecuteCommand( hCamera, cstr2jai( "AcquisitionStop" ) ); 
			if( retval != J_ST_SUCCESS )
			{
				LogMessage( "SnapImage failed to stop acquisition" );
			}
			snapOneImageOnly = false;
		}
	}

	retval = J_Image_CloseStream( hThread );
	if( retval != J_ST_SUCCESS )
	{
		LogMessage( "SnapImage failed to close the image stream" );
		return DEVICE_ERR;
	}
	

	readoutStartTime_ = GetCurrentMMTime();

	return DEVICE_OK;
}

bool CGigECamera::IsCapturing()
{
   return doContinuousAcquisition;
}

///////////////////////////////////////////////////////////////////////////////
// FILE:          TwainDevice.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   implementation classes for Twain Camera
//                
// COPYRIGHT:     University of California, San Francisco, 2009
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

#include <windows.h>

#include "TWAIN.H" //2.0
#include "TwainDevice.h"

#include <iostream>
#include <ostream>
#include <strstream>
#include <sstream>
#include <map>
#include <memory>

#include "dbgbox.h"
//#include "Probe.h"
#include "PerformanceTimer.h"
#include "TwainSpecUtilities.h"
#include "../../MMDevice/DeviceBase.h"
#include "TwainCamera.h"
#include "CommonTwain.h"

#include "SaveBmpImage.h"  // write file directly from OS handle
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ModuleInterface.h"
//#include "../../MMCore/CoreUtils.h"

//#define mmdebugmessage(x) LogMessage((x), true)
#define UNREFERENCEDPARAMETER(p__) (p__)

extern HINSTANCE g_hinstDLL;
// define some 'container' types for the twain calls
typedef struct {
	TW_UINT16  ItemType;
	TW_FIX32  Item;
	} TW_ONEVALUEFIX32;



void 	OutputDebugFrame(const char* const pname, const TW_FRAME& f)
{
	std::ostringstream messs;
	messs << pname<<" top: " << static_cast<int>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Top));
	messs <<" l: " << static_cast<int>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Left));
	messs <<" r: " << static_cast<int>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Right));
	messs <<" b: " << static_cast<int>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Bottom));
	messs <<" w: " <<  static_cast<int>( 1.5 + TwainSpecUtilities::FIX32ToFloat(f.Right)-TwainSpecUtilities::FIX32ToFloat(f.Left));
	messs <<" h: "<< static_cast<int> (1.5 + TwainSpecUtilities::FIX32ToFloat(f.Bottom) - TwainSpecUtilities::FIX32ToFloat(f.Top)) << std::endl;
	OutputDebugString(messs.str().c_str());
};


enum TwainStates { PreSession = 1, ManagerLoaded = 2, ManagerOpened =3, SourceOpened = 4, SourceEnabled = 5, 
TransferReady = 6, Transferring = 7};


static TW_IDENTITY source_s;
static TwainStates twainState_s;
static MMThreadLock* pstateLock_s;
static bool closeDSRequest_s;

int imini( const int i0, const int i1, const int i2)
{
	return( min(min(i0, i1), i2));
}

int imini( const int i0, const int i1, const int i2, const int i3)
{
	return( min(imini(i0, i1, i2), i3));
}

// windows stuff
int GetPaletteSize(BITMAPINFOHEADER& bmInfo)
{
	switch(bmInfo.biBitCount)
	{
	case 1:
			return 2;
	case 4:
			return 16;
	case 8:
			return 256;
	default:
			return 0;
	}
}

// the worker thread

class WorkerThread : public MMDeviceThreadBase
{
   public:
      WorkerThread() : 
      busy_(false), stop_(false) {}
      ~WorkerThread() {}
      int svc (void);

      void Stop() {stop_ = true;}
      void Start() 
		{
			stop_ = false;
			activate();
		}
   private:
      bool busy_;
      bool stop_;
};


int WorkerThread::svc(void)
{
   return 0;
}

// improvement: move this to a class variable.
WorkerThread* pWorkThread_g = NULL;


class TwImpl
{
public:
	// the only ctor for the implementation class -
	// construct the twain device, open the data source manager, count = -1 signifies an arbitrary number of images
	// enumerate the available sources and select the default source and so forth
	TwImpl(TwainCamera* pcam__/*, std::map<long long, std::pair<uint16_t,uint16_t> > roiImageSizes__*/): pcamera_(pcam__), m_hTwainDLL(NULL), m_pDSMProc(NULL), m_libName("TWAINDSM.dll"), count_(1), pbuf_(NULL), width_(0), height_(0), 
		bytesppixel_(0), sizeofbuf_(0),sourceisopen_(false), dummyWindowHandle_(0), callbackValid_(false), exposureSettable_(false), exposureGettable_(false)/*, roiImageSizes_r(roiImageSizes__)*/
	{
		// nasty old 'C' structs.
		memset(&setROI_, 0, sizeof(setROI_));
		memset(&initialFrame_, 0, sizeof(setROI_));
		
      if( NULL == pstateLock_s)
         throw TwainBad("invalid lock object");

		MMThreadGuard g(*pstateLock_s);
		twainState_s = PreSession;

	};


	void StartTwain(void)
	{
		MMThreadGuard g(*pstateLock_s);
		bool reSelect = false;

		while( twainState_s <= ManagerOpened)

		{
			if ( twainState_s <= PreSession)
			{
				// start the Twain library
				m_hTwainDLL  = LoadLibraryA(m_libName.c_str());
				if(NULL == m_hTwainDLL) throw TwainBad("failed to load Twain library");
		
				// get the function
				m_pDSMProc = (DSMENTRYPROC)GetProcAddress(m_hTwainDLL,(LPCSTR)1);
				if (NULL == m_pDSMProc)
				{
					FreeLibrary(m_hTwainDLL);
					m_hTwainDLL = NULL;
					twainState_s = PreSession;
					throw TwainBad("Twain library is incorrect");
				}
				m_driverValid = true;
				// setup application's information for Twain
				// Expects all the fields in appId_ to be set except for the id field.
				appId_.Id = 0; // Initialize to 0 (Source Manager will assign real value)
				appId_.Version.MajorNum = 1; //Your app's version number
				appId_.Version.MinorNum = 5;
				appId_.Version.Language = TWLG_USA;
				appId_.Version.Country = TWCY_USA;
				strcpy (appId_.Version.Info, "1.5");
				appId_.ProtocolMajor = TWON_PROTOCOLMAJOR;
				appId_.ProtocolMinor = TWON_PROTOCOLMINOR;
				// DF_APP2 lets us use the memory callbacks, etc.
				appId_.SupportedGroups = DF_APP2 | DG_IMAGE | DG_CONTROL;
				strcpy (appId_.Manufacturer, "UCSF");
				strcpy (appId_.ProductFamily, "Generic");
				strcpy (appId_.ProductName, "microManager");

				twainState_s = ManagerLoaded; // library is started &  appID is defined
				reSelect = true;
				ostringstream mezzz;
				mezzz << __FILE__ << " " << __LINE__ << " TwImpl::StartTwain entered state " << twainState_s;
				DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);	

			}
			if( ManagerLoaded == twainState_s)
			{

				// open the datasource manager
				if(!TwainCall(&appId_,NULL,DG_CONTROL,DAT_PARENT,MSG_OPENDSM,(TW_MEMREF)NULL)) throw TwainBad("failed to open data source manager");

				// check for DSM2 support

				memset(&DSMEntryPoints_,0, sizeof(DSMEntryPoints_));
				DSMEntryPoints_.Size = sizeof(TW_ENTRYPOINT);

				// get the entry points
				TwainCall(&appId_,NULL,DG_CONTROL,DAT_ENTRYPOINT,MSG_GET,&DSMEntryPoints_); //throw TwainBad("get DSM entry points failed - need DSM 2 support");

				std::ostringstream messs;
				messs <<  "twimpl static callback function address is " << (void *)TwainCallback << std::endl;
				OutputDebugString(messs.str().c_str());

				//source dll should be able to accept 0 for the window handle, but several of them crash in that case
				if( NULL != dummyWindowHandle_)
					DestroyWindow(dummyWindowHandle_);
				dummyWindowHandle_ = CreateDummyWindow();
			
				twainState_s = ManagerOpened; // TWAIN manager is running and ready to select and open a source 
				ostringstream mezzz;
				mezzz << __FILE__ << " " << __LINE__ << " TwImpl::StartTwain entered state " << twainState_s;
				DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);	

			}

			if( ManagerOpened == twainState_s)
			{

				// enumerate available image sources
				TW_IDENTITY so;
				// get the first source
				availablesources_.clear();
				if(!TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_GETFIRST,&so)) throw TwainBad("no Twain sources are available");
				availablesources_.insert(std::pair<std::string,TW_IDENTITY>(so.ProductName, so));
				while(TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_GETNEXT,&so))
				{
					availablesources_.insert(std::pair<std::string,TW_IDENTITY>(so.ProductName, so));
				}

				if ( 0 < currentlySelectedSourceName_.length() )
				{
					std::map<std::string, TW_IDENTITY>::iterator ii = availablesources_.begin();
					while( ii!= availablesources_.end())
					{
						if (currentlySelectedSourceName_ == ii->first) 
						{
							source_ = ii->second;
							break;
						}
						++ii;
					}
				}
				else
				{
					// get the default source and write its parameters onto source_,
					// but client can still select a different source later
					memset(&source_, 0, sizeof(source_));
					if(!TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_GETDEFAULT,&source_)) throw TwainBad("failed to get default data source");
					ostringstream mezzz;
					mezzz << __FILE__ << " " << __LINE__ << " TwImpl::StartTwain re-selected the default source ";
					DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);	
				}

				// this will pop up a dialogue from inside Twain "TWAINDSM.dll" and allow you select the source
				//if( !TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_USERSELECT,&source_)) throw TwainBad("user selection of source failed");
				
				//std::string LeicaDS("Leica DFC280...DFC490");
				//if(proName == LeicaDS)
				//{

				//}


				break; // finished starting the Twain manager

			}
		}

	}

	char* GetImage(int& imheight, int& imwidth, char& bytesppixel)
	{

		bool retval = false;
		HANDLE hBitmap;
		TW_IMAGEINFO info;
		PerformanceTimer timer0;
		std::ostringstream  messs;
		PerformanceTimer delay;

		while( delay.elapsed() < 0.050)
		{
			{
				MMThreadGuard g(*pstateLock_s);
				if( twainState_s < TransferReady) break;
			}
			::Sleep(10);
		}
		
		{
			MMThreadGuard g(*pstateLock_s);
			if( twainState_s < TransferReady)
			{

				messs.str("");
				messs << "Image transfer failed: " << twainState_s << " source never signaled 'Transfer Ready' (waited " << delay.elapsed()*1000. <<" sec)"  << std::endl;
				throw TwainBad( messs.str().c_str());
			}
		}
		// retrieve the current image info
		PerformanceTimer timer1;	
		retval = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGEINFO, MSG_GET,(TW_MEMREF)&info);
		if(!retval) throw TwainBad("failed to get image info");
		
		messs.str("");
		messs << " time to get image info " << timer1.elapsed() << std::endl;
		OutputDebugString(messs.str().c_str());
		
		messs.str("");
		messs << " XRes: " <<  TwainSpecUtilities::FIX32ToFloat(info.XResolution);
		messs << " YRes: " <<  TwainSpecUtilities::FIX32ToFloat(info.YResolution);
		messs << " width: " <<  info.ImageWidth;
		messs << " length: " <<  info.ImageLength ;
		messs << " samples per pixel: " << info.SamplesPerPixel << std::endl;
		TW_UINT32 units;
		GetCapability(ICAP_UNITS, units);
		messs << " units: " << units << std::endl;
		if (1 < info.SamplesPerPixel)
		{
			messs << " Ch 0 (Red) bits: " << info.BitsPerSample[0];
			messs << " Ch 1 (Green) bits: " << info.BitsPerSample[1];
			messs << " Ch 2 (Blue) bits: " << info.BitsPerSample[2]<< std::endl;
			messs << " bitsperpix: " <<  info.BitsPerPixel<< std::endl;
			messs << " colors are grouped: " << (info.Planar?"planar":"chunky")<< std::endl;
			messs << " PixelType is: " << info.PixelType<< std::endl;
			messs << " compression scheme is: " << TwainSpecUtilities::TwainCompressionScheme(info.Compression) << std::endl;
		}	
		OutputDebugString(messs.str().c_str());
		

		// retrieve the current frame layout
		TW_IMAGELAYOUT layout;
		retval = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_GET,(TW_MEMREF)&layout);
		if(!retval) throw TwainBad("failed to get image info");

		OutputDebugFrame(" acquired image size: ", layout.Frame);
		messs.str("");
		messs << std::endl;
		messs << "doc # " << layout.DocumentNumber << " page # " << layout.PageNumber << " Frame # " << layout.FrameNumber << std::endl;
		OutputDebugString(messs.str().c_str());


		// the very first acquisition into this camera should be  the full captured region.
		TW_FRAME zeroFrame;
		memset((void*)&zeroFrame, 0, sizeof(zeroFrame));

		// save the original capture frame if it hasn't been saved yet.
		if( 0 == memcmp((void*)&(zeroFrame), (void*)&initialFrame_, sizeof(TW_FRAME)))
		{
			initialFrame_ = layout.Frame;
			OutputDebugFrame(" initial capture area is ", initialFrame_);

		}
		bool transfersPending = true;
		while(transfersPending)
		{
			// retrieve the current image data per se
			timer1.Reset();

			retval = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGENATIVEXFER, MSG_GET, &hBitmap);
			if(!retval)
			{
				twainState_s = SourceEnabled;
				throw TwainBad("failed image transfer");
			}

			bool directimagesave = false;
			if(directimagesave)
			{
				PBITMAPINFOHEADER pDIB = (PBITMAPINFOHEADER)TwainLockMemory(hBitmap);
				SaveBmpImage(pDIB);
				TwainUnlockMemory(hBitmap);
			}

			messs.str("");
			messs << " time to transfer image data " << timer1.elapsed() << std::endl;
			OutputDebugString(messs.str().c_str());
			UCHAR *lpVoid,*pBits;

			// LPBITMAPINFOHEADER is Windows specific .....
			LPBITMAPINFOHEADER pHead;

			lpVoid = (UCHAR *)GlobalLock(hBitmap);
			pHead = (LPBITMAPINFOHEADER )lpVoid;
			
			imwidth = pHead->biWidth;
			imheight = min(pHead->biHeight, info.ImageLength) ;
			bytesppixel = (char)(pHead->biBitCount/8);

   		// bytes per row  - aligned to 4 byte word
			int bytes = (pHead->biBitCount*pHead->biWidth)>>3;
			while(bytes%4) bytes++;

			if(pHead->biCompression != BI_RGB) 
			{
				GlobalUnlock(lpVoid);
				GlobalFree(hBitmap);
				throw TwainBad("unsupported image compression");
			}

			// currently only support 8 bit and 24 bit images
			if((pHead->biBitCount != 8 )&&(pHead->biBitCount != 16)&&(pHead->biBitCount != 24))
			{
					GlobalUnlock(lpVoid);
					GlobalFree(hBitmap);
					throw TwainBad("unsupported image depth");
			}

   		if(pHead->biWidth != info.ImageWidth)
			{
				messs.str("");
				messs << " image width from image native transfer is " << pHead->biWidth  << "  image info ImageWidth is " << info.ImageWidth << std::endl;
				OutputDebugString(messs.str().c_str());
			}

			pBits = lpVoid + sizeof(BITMAPINFOHEADER) + sizeof(RGBQUAD)*GetPaletteSize(*pHead);
			//if( 1 == bytesppixel) 
			//{
			//	pBits += 0xC77;  // there was a 'pad' of wrong data coming from Leica DFC340FX
			//	imheight -= (0xC77/bytes);
			//}
			timer1.Reset();
			if( imheight*bytes != sizeofbuf_)
			{
				if(NULL!=pbuf_)free(pbuf_);
				sizeofbuf_ = 0;
				pbuf_ = (char *) malloc( imheight*bytes);
				if (NULL!=pbuf_) sizeofbuf_ = imheight*bytes;
			}
			memset(pbuf_, 0, imheight*bytes);
			
			// Watch out here!
			// even though, for example, the rows are reported to be, say, 573 pixels * 3 bytes per pixel = 1719 bytes
			// each row will occupy 1720 bytes, for example

			// a future performance improvement would be either to allow microManager to work on 3 byte RGB images 
			// or else directly copy from the Twain array into the micromanager image buffer
			// but this is working fine as is so I'm leaving it alone for now.

			// TwainDevice::GetImage returns a compact array, so keep it that way
			char* praster = (char *)pBits;
			char* poutput = (char *)pbuf_;
			for(int yoff = 0; yoff < imheight; ++yoff)
			{
				memcpy(poutput, praster, imwidth*bytesppixel);
				poutput += imwidth*bytesppixel;
				praster += bytes;
			}
			//memcpy(pbuf_, pBits, imheight*bytes);
			GlobalUnlock(lpVoid);
			GlobalFree(hBitmap);

			TW_PENDINGXFERS pendxfers;
			memset( &pendxfers, 0, sizeof(pendxfers) );
			bool ret = TwainCall(&appId_, &source_, DG_CONTROL, DAT_PENDINGXFERS, MSG_ENDXFER, (TW_MEMREF)&pendxfers);
			if(!ret) break;
		   if(0 == pendxfers.Count)
			{
				// nothing left to transfer, finished.
				transfersPending = false;
			}
			else
			{
				messs.str("");

				messs<<  pendxfers.Count << " pending transfers! " << endl;
				OutputDebugString(messs.str().c_str());

			}
		
		}
		// in case of an error, reset the transfers
		if (transfersPending)
		{
			TW_PENDINGXFERS pendxfers;
			memset( &pendxfers, 0, sizeof(pendxfers) );
			bool ret = TwainCall(&appId_, &source_, DG_CONTROL, DAT_PENDINGXFERS, MSG_ENDXFER, (TW_MEMREF)&pendxfers);

			// We need to get rid of any pending transfers
			if(ret &&(0 != pendxfers.Count))
			{
				memset( &pendxfers, 0, sizeof(pendxfers) );
				TwainCall(&appId_, &source_, DG_CONTROL, DAT_PENDINGXFERS, MSG_RESET, (TW_MEMREF)&pendxfers);
			}
		}
		messs.str("");
		messs << " time copy data out of bitmap handle " << timer1.elapsed() << std::endl;
		OutputDebugString(messs.str().c_str());

		//EnableCamera(false);
		timer1.Reset();
		messs.str("");
		messs << " total time inside pTwimpl->GetImage " << timer0.elapsed() << std::endl;
		OutputDebugString(messs.str().c_str());
		
		twainState_s = SourceEnabled;

		return pbuf_;
	};


	//!!!! N.B. !!!! this consumes an image frame, but seems to be the only
	// way to guarantee to get the correct image size.
	void GetActualImageSize(uint16_t& imheight, uint16_t& imwidth, char& bytesppixel)
	{
		static bool once = false;
		bool retval = false;
		HANDLE hBitmap;

		if (!once)
		{
			once = false;
		}

		std::ostringstream  messs;
	
		PerformanceTimer delay;

		while( delay.elapsed() < 0.050)
		{
			{
				MMThreadGuard g(*pstateLock_s);
				if( twainState_s < TransferReady) break;
			}
			::Sleep(10);
		}
		
		{
			MMThreadGuard g(*pstateLock_s);
			if( twainState_s < TransferReady)
			{

				messs.str("");
				messs << "Image transfer failed: " << twainState_s << " source never signaled 'Transfer Ready' (waited " << delay.elapsed()*1000. <<" ms)"  << std::endl;
				throw TwainBad( messs.str().c_str());
			}
		}

		bool transfersPending = true;
		while(transfersPending)
		{
			retval = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGENATIVEXFER, MSG_GET, &hBitmap);
			if(!retval) throw TwainBad("failed image transfer");
			bool directimagesave = false;
			if(directimagesave)
			{
				PBITMAPINFOHEADER pDIB = (PBITMAPINFOHEADER)TwainLockMemory(hBitmap);
				SaveBmpImage(pDIB);
				TwainUnlockMemory(hBitmap);
			}

			UCHAR *lpVoid;
			// begin M.S. Windows specific .....
			LPBITMAPINFOHEADER pHead;
			lpVoid = (UCHAR *)GlobalLock(hBitmap);
			pHead = (LPBITMAPINFOHEADER )lpVoid;
		
			imwidth = static_cast<uint16_t>(pHead->biWidth);
			imheight = static_cast<uint16_t>(pHead->biHeight);
			bytesppixel = (char)(pHead->biBitCount/8);

   		// bytes per row  - aligned to 4 byte word
			int bytes = (pHead->biBitCount*pHead->biWidth)>>3;
			while(bytes%4) bytes++;

			if(pHead->biCompression != BI_RGB) 
			{
				GlobalUnlock(lpVoid);
				GlobalFree(hBitmap);
				throw TwainBad("unsupported image compression");
			}

			//todo support 16-bit monochrome!!!!

			// currently only support 8 bit and 24 bit images
			if((pHead->biBitCount != 8 )&&(pHead->biBitCount != 16)&&(pHead->biBitCount != 24))
			{
					GlobalUnlock(lpVoid);
					GlobalFree(hBitmap);
					throw TwainBad("unsupported image depth");
			}

			GlobalUnlock(lpVoid);
			GlobalFree(hBitmap);

			TW_PENDINGXFERS pendxfers;
			memset( &pendxfers, 0, sizeof(pendxfers) );
			bool ret = TwainCall(&appId_, &source_, DG_CONTROL, DAT_PENDINGXFERS, MSG_ENDXFER, (TW_MEMREF)&pendxfers);
			if(!ret) break;
		   if(0 == pendxfers.Count)
			{
				// nothing left to transfer, finished.
				transfersPending = false;
			}
			else
			{
				messs.str("");
				messs<<  pendxfers.Count << " pending transfers! " << endl;
				OutputDebugString(messs.str().c_str());
			}
		}
		// in case of an error, reset the transfers
		if (transfersPending)
		{
			TW_PENDINGXFERS pendxfers;
			memset( &pendxfers, 0, sizeof(pendxfers) );
			bool ret  = TwainCall(&appId_, &source_, DG_CONTROL, DAT_PENDINGXFERS, MSG_ENDXFER, (TW_MEMREF)&pendxfers);

			// We need to get rid of any pending transfers
			if(ret &&(0 != pendxfers.Count))
			{
				memset( &pendxfers, 0, sizeof(pendxfers) );
				TwainCall(&appId_, &source_, DG_CONTROL, DAT_PENDINGXFERS, MSG_RESET, (TW_MEMREF)&pendxfers);
			}
		}
		

	};




	//bool SetCapability(TW_UINT16 cap, const void*const value, const int valuelength, TW_UINT16 tw_type)
	//{
	//	TW_CAPABILITY twCap;
	//	pTW_ONEVALUE pVal;
	//	bool ret_value = false;

	//	twCap.Cap = cap;
	//	twCap.ConType = TWON_ONEVALUE;
	//	
	//	twCap.hContainer = GlobalAlloc(GHND,sizeof(TW_ONEVALUE));
	//	if(twCap.hContainer)
	//	{
	//		pVal = (pTW_ONEVALUE)GlobalLock(twCap.hContainer);
	//		pVal->ItemType = tw_type;
	//		pVal->Item = (TW_UINT32)value;
	//		GlobalUnlock(twCap.hContainer);
	//		ret_value = SetCapability(twCap);
	//		GlobalFree(twCap.hContainer);
	//	}
	//	return ret_value;
	//};

	



	//////////////////////////////////////////////////////////////////////////////
	bool set_CapabilityOneValue(TW_UINT16 Cap, const TW_FRAME value)
	{
	  bool twrc = false;
	  TW_CAPABILITY   cap;

	  cap.Cap         = Cap;
	  cap.ConType     = TWON_ONEVALUE;
	  cap.hContainer  = TwainAlloc(sizeof(TW_ONEVALUE_FRAME));
	  if(0 == cap.hContainer) throw TwainBad("set cap. mem. allocation failure");

	  pTW_ONEVALUE_FRAME pVal = (pTW_ONEVALUE_FRAME)TwainLockMemory(cap.hContainer);

	  pVal->ItemType = TWTY_FRAME;
	  pVal->Item     = value;

	  // capability structure is set, make the call to the source now
	  twrc = TwainCall( &appId_, &source_, DG_CONTROL, DAT_CAPABILITY, MSG_SET, (TW_MEMREF)&(cap));

	  TwainUnlockMemory(cap.hContainer);
	  TwainFree(cap.hContainer);

	  return twrc;
	}



	//////////////////////////////////////////////////////////////////////////////
	bool  set_CapabilityOneValue(TW_UINT16 Cap, const TW_FIX32 _value)
	{

	  TW_CAPABILITY   cap;
	  bool ret = false;

	  cap.Cap         = Cap;
	  cap.ConType     = TWON_ONEVALUE;
	  cap.hContainer  = TwainAlloc(sizeof(TW_ONEVALUE_FIX32));
	  if(0 == cap.hContainer) throw TwainBad("set cap. mem. allocation failure");

	  pTW_ONEVALUE_FIX32 pVal = (pTW_ONEVALUE_FIX32)TwainLockMemory(cap.hContainer);

	  pVal->ItemType = TWTY_FIX32;
	  pVal->Item     = _value;

	  // capability structure is set, make the call to the source now
	  ret = TwainCall(&appId_, &source_, DG_CONTROL, DAT_CAPABILITY, MSG_SET, (TW_MEMREF)&(cap));


	  TwainUnlockMemory(cap.hContainer);
	  TwainFree(cap.hContainer);
	  return ret;

	}



	bool SetCapability(TW_UINT16 cap, TW_UINT16 value, bool sign)
	{
		TW_CAPABILITY twCap;
		pTW_ONEVALUE pVal;
		bool ret_value = false;

		twCap.Cap = cap;
		twCap.ConType = TWON_ONEVALUE;
		std::auto_ptr<TW_ONEVALUE> handle( new TW_ONEVALUE);
		
		twCap.hContainer = handle.get();
		//twCap.hContainer = (void *) malloc(sizeof(TW_ONEVALUE));
		if(twCap.hContainer)
		{
			pVal = (pTW_ONEVALUE)(twCap.hContainer);
			pVal->ItemType = sign ? TWTY_INT16 : TWTY_UINT16;
			pVal->Item = (TW_UINT32)value;
			ret_value = SetCapability(twCap);
			//free(twCap.hContainer);
		}
		return ret_value;
	};

	
	//bool SetCapability GLOBALLOCK(TW_UINT16 cap, TW_UINT16 value, bool sign)
	//{
	//	TW_CAPABILITY twCap;
	//	pTW_ONEVALUE pVal;
	//	bool ret_value = false;

	//	twCap.Cap = cap;
	//	twCap.ConType = TWON_ONEVALUE;
	//	
	//	twCap.hContainer = GlobalAlloc(GHND,sizeof(TW_ONEVALUE));
	//	if(twCap.hContainer)
	//	{
	//		pVal = (pTW_ONEVALUE)GlobalLock(twCap.hContainer);
	//		pVal->ItemType = sign ? TWTY_INT16 : TWTY_UINT16;
	//		pVal->Item = (TW_UINT32)value;
	//		GlobalUnlock(twCap.hContainer);
	//		ret_value = SetCapability(twCap);
	//		GlobalFree(twCap.hContainer);
	//	}
	//	return ret_value;
	//};

	bool SetCapability(TW_CAPABILITY& cap)
	{
		return TwainCall(&appId_, &source_, DG_CONTROL, DAT_CAPABILITY, MSG_SET,(TW_MEMREF)&cap);
	};

	// get a single value capability
	bool GetCapability(TW_UINT16 cap, TW_UINT32& value)
	{
		TW_CAPABILITY twCap;
		bool ret = false;

		if(GetCapability(twCap, cap, TWON_ONEVALUE))
		{
			pTW_ONEVALUE pVal;
			pVal = (pTW_ONEVALUE )GlobalLock(twCap.hContainer);
			if(pVal)
			{
				value = pVal->Item;
				GlobalUnlock(pVal);
				GlobalFree(twCap.hContainer);
				ret = true;
			}
		}

		return ret;
	};

	bool ResetCapability(TW_UINT16 cap)
	{
		TW_CAPABILITY twCap;
		twCap.Cap = cap;
		twCap.ConType = TWON_ONEVALUE;
		twCap.hContainer = NULL;
		return TwainCall(&appId_,&source_,DG_CONTROL,DAT_CAPABILITY,MSG_RESET,(TW_MEMREF)&twCap);
	};

	bool GetCapability(TW_CAPABILITY& twCap,TW_UINT16 cap, TW_UINT16 conType)
	{
		twCap.Cap = cap;
		twCap.ConType = conType;  // wrong - twain might return an enumeration container type
		twCap.hContainer = NULL;
		return TwainCall(&appId_,&source_,DG_CONTROL,DAT_CAPABILITY,MSG_GET,(TW_MEMREF)&twCap);
	}; 


	// following 'getcurrent' converters are from twain.org TWAIN_APP
	// useful if the returned container is an enumeration
	//////////////////////////////////////////////////////////////////////////////
	bool getcurrent(TW_CAPABILITY *pCap, TW_UINT32& val)
	{
	  bool bret = false;

	  if(0 != pCap->hContainer)
	  {
		 if(TWON_ENUMERATION == pCap->ConType)
		 {
			pTW_ENUMERATION pCapPT = (pTW_ENUMERATION)TwainLockMemory(pCap->hContainer);
			switch(pCapPT->ItemType)
			{
			case TWTY_INT32:
			  val = (TW_INT32)((pTW_INT32)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
			  bret = true;
			  break;

			case TWTY_UINT32:
			  val = (TW_INT32)((pTW_UINT32)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
			  bret = true;
			  break;

			case TWTY_INT16:
			  val = (TW_INT32)((pTW_INT16)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
			  bret = true;
			  break;

			case TWTY_UINT16:
			  val = (TW_INT32)((pTW_UINT16)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
			  bret = true;
			  break;

			case TWTY_INT8:
			  val = (TW_INT32)((pTW_INT8)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
			  bret = true;
			  break;

			case TWTY_UINT8:
			  val = (TW_INT32)((pTW_UINT8)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
			  bret = true;
			  break;

			case TWTY_BOOL:
			  val = (TW_INT32)((pTW_BOOL)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
			  bret = true;
			  break;

			}
			TwainUnlockMemory(pCap->hContainer);
		 }
		 else if(TWON_ONEVALUE == pCap->ConType)
		 {
			pTW_ONEVALUE pCapPT = (pTW_ONEVALUE)TwainLockMemory(pCap->hContainer);
			val = pCapPT->Item;
			bret = true;
			TwainUnlockMemory(pCap->hContainer);
		 }
	  }

	  return bret;
	};


	//////////////////////////////////////////////////////////////////////////////
	bool getcurrent(TW_CAPABILITY *pCap, std::string& val)
	{
	  bool bret = false;

	  if(0 != pCap->hContainer)
	  {
		 if(TWON_ENUMERATION == pCap->ConType)
		 {
			pTW_ENUMERATION pCapPT = (pTW_ENUMERATION)TwainLockMemory(pCap->hContainer);
			switch(pCapPT->ItemType)
			{
			case TWTY_STR32:
			  {
				 pTW_STR32 pStr = &((pTW_STR32)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
				 if(32 < strlen(pStr))
					pStr[32] = 0;
				 val = pStr;
				 bret = true;
			  }
			  break;

			case TWTY_STR64:
			  {
				 pTW_STR64 pStr = &((pTW_STR64)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
				 if(64 < strlen(pStr))
					pStr[64] = 0;
				 val = pStr;
				 bret = true;
			  }
			  break;

			case TWTY_STR128:
			  {
				 pTW_STR128 pStr = &((pTW_STR128)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
				 if(128 < strlen(pStr))
					pStr[128] = 0;
				 val = pStr;
				 bret = true;
			  }
			  break;

			case TWTY_STR255:
			  {
				 pTW_STR255 pStr = &((pTW_STR255)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
				 if(255 < strlen(pStr))
					pStr[255] = 0;
				 val = pStr;
				 bret = true;
			  }
			  break;
			}
			TwainUnlockMemory(pCap->hContainer);
		 }
		 else if(TWON_ONEVALUE == pCap->ConType)
		 {
			pTW_ONEVALUE pCapPT = (pTW_ONEVALUE)TwainLockMemory(pCap->hContainer);

			switch(pCapPT->ItemType)
			{
			case TWTY_STR32:
			  {
				 pTW_STR32 pStr = ((pTW_STR32)(&pCapPT->Item));
				 if(32 < strlen(pStr))
					pStr[32] = 0;
				 val = pStr;
				 bret = true;
			  }
			  break;

			case TWTY_STR64:
			  {
				 pTW_STR64 pStr = ((pTW_STR64)(&pCapPT->Item));
				 if(64 < strlen(pStr))
					pStr[64] = 0;
				 val = pStr;
				 bret = true;
			  }
			  break;

			case TWTY_STR128:
			  {
				 pTW_STR128 pStr = ((pTW_STR128)(&pCapPT->Item));
				 if(128 < strlen(pStr))
					pStr[128] = 0;
				 val = pStr;
				 bret = true;
			  }
			  break;

			case TWTY_STR255:
			  {
				 pTW_STR255 pStr = ((pTW_STR255)(&pCapPT->Item));
				 if(255 < strlen(pStr))
					pStr[255] = 0;
				 val = pStr;
				 bret = true;
			  }
			  break;
			}
			TwainUnlockMemory(pCap->hContainer);
		 }
	  }

	  return bret;
	};
	//////////////////////////////////////////////////////////////////////////////

	//////////////////////////////////////////////////////////////////////////////
	bool getcurrent(TW_CAPABILITY *pCap, TW_FIX32& val)
	{
	  bool bret = false;

	  if(0 != pCap->hContainer)
	  {
		 if(TWON_ENUMERATION == pCap->ConType)
		 {
			pTW_ENUMERATION_FIX32 pCapPT = (pTW_ENUMERATION_FIX32)TwainLockMemory(pCap->hContainer);

			if(TWTY_FIX32 == pCapPT->ItemType)
			{
			  val = pCapPT->ItemList[pCapPT->CurrentIndex];
			  bret = true;
			}
			TwainUnlockMemory(pCap->hContainer);
		 }
		 else if(TWON_ONEVALUE == pCap->ConType)
		 {
			pTW_ONEVALUE_FIX32 pCapPT = (pTW_ONEVALUE_FIX32)TwainLockMemory(pCap->hContainer);
	      
			if(TWTY_FIX32 == pCapPT->ItemType)
			{
			  val = pCapPT->Item;
			  bret = true;
			}
			TwainUnlockMemory(pCap->hContainer);
		 }
	  }
	  
	  return bret;
	};

	//////////////////////////////////////////////////////////////////////////////
	bool getcurrent(TW_CAPABILITY *pCap, TW_FRAME& frame)
	{
	  bool bret = false;

	  if(0 != pCap->hContainer)
	  {
		 if(TWON_ENUMERATION == pCap->ConType)
		 {
			pTW_ENUMERATION_FRAME pCapPT = (pTW_ENUMERATION_FRAME)TwainLockMemory(pCap->hContainer);

			if(TWTY_FRAME == pCapPT->ItemType)
			{
			  frame = pCapPT->ItemList[pCapPT->CurrentIndex];
			  bret = true;
			}
			TwainUnlockMemory(pCap->hContainer);
		 }
		 else if(TWON_ONEVALUE == pCap->ConType)
		 {
			pTW_ONEVALUE_FRAME pCapPT = (pTW_ONEVALUE_FRAME)TwainLockMemory(pCap->hContainer);
	      
			if(TWTY_FRAME == pCapPT->ItemType)
			{
			  frame = pCapPT->Item;
			  bret = true;
			}
			TwainUnlockMemory(pCap->hContainer);
		 }
	  }
	  
	  return bret;
	};






	bool GetCapability_TWAIN(TW_CAPABILITY& _cap, void* pvalue, unsigned int valueSize, TW_UINT16 command = MSG_GET )
	{
		if(twainState_s < SourceOpened) throw TwainBad("You need to open a data source first.");
		memset(pvalue,0,valueSize);
		// Check if this capability structure has memory already alloc'd.
		// If it does, free that memory before the call else we'll have a memory
		// leak because the source allocates memory during a MSG_GET.
		if(0 != _cap.hContainer)
		{
			TwainFree(_cap.hContainer);
			_cap.hContainer = 0;
		}
		
		_cap.ConType = TWON_DONTCARE16;  // allow library to set whatever container type ??

		// capability structure is set, make the call to the source now
		bool ret = TwainCall(&appId_, &source_, DG_CONTROL, DAT_CAPABILITY, command, (TW_MEMREF)&_cap);

		// there is some trouble with FIX32 values,
		// for example the Leica DFC 420C reports "0.090" exposure time when it's GUI reports 8.8 ms
		// and furthermore the twain.org TWAIN_APP reports this number as "0.90"

		// extract the value

		if( _cap.ConType == TWON_ENUMERATION || _cap.ConType == TWON_ONEVALUE )
			{
				pTW_ONEVALUE pCap = (pTW_ONEVALUE)TwainLockMemory(_cap.hContainer);
				if(pCap)
				{
					TW_UINT16 type = pCap->ItemType;
					TwainUnlockMemory(_cap.hContainer);
					switch(type)
					{
					  case TWTY_INT8:
					  case TWTY_UINT8:
					  case TWTY_INT16:
					  case TWTY_UINT16:
					  case TWTY_INT32:
					  case TWTY_UINT32:
					  case TWTY_BOOL:
					  {
						 TW_UINT32 uVal;
						 getcurrent(&_cap, uVal);
						 *(TW_UINT32*)pvalue = uVal; // TODO correct this to work with
						 break;
					  }

					  case TWTY_STR32:
					  case TWTY_STR64:
					  case TWTY_STR128:
					  case TWTY_STR255:
					  {
						 std::string sVal;
						 getcurrent(&_cap, sVal);
						 int len1 = min( valueSize, strlen(sVal.c_str()));
						 strncpy((char*)pvalue, sVal.c_str(), len1);
						 break;
					  }

					  case TWTY_FIX32:
					  {
						 TW_FIX32 fix32;
						 getcurrent(&_cap, fix32);
						 *(TW_FIX32*)pvalue = fix32;
						 break;
					  }

					  case TWTY_FRAME:
					  {
						 TW_FRAME frame;
						 getcurrent(&_cap, frame);
						 memcpy( pvalue, &frame, valueSize);
						 break;
					  }
					  default:
					  {
						 strncpy((char*)pvalue, "?", 1);
						 break;
					  }
					}// switch
				}// if(pCap)
				
		}// if... container type
		if(0 != _cap.hContainer)
		{
			TwainFree(_cap.hContainer);
			_cap.hContainer = 0;
		}

	return ret;
	}


	bool GetOneCapabilityValue(TW_UINT16 cap, void*const pitem, int itemSize)
	{
		bool ret = false;
		TW_CAPABILITY twCap;
		twCap.Cap = cap;
		twCap.ConType = TWON_ONEVALUE;
		//TW_ONEVALUE container;

		twCap.hContainer = NULL;
		ret = TwainCall(&appId_,&source_,DG_CONTROL,DAT_CAPABILITY,MSG_GETCURRENT,(TW_MEMREF)&twCap);
   
		if(ret)
		{
	// this did work:
	//	memcpy(&container, twCap.hContainer, sizeof(container));
	//	memcpy( pitem, &container.Item, itemSize);
		
		
		
		memcpy( pitem, &(((TW_ONEVALUE*)(twCap.hContainer))->Item), itemSize);
		GlobalFree(twCap.hContainer);
		}

		return ret;
	};

	//// overloaded function for each twain type
	//bool SetOneCapabilityValue(TW_UINT16 Cap, const TW_FIX32 value)
	//{
	//  bool ret = false;
	//  TW_CAPABILITY   cap;

	//  cap.Cap         = Cap;
	//  cap.ConType     = TWON_ONEVALUE;
	//  std::auto_ptr<TW_ONEVALUEFIX32> pcontainer (new TW_ONEVALUEFIX32);
	//  cap.hContainer  = pcontainer.get();

	//  pcontainer->ItemType = TWTY_FIX32;
	//  pcontainer->Item     = value;

	//  // capability structure is set, make the call to the source now
	//  ret = TwainCall(&appId_, &source_, DG_CONTROL, DAT_CAPABILITY, MSG_SET, (TW_MEMREF)&(cap));

	//  return ret;
	//};


   //DSM_MEMALLOCATE   DSM_MemAllocate;
   //DSM_MEMFREE       DSM_MemFree;
   //DSM_MEMLOCK       DSM_MemLock;
   //DSM_MEMUNLOCK     DSM_MemUnlock;


	// overloaded function for each twain type
	bool SetOneCapabilityValue(TW_UINT16 Cap, const TW_FIX32 value)
	{
		bool ret = false;
		TW_CAPABILITY   cap;

		cap.Cap         = Cap;
		cap.ConType     = TWON_ONEVALUE;

		//cap.hContainer  = DSMEntryPoints_.DSM_MemAllocate(sizeof(TW_ONEVALUEFIX32));
		cap.hContainer = ::GlobalAlloc(GPTR,sizeof(TW_ONEVALUEFIX32));

		//TW_ONEVALUEFIX32 *pVal = (TW_ONEVALUEFIX32*)DSMEntryPoints_.DSM_MemLock(cap.hContainer);
		TW_ONEVALUEFIX32 *pVal = (TW_ONEVALUEFIX32 *)::GlobalLock(cap.hContainer);

		pVal->ItemType = TWTY_FIX32;
		pVal->Item     = value;

		// capability structure is set, make the call to the source now
		ret = TwainCall(&appId_, &source_, DG_CONTROL, DAT_CAPABILITY, MSG_SET, (TW_MEMREF)&(cap));
		//DSMEntryPoints_.DSM_MemUnlock(cap.hContainer);
		//DSMEntryPoints_.DSM_MemFree(cap.hContainer);

		::GlobalUnlock(cap.hContainer);
		::GlobalFree (cap.hContainer);


	  return ret;
	};


	// NB not working yet!
	void SetOneCapabilityValue2(TW_UINT16 Cap, const TW_FIX32 value)    
	{
		TW_CAPABILITY pCap;
		pCap.Cap = Cap;
		pCap.ConType = TWON_ONEVALUE;
		memset(&pCap, 0, sizeof(TW_CAPABILITY));

		// Application must allocate memory for the capability container.
		pCap.hContainer = GlobalAlloc(GHND,(sizeof(TW_ONEVALUE)+sizeof(TW_FIX32)));
		if (pCap.hContainer == 0)
		{
			throw TwainBad( "Insufficient memory (TW_FIX32)");

		}

		// Lock memory handle so that container's fields can be populated.
		TW_ONEVALUE * pTWOneValue = (pTW_ONEVALUE) GlobalLock (pCap.hContainer);

   	// Populate container with appropriate data type and Physical Width
		pTWOneValue->ItemType = TWTY_FIX32;
		memcpy(&pTWOneValue->Item, &value, sizeof(value));

		// Unlock memory handle.
		GlobalUnlock(pCap.hContainer);
		TwainCall(&appId_, &source_, DG_CONTROL, DAT_CAPABILITY, MSG_SET,(TW_MEMREF)&pCap);

	};




	bool TwainCall(pTW_IDENTITY pOrigin,pTW_IDENTITY pDest,
					   TW_UINT32 DG,TW_UINT16 DAT,TW_UINT16 MSG,
					   TW_MEMREF pData, TW_UINT16 *preturn = NULL)
	{
		bool retval = false;
		if(m_driverValid)
		{
			USHORT tw_status;
			tw_status = (*m_pDSMProc)(pOrigin,pDest,DG,DAT,MSG,pData);
			retval =  (TWRC_SUCCESS == tw_status ) || ( TWRC_XFERDONE == tw_status);
			if(NULL != preturn)
				*preturn = tw_status;
			
         /*
			std::ostringstream x;
			static std::string previousEvent;
			
			//x << "T.C. 0x" << std::hex << MSG;
			x << std::dec << " DG" << DG << " from " << pOrigin->ProductName << " to " ;
			if(NULL!=pDest)
			{
				x << pDest->ProductName;
			}
			else
			{
				x<< "NULL";
			}
			x <<" status " << tw_status ; 
         */

			if (!retval) // query status on error
			{
				(*m_pDSMProc)(pOrigin,pDest,DG_CONTROL,DAT_STATUS,MSG_GET,&m_Status);
            /*
				x << " condition code " <<   m_Status.ConditionCode << std::endl;

				if ( previousEvent != x.str())
				{
					previousEvent = x.str();
					OutputDebugString( previousEvent.c_str());
				}
            */
				// so, on further investigation, the Twain driver is telling us that the request DID work....
				if (0 == m_Status.ConditionCode) 
					retval = true;

			}
		}
		return retval;
	};


	void EnableCamera(bool enable)
	{
		static bool warned(false);
		bool ret;
		TW_CAPABILITY _cap;	
		TW_INT32 capValue =0;
		memset(&_cap, 0, sizeof(TW_CAPABILITY));
		_cap.Cap = CAP_UICONTROLLABLE;
		ret = GetCapability_TWAIN( _cap, &capValue, sizeof(capValue));
		if( !ret || ( 0 == capValue))
		{
			//throw TwainBad("microManager currently can not control cameras with non-controllable UI");

			if(!warned)
			{
				warned = true;
				OutputDebugString("camera reports that UI is non-controllable, you must select a camera that reports UI_CONTROLLABLE = true");
				ostringstream mezzz;
				mezzz << __FILE__ << " " << __LINE__ << " you must select a camera that reports UI_CONTROLLABLE = true";
				DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);	
			}
		}


		// BEFORE enabling, setup capabilities
		if(enable)
			{

				double requestedExposure = pcamera_->GetExposure();
				if( SourceOpened != twainState_s) throw TwainBad("invalid sequence, request to enable camera before camera source is opened");
				std::ostringstream messs;
 				messs.str("");
				// Source is Open, Twain State is 4, setup the necessary capabilities
				
				// does source support exposure time setting?
				TW_INT32 capabilitySupport =0;
				memset(&_cap, 0, sizeof(TW_CAPABILITY));
				_cap.Cap = ICAP_EXPOSURETIME;
				ret = GetCapability_TWAIN( _cap, &capabilitySupport, sizeof(capabilitySupport), MSG_QUERYSUPPORT );
				if (ret)
				{
					exposureGettable_ = !!( TWQC_GET & capabilitySupport);
					exposureSettable_ = !!(	TWQC_SET & capabilitySupport);
				}



				if( exposureGettable_ && exposureSettable_)
				{
				// set auto exposure as desired ( for now always turn on 'autobright' only if there is no valid exposure time requested )
				const bool autoBright( requestedExposure < 0.);
				TW_UINT32 tmpAutoBright;
				bool couldGetCap = GetCapability(ICAP_AUTOBRIGHT, tmpAutoBright);

				if (couldGetCap && ( (TW_UINT32)autoBright != tmpAutoBright))
				{
					// make sure requested exposure is a reasonable value before requesting to turn off autobright

					// requestedExposure is in milliseconds
					// Twain specification says that exposure is in seconds
					double tmpExposure = requestedExposure / 1000. ; // time in seconds so 8 ms will look like 0.008
					tmpExposure *= 10.; // SPECIFIC TO LEICA????? NUMBER LIKE 8ms appears as 0.080 ?????

					bool ret = false;
					TW_FIX32 exposure = TwainSpecUtilities::FloatToFIX32((float)tmpExposure);

					messs << "attempt to set exposure to " << TwainSpecUtilities::FIX32ToFloat(exposure);
					OutputDebugString(messs.str().c_str());

					ret = set_CapabilityOneValue(ICAP_EXPOSURETIME, exposure);
					OutputDebugString(ret?" set exp success!! ":" set exp failed ");

					if(!SetCapability(ICAP_AUTOBRIGHT, (TW_UINT16)autoBright, false) ) OutputDebugString("failed to set autobright\n");
				}

				// requestedExposure is in milliseconds
				// Twain specification says that exposure is in seconds
				double tmpExposure = requestedExposure / 1000. ; // time in seconds so 8 ms will look like 0.008
				tmpExposure *= 10.; // SPECIFIC TO LEICA????? NUMBER LIKE 8ms appears as 0.080 ?????


				TW_FIX32 exposure = TwainSpecUtilities::FloatToFIX32((float)tmpExposure);

				messs << "attempt to set exposure to " << TwainSpecUtilities::FIX32ToFloat(exposure) << std::endl;
				OutputDebugString(messs.str().c_str());

				ret = set_CapabilityOneValue(ICAP_EXPOSURETIME, exposure);
				OutputDebugString(ret?"set exp success!!\n":"set exp failed\n");


				memset(&exposure, 0, sizeof(exposure));
				TW_CAPABILITY _cap;		
				memset(&_cap, 0, sizeof(TW_CAPABILITY));
				_cap.Cap = ICAP_EXPOSURETIME;
				GetCapability_TWAIN(_cap, &exposure, sizeof(TW_FIX32));

				tmpExposure = TwainSpecUtilities::FIX32ToFloat(exposure);
				// SPECIFIC TO LEICA??  - NUMBER LIKE 0.080 means 8 ms.
				tmpExposure /= 10.; 
				messs.str("");
				messs << "  Exposure set to " <<  tmpExposure;
				OutputDebugString(messs.str().c_str());
				pcamera_->SetExposure(tmpExposure*1000.);

				}







				memset(&setROI_,0, sizeof(setROI_));
				// retrieve current layout
				TW_IMAGELAYOUT layout;
				bool retval = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_GET,(TW_MEMREF)&layout);
				if(!retval) throw TwainBad("failed to get current image layout");
				setROI_ = layout.Frame;


			

		}



		TW_USERINTERFACE twUI;
		
		twUI.ShowUI = false;
		twUI.ModalUI = false;
		twUI.hParent = (TW_HANDLE)dummyWindowHandle_;
		//enable data source
		{
			MMThreadGuard g(*pstateLock_s);
			twainState_s = SourceOpened;
			if(!TwainCall(&appId_,&source_,DG_CONTROL,DAT_USERINTERFACE,(enable?MSG_ENABLEDS:MSG_DISABLEDS),(TW_MEMREF)&twUI)) throw TwainBad((enable?"failed to enable data source":"failed to disable data source"));
		}

		CDeviceUtils::SleepMs(1);
		if(enable)
		{
			MMThreadGuard g(*pstateLock_s);
			if ( twainState_s < SourceEnabled) twainState_s = SourceEnabled;
		}

	};

	std::vector<std::string> AvailableSources(void)
	{
		std::vector<std::string> namelist;
		std::map<std::string, TW_IDENTITY>::iterator ii;
		for( ii = availablesources_.begin(); ii!=availablesources_.end(); ++ii)
		{
			namelist.push_back(ii->first);
		}
		return namelist;

	};


	TW_FRAME GetROIRectangle() const
	{
		// todo this should check that source has reached state 4 !!!!!!!
		if ( twainState_s < ManagerOpened ) throw TwainBad("camera has not yet been opened");
		OutputDebugFrame("GetROIRectangle returns ", setROI_);
		return setROI_;
	};

	TW_FRAME GetWholeCaptureFrame() const
	{
		if ( twainState_s < ManagerOpened ) throw TwainBad("camera has not yet been opened");
		OutputDebugFrame("GetWholeCaptureFrame returns ", initialFrame_);
		return initialFrame_;
	}

	void SetROIRectangle(const TW_FRAME& requestedFrame)    // assume user valid selected region within the image 
	{

		std::ostringstream messs;

		if( ManagerOpened  == twainState_s)
		{
			SelectAndOpenSource ( CurrentSource());
		}
		::Sleep(10);

		if ( SourceOpened!= twainState_s) throw TwainBad("camera not ready to set ROI");

		memset(&setROI_,0, sizeof(setROI_));
		// retrieve current layout
		TW_IMAGELAYOUT layout;
		bool retval = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_GET,(TW_MEMREF)&layout);
		if(!retval) throw TwainBad("failed to get current image layout");

		OutputDebugFrame("in SetROIRectangle current layout: ",layout.Frame);
		messs << "doc # " << layout.DocumentNumber << " page # " << layout.PageNumber << " Frame # " << layout.FrameNumber << std::endl;
		OutputDebugString(messs.str().c_str());

		// if the frames are not the same, request the correct frame
		if( 0 != memcmp((void*)&(layout.Frame), (void*)&requestedFrame, sizeof(TW_FRAME)))
		{
			// request the correct frame
			layout.Frame = requestedFrame;
			OutputDebugFrame("in SetROIRectangle , request frame is: ", requestedFrame);

			retval = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_SET,(TW_MEMREF)&layout);
			OutputDebugString(retval?"new layout set\n":"new layout NOT SET\n");

			retval = set_CapabilityOneValue(ICAP_FRAMES, layout.Frame);
			OutputDebugString(retval?"new frame set":"new frame NOT set");
			if (retval) setROI_ = requestedFrame;
		}
		else
		{
			setROI_ = requestedFrame;
		}


		// current layout:
		retval = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_GET,(TW_MEMREF)&layout);

		OutputDebugFrame(" current layout: ", layout.Frame);
		messs.str("");

		messs << "doc # " << layout.DocumentNumber << " page # " << layout.PageNumber << " Frame # " << layout.FrameNumber << std::endl;
		OutputDebugString(messs.str().c_str());



	};

	// restore the capture region to the original (should be setup to be the entire sensor)
	void ClearROI()
	{
		SetROIRectangle(initialFrame_);
	};



	void SelectAndOpenSource(const std::string selectedsource)
	{
		std::ostringstream messs;
		
		if ( currentlySelectedSourceName_ != selectedsource)
		{
			currentlySelectedSourceName_ = selectedsource;
			memset((void*)&setROI_, 0, sizeof(setROI_));
			// the very first acquisition into this camera should be  the full captured region.
			memset((void*)&initialFrame_, 0, sizeof(initialFrame_));
			// a new source is selected, clear the list of saved image sizes
			/*roiImageSizes_r.clear();*/
			
		}

		std::map<std::string, TW_IDENTITY>::iterator ii = availablesources_.begin();
		while( ii!= availablesources_.end())
		{
			if (selectedsource == ii->first)
			{
				//todo GET RID OF ' sourceisopen_ ' and use just twainState_s!!!!
				if (sourceisopen_ && (selectedsource != CurrentSource()) )
				{
					MMThreadGuard g(*pstateLock_s);
					if(!TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_CLOSEDS,(TW_MEMREF)&source_)) throw TwainBad("failed to close data source");
					twainState_s = ManagerOpened;
					sourceisopen_ = false;
					source_ = ii->second;
				}
				if (! sourceisopen_)
				{
					// open data source
					// N.B. at least in case of Leica firewire camera, this call can succeed even if the camera is unplugged - and even go on to return 
					// some particular image size...
					{
						MMThreadGuard g(*pstateLock_s);
						if(!TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_OPENDS,(TW_MEMREF)&source_)) throw TwainBad("failed to open data source");
						twainState_s = SourceOpened;
					}


				   sourceisopen_ = true;
					source_s = source_;

					// callback is registered immediately after opening the data source....
					TW_CALLBACK callback = {0};
					callback.CallBackProc = (TW_MEMREF)TwainCallback;
					callback.RefCon       = 0; 
					callbackValid_ = TwainCall(&appId_, &source_, DG_CONTROL, DAT_CALLBACK, MSG_REGISTER_CALLBACK, (TW_MEMREF)&callback);

   				//// set the transfer count capability (of the application)
					if(!SetCapability(CAP_XFERCOUNT, (TW_UINT16)count_, true) ) OutputDebugString( "failed to set image count capability");
#if 0
					TW_FRAME zeroFrame;
					memset((void*)&zeroFrame, 0, sizeof(zeroFrame));
					// if original capture frame if it hasn't been saved yet, find the maximum capture size for this camera
					if( 0 == memcmp((void*)&(zeroFrame), (void*)&initialFrame_, sizeof(TW_FRAME)))
					{
						TW_CAPABILITY _cap;
						bool succ;
				
						TW_FIX32 xresolution, yresolution;
						float xr, yr;
		
						memset(&_cap, 0, sizeof(TW_CAPABILITY));
						_cap.Cap = ICAP_XRESOLUTION;
						succ = GetCapability_TWAIN(_cap, &xresolution, sizeof(TW_FIX32));
						xr = TwainSpecUtilities::FIX32ToFloat(xresolution);
					
						
						memset(&_cap, 0, sizeof(TW_CAPABILITY));
						_cap.Cap = ICAP_YRESOLUTION;
						succ = GetCapability_TWAIN(_cap, &yresolution, sizeof(TW_FIX32));
						yr = TwainSpecUtilities::FIX32ToFloat(yresolution);

						succ =  ResetCapability(ICAP_XRESOLUTION);
						succ =  ResetCapability(ICAP_YRESOLUTION);

						memset(&_cap, 0, sizeof(TW_CAPABILITY));
						// twain spec says 'resolution' is pixels / pixel unit but also states that 'many sources like to put the maximum number of pixels here'
						_cap.Cap = ICAP_XRESOLUTION;
						succ = GetCapability_TWAIN(_cap, &xresolution, sizeof(TW_FIX32));
						if (succ)
							succ = set_CapabilityOneValue(ICAP_XRESOLUTION, xresolution);
						xr = TwainSpecUtilities::FIX32ToFloat(xresolution);
						
						memset(&_cap, 0, sizeof(TW_CAPABILITY));
						_cap.Cap = ICAP_YRESOLUTION;
						succ = GetCapability_TWAIN(_cap, &yresolution, sizeof(TW_FIX32));
						if(succ)
							succ = set_CapabilityOneValue(ICAP_YRESOLUTION, yresolution);
						yr = TwainSpecUtilities::FIX32ToFloat(yresolution);

		//				TW_IMAGELAYOUT layout0;
		//				memset(&layout0,0, sizeof(layout0));
		//				succ = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_RESET,(TW_MEMREF)&layout0);

						succ = ResetCapability(ICAP_FRAMES);

						TW_IMAGELAYOUT layout0;
						memset(&layout0,0, sizeof(layout0));
						succ = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_GET,(TW_MEMREF)&layout0);
						if(!succ)
						{
							pcamera_->LogMessage("source is not Twain-compliant: not able to retrieve layout");
						}
						else
						{
							succ = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_SET,(TW_MEMREF)&layout0);
							if(succ)
							{
								TW_FRAME f;
								memset(&f, 0, sizeof(f));
								memset(&_cap, 0, sizeof(TW_CAPABILITY));
								_cap.Cap = ICAP_FRAMES;
								succ = GetCapability_TWAIN(_cap, &f, sizeof(f), MSG_GETCURRENT );
								const char* perror = "source is not Twain-compliant: not able to get current frame";
								if(!succ) pcamera_->LogMessage(perror);
								OutputDebugString(succ ? "get reset frame \n" : perror);
							}
						}
						
				//		succ = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_SET,(TW_MEMREF)&layout0);
				//		OutputDebugString(succ?"reset layout was set again ":"layout NOT set after layout reset");

						succ = set_CapabilityOneValue(ICAP_FRAMES, layout0.Frame);
						OutputDebugString(succ?"frame set from reset layout \n":"frame NOT set from reset layout\n");

					}
#endif
				}
				return;

			}
			++ii;
		}
	};

	std::string CurrentSource()
	{
		std::string productName;
		if ( 0 != source_.ProductName[0])
		{
			productName = source_.ProductName;
		}
		return productName;
	};

	// Twain-specific memory management adapted from Twain.org's TwainApp.cpp
	//////////////////////////////////////////////////////////////////////////////
	// The following functions are defined in the DSM2,
	// For backwards compatibiltiy on windows call the default function
	TW_HANDLE TwainAlloc(TW_UINT32 _size)
	{
		if(DSMEntryPoints_.DSM_MemAllocate)
		{
			return DSMEntryPoints_.DSM_MemAllocate(_size);
		}

		#ifdef TWH_CMP_MSC
			return ::GlobalAlloc(GPTR, _size);
		#else

		return 0;
		#endif
	}

	//////////////////////////////////////////////////////////////////////////////
	void TwainFree(TW_HANDLE _hMemory)
	{
		if(DSMEntryPoints_.DSM_MemFree)
		{
			return DSMEntryPoints_.DSM_MemFree(_hMemory);
		}

		#ifdef TWH_CMP_MSC
		  ::GlobalFree(_hMemory);
		#endif

	  return;
	}

	//////////////////////////////////////////////////////////////////////////////
	TW_MEMREF TwainLockMemory(TW_HANDLE _hMemory)
	{
		if(DSMEntryPoints_.DSM_MemLock)
		{
			return DSMEntryPoints_.DSM_MemLock(_hMemory);
		}

		#ifdef TWH_CMP_MSC
			return (TW_MEMREF)::GlobalLock(_hMemory);
		#else	
			return 0;
		#endif
	}

	//////////////////////////////////////////////////////////////////////////////
	void TwainUnlockMemory(TW_HANDLE _hMemory)
	{
	  if(DSMEntryPoints_.DSM_MemUnlock)
	  {
		 return DSMEntryPoints_.DSM_MemUnlock(_hMemory);
	  }

	#ifdef TWH_CMP_MSC
	  ::GlobalUnlock(_hMemory);
	#endif

	  return;
	};


	HWND CreateDummyWindow(void)
	{
		std::ostringstream messs;
		messs<< source_.ProductName << " " << source_.Id << std::endl;

		HWND hwnd;
		hwnd = CreateWindow("STATIC",                // class
						"Acquire Proxy",              // title
						WS_POPUPWINDOW,               // style
						CW_USEDEFAULT, CW_USEDEFAULT, // x, y
						CW_USEDEFAULT, CW_USEDEFAULT, // width, height
						HWND_DESKTOP,                 // parent window
						NULL,                         // hmenu
						g_hinstDLL,                     // hinst
						NULL);                        // lpvparam
		return hwnd;

	};


	// adapted from twain.org's twainapp.cpp
	//////////////////////////////////////////////////////////////////////////////
	/**
	* Callback funtion for DS.  This is a callback function that will be called by
	* the source when it is ready for the application to start a scan. This 
	* callback needs to be registered with the DSM before it can be called.
	* It is important that the application returns right away after recieving this
	* message.  Set a flag and return.  Do not process the callback in this function.
	*/

	static TW_UINT16 FAR PASCAL TwainCallback(pTW_IDENTITY _pOrigin,
					pTW_IDENTITY _pDest,
					TW_UINT32    _DG,
					TW_UINT16    _DAT,
					TW_UINT16    _MSG,
					TW_MEMREF    _pData)
	{
		UNREFERENCEDPARAMETER(_pDest);
		UNREFERENCEDPARAMETER(_DG);
		UNREFERENCEDPARAMETER(_DAT);
		UNREFERENCEDPARAMETER(_pData);

		TW_UINT16 twrc = TWRC_SUCCESS;
		std::ostringstream messs;
		// we are only waiting for callbacks from our datasource, so validate
		// that's the originator.
		if(0 == _pOrigin || _pOrigin->Id != source_s.Id)
		{
			return TWRC_FAILURE;
		}
		switch (_MSG)
		{
			case MSG_XFERREADY:
				messs << " callback gets 'MSG_XFERREADY'  " <<  _MSG << " current twain state was " << twainState_s << std::endl;
				OutputDebugString(messs.str().c_str());
				{
					MMThreadGuard g(*pstateLock_s);
					if(( SourceEnabled == twainState_s) || ( SourceOpened ==twainState_s)) // source transits from 4 to 5 automagically (but no callback occurs...)
						twainState_s = TransferReady;
				}
				break;
			case MSG_CLOSEDSREQ:
				// sort of a duplicate use of the twainState_s variable, we just 
				{
		//			MMThreadGuard g(*pstateLock_s);
		//			if ( SourceOpened < twainState_s )
		//				twainState_s = SourceOpened;
					closeDSRequest_s = true;
				}

			case MSG_CLOSEDSOK:
			case MSG_NULL:
				 messs << " callback gets message " <<  _MSG << std::endl;
				 OutputDebugString(messs.str().c_str());
				 //todo
				 //gpTwainApplicationCMD->m_DSMessage = _MSG;
				 break;
			default:
				messs << " callback gets message " <<  _MSG << std::endl;
				twrc = TWRC_FAILURE;
			   break;
	     }

	  return twrc;
	};
	
	void RetrieveCurrentLayout(TW_IMAGELAYOUT& layout )
	{
		memset(&setROI_,0, sizeof(setROI_));
		// retrieve current layout

		bool retval = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGELAYOUT, MSG_GET,(TW_MEMREF)&layout);
		if(!retval) throw TwainBad("failed to get current image layout");
		setROI_ = layout.Frame;

		std::ostringstream messs;
		OutputDebugFrame(" current layout: ", layout.Frame);
		messs.str("");
		messs << std::endl;
		messs << "doc # " << layout.DocumentNumber << " page # " << layout.PageNumber << " Frame # " << layout.FrameNumber << std::endl;
		OutputDebugString(messs.str().c_str());

		// the very first acquisition into this camera should be  the full captured region.
		TW_FRAME zeroFrame;
		memset((void*)&zeroFrame, 0, sizeof(zeroFrame));

		// save the original capture frame if it hasn't been saved yet.
		if( 0 == memcmp((void*)&(zeroFrame), (void*)&initialFrame_, sizeof(TW_FRAME)))
		{
			initialFrame_ = layout.Frame;
			OutputDebugFrame(" initial capture area is ", initialFrame_);

		}

	};

   void LaunchVendorSettings(void)
	{
		bool ret;
		
		TW_USERINTERFACE twUI;
		closeDSRequest_s = false;

		StartTwain();

#if 0
		MMThreadGuard g(*pstateLock_s);



		if ( PreSession < twainState_s ) throw TwainBad("wrong state for LaunchVendorSettings");

		m_hTwainDLL  = LoadLibraryA(m_libName.c_str());
		if(NULL == m_hTwainDLL) throw TwainBad("failed to load Twain library");
		twainState_s = ManagerLoaded;
		// get the function
		m_pDSMProc = (DSMENTRYPROC)GetProcAddress(m_hTwainDLL,(LPCSTR)1);
		if (NULL == m_pDSMProc)
		{
			FreeLibrary(m_hTwainDLL);
			m_hTwainDLL = NULL;
			twainState_s = PreSession;
			throw TwainBad("Twain library is incorrect");
		}
		// setup application's information for Twain
		// Expects all the fields in appId_ to be set except for the id field.
		appId_.Id = 0; // Initialize to 0 (Source Manager will assign real value)
		appId_.Version.MajorNum = 1; //Your app's version number
		appId_.Version.MinorNum = 5;
		appId_.Version.Language = TWLG_USA;
		appId_.Version.Country = TWCY_USA;
		strcpy (appId_.Version.Info, "1.5");
		appId_.ProtocolMajor = TWON_PROTOCOLMAJOR;
		appId_.ProtocolMinor = TWON_PROTOCOLMINOR;
		// DF_APP2 lets us use the memory callbacks, etc.
		appId_.SupportedGroups = DF_APP2 | DG_IMAGE | DG_CONTROL;
		strcpy (appId_.Manufacturer, "UCSF");
		strcpy (appId_.ProductFamily, "Generic");
		strcpy (appId_.ProductName, "microManager");


		// open the datasource manager
		if(!TwainCall(&appId_,NULL,DG_CONTROL,DAT_PARENT,MSG_OPENDSM,(TW_MEMREF)NULL)) throw TwainBad("failed to open data source manager");
		// check for DSM2 support

		memset(&DSMEntryPoints_, 0, sizeof(DSMEntryPoints_));
		DSMEntryPoints_.Size = sizeof(TW_ENTRYPOINT);

		// get the entry points
		TwainCall(&appId_,NULL,DG_CONTROL,DAT_ENTRYPOINT,MSG_GET,&DSMEntryPoints_); //throw TwainBad("get DSM entry points failed - need DSM 2 support");

		std::ostringstream messs;
		messs <<  "twimpl static callback function address is " << (void *)TwainCallback << std::endl;
		OutputDebugString(messs.str().c_str());
		if( NULL != dummyWindowHandle_)
			DestroyWindow(dummyWindowHandle_);
		dummyWindowHandle_ = CreateDummyWindow();

		twainState_s = ManagerOpened; // TWAIN is ready to select and open a source 



		// enumerate available image sources
		TW_IDENTITY so;
		// get the first source
		availablesources_.clear();
		if(!TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_GETFIRST,&so)) throw TwainBad("no Twain sources are available");
		availablesources_.insert(std::pair<std::string,TW_IDENTITY>(so.ProductName, so));
		while(TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_GETNEXT,&so))
		{
			availablesources_.insert(std::pair<std::string,TW_IDENTITY>(so.ProductName, so));
		}

		// get the default source and write its parameters onto source_,
		// but client can still select a different source later
		memset(&source_, 0, sizeof(source_));
		if(!TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_GETDEFAULT,&source_)) throw TwainBad("failed to get default data source");


#endif


		
		// this could pop up a dialogue from inside Twain "TWAINDSM.dll" and allow you select the source
		//if( !TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_USERSELECT,&source_)) throw TwainBad("user selection of source failed");

		// open data source
		// N.B. at least in case of Leica firewire camera, this call can succeed even if the camera is unplugged - and even go on to return 
		// some particular image size...
		{
			sourceisopen_ = false;
			MMThreadGuard g(*pstateLock_s);
			if(!TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_OPENDS,(TW_MEMREF)&source_)) throw TwainBad("failed to open data source");
			twainState_s = SourceOpened;
			sourceisopen_ = true;
			source_s = source_;
		}

		// callback is registered immediately after opening the data source....
		TW_CALLBACK callback = {0};
		callback.CallBackProc = (TW_MEMREF)TwainCallback;
		callback.RefCon       = 0; 
		callbackValid_ = TwainCall(&appId_, &source_, DG_CONTROL, DAT_CALLBACK, MSG_REGISTER_CALLBACK, (TW_MEMREF)&callback);

		//// set the transfer count capability (of the application)
		//if(!SetCapability(CAP_XFERCOUNT, (TW_UINT16)count_, true) ) throw TwainBad("failed to set image count capability");
		

		
		twUI.ShowUI = true;
		twUI.ModalUI = true;
		twUI.hParent = (TW_HANDLE)dummyWindowHandle_;
		//enable data source
		{
			MMThreadGuard g(*pstateLock_s);
			if(!TwainCall(&appId_,&source_,DG_CONTROL,DAT_USERINTERFACE, MSG_ENABLEDS,(TW_MEMREF)&twUI)) throw TwainBad("failed to enable data source");
			twainState_s = SourceEnabled;

		}
		
		// Begin the event-handling loop. Data transfer takes place in this loop.

		MSG msg;
		TW_EVENT event;
//		TW_PENDINGXFERS pxfers;
		while (GetMessage ((LPMSG) &msg, 0, 0, 0))
		{

			// Each window message must be forwarded to the default data source.

			event.pEvent = (TW_MEMREF) &msg;
			event.TWMessage = MSG_NULL;

			uint16_t returncode;

			ret = TwainCall(&appId_, &source_,  DG_CONTROL, DAT_EVENT,MSG_PROCESSEVENT,(TW_MEMREF) &event, &returncode);


			// if TwainCallback sees message MSG_CLOSEDSREQ, it will change the state back to SourceOpened
			// this is our signal to explicitly close the vendor GUI and return from here.

			{
				MMThreadGuard g(*pstateLock_s);
				if (closeDSRequest_s) //( twainState_s < SourceEnabled )
				{
					closeDSRequest_s = false;
					//todo better error reporting here. but remember, at this point there is no GUI at all.
					bool ret;
					ret = TwainCall(&appId_,&source_,DG_CONTROL,DAT_USERINTERFACE,MSG_DISABLEDS,(TW_MEMREF)&twUI) ;
					twainState_s = SourceOpened;
					ret = TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_CLOSEDS,(TW_MEMREF)&source_);
					sourceisopen_ = false;
					twainState_s = ManagerOpened;
					ret = TwainCall(&appId_,NULL,DG_CONTROL,DAT_PARENT,MSG_CLOSEDSM,(TW_MEMREF)NULL);
					twainState_s = ManagerLoaded;
					int retval = FreeLibrary(m_hTwainDLL);
					if(1 != retval)
					{
						pcamera_->LogMessage("error freeing twain library");
					}
					m_hTwainDLL = NULL;
					twainState_s = PreSession;
					break;

				}
				if( TransferReady == twainState_s)
				{
					int imageHeight, imageWidth;
					char bytesPerPixel;
					// this will set state back to 'SourceEnabled'
					GetImage(imageHeight, imageWidth, bytesPerPixel);
				}

			}


			// If the message does not correspond to a data source event, we must
			// dispatch it to the appropriate Windows window.

			if (returncode == TWRC_NOTDSEVENT)
			{             
				 TranslateMessage ((LPMSG) &msg);
				 DispatchMessage ((LPMSG) &msg);
				 continue;
			}
#if 0
			// log each unique DS message 
			unsigned short lpwords[2];
			static HWND        old_hwnd;
			static UINT        old_message;
			static WPARAM      old_wParam;
			static LPARAM      old_lParam;
			if ( (275 != msg.message) )//  && (512 != msg.message)  && (15!=msg.message))
			{
				if ((old_hwnd != msg.hwnd )|| (old_message != msg.message) || ( old_wParam != msg.wParam ) || ( old_lParam != msg.lParam ))
				{
					// a unique message came in
					std::ostringstream tmp;
					memcpy((void*)lpwords,&(msg.lParam),sizeof(msg.lParam));
		
					tmp << msg.hwnd << " mess: " << msg.message << " wPrm: " << msg.wParam << " lPrm: [" << lpwords[0] <<"," << lpwords[1]  << "] t: " << msg.time << std::endl;

					OutputDebugString(tmp.str().c_str());
					old_hwnd = msg.hwnd; old_message = msg.message; old_wParam = msg.wParam; old_lParam = msg.lParam;
				}
			}
#endif
			// If Callbacks are not registered, the message will come here
			// else TwainCallBack will process the request to shut down the Vendor GUI.
			// If the default data source is requesting that the data source's
			// dialog box be closed (user pressed Cancel), we must break out of the
			// message loop.

			if (event.TWMessage == MSG_CLOSEDSREQ)   break;

		}

	}

   void StopTwain(void)
	{
		//todo -- better error reporting here. but remember, at this point there maybe no GUI at all.

		//MM::MMTime t0 = GetCurrentMMTime();  // todo WHICH HEADER DO I NEED
		
		// change this to use CurrentMMTime;
		PerformanceTimer t0;

		while( PreSession < twainState_s )
		{
			//MM::MMTime elapsed = (GetCurrentMMTime() - t0);
			MMThreadGuard g(*pstateLock_s);
			bool ret;
			if(5000. < t0.elapsed()  )
			{
				OutputDebugString("StopTwain Timeout");
				break;
			}

			switch( twainState_s)
			{
				case Transferring:
					break; // just wait for transfer to complete
				case TransferReady: // cancel the transfer
					{
						bool transfersPending = true;
						bool ret = false;
						while( transfersPending)
						{
							HANDLE hBitmap;

							ret = TwainCall(&appId_, &source_, DG_IMAGE, DAT_IMAGENATIVEXFER, MSG_GET, &hBitmap);
							if(!ret) break;
							UCHAR *lpVoid = (UCHAR *)GlobalLock(hBitmap);
							GlobalUnlock(lpVoid);
							GlobalFree(hBitmap);
							// always check for pending transfers

							TW_PENDINGXFERS pendxfers;
							memset( &pendxfers, 0, sizeof(pendxfers) );

							ret = TwainCall(&appId_, &source_, DG_CONTROL, DAT_PENDINGXFERS, MSG_ENDXFER, (TW_MEMREF)&pendxfers);
							if(!ret) break;
						   if(0 == pendxfers.Count)
							{
								// nothing left to transfer, finished.
								transfersPending = false;
							}
						}
						// in case of an error, reset the transfers
						if (transfersPending)
						{
							TW_PENDINGXFERS pendxfers;
							memset( &pendxfers, 0, sizeof(pendxfers) );
							bool ret = TwainCall(&appId_, &source_, DG_CONTROL, DAT_PENDINGXFERS, MSG_ENDXFER, (TW_MEMREF)&pendxfers);

							// We need to get rid of any pending transfers
							if(ret &&(0 != pendxfers.Count))
							{
								memset( &pendxfers, 0, sizeof(pendxfers) );
								TwainCall(&appId_, &source_, DG_CONTROL, DAT_PENDINGXFERS, MSG_RESET, (TW_MEMREF)&pendxfers);
							}
						}
					}
					break;
				case SourceEnabled:
					TW_USERINTERFACE twUI;
					twUI.ShowUI = false;
					twUI.ModalUI = false;
					twUI.hParent = (TW_HANDLE)0;
					ret = TwainCall(&appId_,&source_,DG_CONTROL,DAT_USERINTERFACE,MSG_DISABLEDS,(TW_MEMREF)&twUI) ;
					twainState_s = SourceOpened;
					break;
				case SourceOpened:
					ret = TwainCall(&appId_,NULL,DG_CONTROL,DAT_IDENTITY,MSG_CLOSEDS,(TW_MEMREF)&source_);
					sourceisopen_ = false;
					memset(&DSMEntryPoints_,0, sizeof(DSMEntryPoints_));

					twainState_s = ManagerOpened;
					break;
				case ManagerOpened:
					ret = TwainCall(&appId_,NULL,DG_CONTROL,DAT_PARENT,MSG_CLOSEDSM,(TW_MEMREF)NULL);
					twainState_s = ManagerLoaded;
					break;
				case ManagerLoaded:
					int retval = FreeLibrary(m_hTwainDLL);
					if(1 != retval)
					{
						pcamera_->LogMessage("error freeing twain library");
					}
					m_hTwainDLL = NULL;
					twainState_s = PreSession;
					break;
			}
		}
	}

	~TwImpl()
	{
		try
		{
			StopTwain();
		}
		catch( TwainBad& ex)
		{
			OutputDebugString(ex.ReasonText());
		}
		catch(...)
		{

		}

      m_driverValid = false;
		m_pDSMProc = NULL;
		FreeLibrary(m_hTwainDLL);
		m_hTwainDLL = NULL;
		free(pbuf_);
   
   };
	std::string currentlySelectedSourceName_;
	// endof of TwImpl publics
private:	
	TwainCamera* pcamera_;
	HINSTANCE m_hTwainDLL;
	DSMENTRYPROC m_pDSMProc;
	std::string m_libName;
	bool m_driverValid;

	TW_IDENTITY appId_; // Twain's ID for this program
	TW_IDENTITY source_; // the camera
	//static TW_IDENTITY source_s;
	TW_STATUS m_Status;
	TW_INT16  m_returnCode;
	HWND m_hMessageWnd;
	int count_; // # images to acquire
	char* pbuf_; // copy the raw image data to here
	int width_;
	int height_;
	int bytesppixel_;
	long sizeofbuf_; //(size of current temporary buffer in bytes)
	std::map<std::string, TW_IDENTITY> availablesources_;
	bool sourceisopen_;
	bool callbackValid_;

	TW_ENTRYPOINT DSMEntryPoints_;
	HWND dummyWindowHandle_;
	//TW_FRAME IIIFFF;
	TW_FRAME setROI_;  // the latest selected ROI
	TW_FRAME initialFrame_; // the default capture size returned by the camera - will be the full sensor array at startup time
	// this map is declared in the wrapper, let the implementation have reference to it, too.
	/*std::map<long long, std::pair<uint16_t,uint16_t> > &roiImageSizes_r;*/
	/*TwImpl& operator=(const TwImpl& ){throw TwainBad("never come here unimplemented operator="); }; */// disallow operator =
	
	// cache these 'capability' settings
	bool exposureSettable_;
	bool exposureGettable_;

};

TwainDevice::TwainDevice(TwainCamera *pcamera):pTwImpl_(NULL),pcamera_(pcamera)
{
   pstateLock_s = new MMThreadLock();
   pWorkThread_g = new WorkerThread();
	pTwImpl_ = new TwImpl(pcamera_/*, roiImageSizes_*/);
	pcamera_->LogMessage(" created TwainDevice wrapper");
}

TwainDevice::~TwainDevice(void)
{
	delete pTwImpl_;
   pWorkThread_g->Stop();
   delete pWorkThread_g;
	delete pstateLock_s;
}

char* TwainDevice::GetImage(int& imageHeight, int& imageWidth, char& bytesPerPixel )
{
	//pTwImpl_->StartTwain();
	char* pchar = pTwImpl_->GetImage(imageHeight, imageWidth, bytesPerPixel);


	imageTransferStartTime_ = this->pcamera_->GetCurrentMMTime().getMsec();

	// calculate the framerate for second and subsequent frame
	if( 0. < previousImageStartTime_)
	{
		double el = imageTransferStartTime_ - previousImageStartTime_;
		if ( 0. != el)
		{
			double framesPerSecond = 1000./el;
			ostringstream o;
			o << " current acq rate " << framesPerSecond << " frames per sec. \n";
			//CodeUtility::DebugOutput( o.str());
		}
	}
	previousImageStartTime_  = imageTransferStartTime_;





	currentpixeldepth_ = bytesPerPixel;
	return pchar;
}

void TwainDevice::EnableCamera(const bool enable)
{
	pTwImpl_->EnableCamera(enable);

}

std::vector<std::string> TwainDevice::AvailableSources()
{
	pTwImpl_->StartTwain();
	return pTwImpl_->AvailableSources();
}

void TwainDevice::SelectAndOpenSource(std::string selectedsource)
{
	pTwImpl_->StartTwain();
	pTwImpl_->SelectAndOpenSource(selectedsource);
}

void TwainDevice::SetROIRectangle(const uint16_t& left, const uint16_t& top, const uint16_t& right, const uint16_t& bottom, uint16_t* pactualwidth , uint16_t* pactualheight, unsigned char* pactualdepth )
{
	pTwImpl_->StartTwain();
	TW_FRAME f;
	f.Left = TwainSpecUtilities::FloatToFIX32((float)left);
	f.Top = TwainSpecUtilities::FloatToFIX32((float)top);
	f.Right = TwainSpecUtilities::FloatToFIX32((float)right);
	f.Bottom = TwainSpecUtilities::FloatToFIX32((float)bottom);

	pTwImpl_->SetROIRectangle(f);
	if ((NULL !=pactualwidth) && (NULL!=pactualheight))
	{
#if 1
		// if we already determined the actual image size, there is no need to do it again.
		// the map of sizes is cleared when a difference source is selected.
		rectangle_t currentRectangle;
		currentRectangle.lefttoprightbottom_[0] = left;
		currentRectangle.lefttoprightbottom_[1] = top;
		currentRectangle.lefttoprightbottom_[2] = right;
		currentRectangle.lefttoprightbottom_[3] = bottom;
		std::map<long long, std::pair<uint16_t,uint16_t> >::iterator ii = roiImageSizes_.find(currentRectangle.values_ );
		if (roiImageSizes_.end()  == ii)
		{
#endif
		
			EnableCamera(true);
			GetActualImageSize(*pactualheight, *pactualwidth, currentpixeldepth_);

#if 1
			roiImageSizes_[currentRectangle.values_] = std::pair<uint16_t,uint16_t>(*pactualwidth,*pactualheight); 
#endif
			EnableCamera(false);
#if 1
		}
		else
		{
			*pactualwidth = ii->second.first;
			*pactualheight = ii->second.second;
			//todo retain pixel depth, too...
		}
#endif
		if(NULL!=pactualdepth) 
			*pactualdepth = currentpixeldepth_;
	}

}

void TwainDevice::GetROIRectangle(uint16_t& left, uint16_t& top, uint16_t& right, uint16_t& bottom)
{
	pTwImpl_->StartTwain();
	TW_FRAME f = pTwImpl_->GetROIRectangle();

	right = static_cast<uint16_t>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Right));
	left = static_cast<uint16_t>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Left));
	bottom = static_cast<uint16_t>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Bottom));
	top = static_cast<uint16_t>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Top));
}

void TwainDevice::ClearROI(uint16_t* pactualwidth , uint16_t* pactualheight, unsigned char* pactualdepth )
{
	pTwImpl_->StartTwain();
	pTwImpl_->ClearROI();
	if ((NULL !=pactualwidth) && (NULL!=pactualheight))
	{

		char tempDepth = 0;
		GetActualImageSize(*pactualheight, *pactualwidth, tempDepth);
		if(NULL!=pactualdepth) 
			*pactualdepth = tempDepth;

	}
}

void TwainDevice::GetWholeCaptureRectangle(uint16_t& left, uint16_t& top, uint16_t& right, uint16_t& bottom)
{
	pTwImpl_->StartTwain();
	TW_FRAME f = pTwImpl_->GetWholeCaptureFrame();

	right = static_cast<uint16_t>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Right));
	left = static_cast<uint16_t>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Left));
	bottom = static_cast<uint16_t>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Bottom));
	top = static_cast<uint16_t>(0.5+TwainSpecUtilities::FIX32ToFloat(f.Top));
}

// this caches the previously determined image size per each requested ROI, so if the user requests the same ROI again
// no image need be wasted to return the real image size.
void TwainDevice::GetActualImageSize(uint16_t& left, uint16_t& top, uint16_t& right, uint16_t& bottom, uint16_t& imheight, uint16_t& imwidth, char& bytesppixel)
{
	pTwImpl_->StartTwain();
	// if we already determined the actual image size, there is no need to do it again.
	// todo clear the map if the source is re-selected.
	rectangle_t currentRectangle;
	currentRectangle.lefttoprightbottom_[0] = left;
	currentRectangle.lefttoprightbottom_[1] = top;
	currentRectangle.lefttoprightbottom_[2] = right;
	currentRectangle.lefttoprightbottom_[3] = bottom;
	std::map<long long, std::pair<uint16_t,uint16_t> >::iterator ii = roiImageSizes_.find(currentRectangle.values_ );

	if (roiImageSizes_.end()  == ii)
	{
			EnableCamera(true);
			GetActualImageSize(imheight, imwidth, currentpixeldepth_);
			roiImageSizes_[currentRectangle.values_] = std::pair<uint16_t,uint16_t>(imheight,imwidth); 
			EnableCamera(false);
	}
	bytesppixel = currentpixeldepth_;
	
}

	//!!!! N.B. !!!! this consumes an image frame, but seems to be the only
	// way to guarantee to get the correct image size.
void TwainDevice::GetActualImageSize(uint16_t& imheight, uint16_t& imwidth, char& bytesppixel)
{	
	pTwImpl_->StartTwain();
	pTwImpl_->GetActualImageSize(imheight, imwidth, bytesppixel);
}

std::string TwainDevice::CurrentSource()
{
	pTwImpl_->StartTwain();
	return pTwImpl_->CurrentSource();
}

void TwainDevice::LaunchVendorSettings()
{
	if (NULL!=pTwImpl_)
	{
		pTwImpl_->StopTwain();
		pTwImpl_->LaunchVendorSettings();
		pTwImpl_->StartTwain();

	}
}

void TwainDevice::StopTwain()
{
	if (NULL!=pTwImpl_)
	{
		StopTwain();
	}
}

void TwainDevice::CurrentlySelectedSource(const std::string sourceName)
{
		pTwImpl_->currentlySelectedSourceName_ = sourceName;
}


std::string TwainDevice::CurrentlySelectedSource(void)
{
		return pTwImpl_->currentlySelectedSourceName_;
}

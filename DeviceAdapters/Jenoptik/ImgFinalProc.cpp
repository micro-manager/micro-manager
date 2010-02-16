#include "sdk/mexexl.h"
#include <atltime.h>
#include "mmgrProgRes.h"

unsigned short GetShiftBpp (mexCameraTypeSummary camType)
{
	return (unsigned short) (camType.BitDepth-8);
}

void RawToView(unsigned short* src0, ImgBuffer* destination, CProgRes* pHost)
{
	register unsigned short* src=NULL; 
	register unsigned char* dst=NULL;
	
	int shiftbpp = GetShiftBpp (pHost->m_CameraTypeSummary);

	src = src0;
	dst = destination->GetPixelsRW();
	for (unsigned int j = 0; j < destination->Height(); j++)
		for (unsigned int i = 0; i < destination->Width(); i++)
		{
			switch (destination->Depth())
			{
			case 2:
				{
					unsigned short intensity = (unsigned short) (0.299 * (*src++) + 0.587 * (*src++) + 0.114 * (*src++));
					*(unsigned short*)dst = intensity;
					dst += 2;
				}
				break;
			case 4:
				{
					*dst++ = (unsigned char) (*src++ >> shiftbpp);
					*dst++ = (unsigned char) (*src++ >> shiftbpp);
					*dst++ = (unsigned char) (*src++ >> shiftbpp);
					*dst++ = 0;
				}
				break;
			default:
				{
					unsigned short intensity_ = (unsigned short) (0.299 * (*src++) + 0.587 * (*src++) + 0.114 * (*src++));
					unsigned char intensity = (unsigned char)(intensity_ >> shiftbpp);
					*dst++ = intensity;
				}
				break;
			}
		}
}
/*
void RawToViewChannelsLive(unsigned int cx, unsigned int cy,unsigned char* src0, ImgBuffer *destination, CProgRes* pHost)
{
	register unsigned char* src=NULL; 
	register unsigned char* pb=pHost->m_pLiveBlue;
	register unsigned char* pg=pHost->m_pLiveGreen;
	register unsigned char* pr=pHost->m_pLiveRed;
	register unsigned char* dst_pb=NULL;
	register unsigned char* dst_pg=NULL;
	register unsigned char* dst_pr=NULL;
	register unsigned char* srcRow=(src0+(cy-1)*destination->Width());
	
	long src_delta_row = destination->Width();
	unsigned int run=0;

	while(run < cy)
	{
		src=srcRow;
		dst_pb=pb;
		dst_pg=pg;
		dst_pr=pr;
		for (unsigned int i = 0; i < cx; i++)
		{
			*pb++ = *src++; // blue
			*pg++ = *src++; // green
			*pr++ = *src++; // red
		}
		srcRow -= src_delta_row;
		dst_pb += cx;
		dst_pg += cx;
		dst_pr += cx;
		++run;
	}
}
*/
int __stdcall ImgFinalProc(unsigned long status, mexImg *pImg, unsigned long UserValue)
{
	CProgRes* pHost=reinterpret_cast<CProgRes*>(UserValue);

	if(pImg != NULL && (status==IMAGE_READY || status==IMAGE_READY_BUTNOTEQUALIZED))
	{
		unsigned short* redChannel = (unsigned short*)pImg->pRed;	
		unsigned short* greenChannel = (unsigned short*)pImg->pGreen;
		unsigned short* blueChannel = (unsigned short*)pImg->pBlue;
		unsigned short* pb,*pg,*pr;
		ImgBuffer *pdestination;
		unsigned int i, j;
		mexImageInfo		image_info;
		
		long	ccd;
		
		EnterCriticalSection (&(pHost->m_CSec));
		
/*		if (pHost->m_LiveRunning)
		{
			pdestination = &pHost->m_LiveImage;
			ccd = pHost->m_LiveAcqParams.ccdtransferCode;
			mexGetAcquisitionInfo (pHost->m_GUID, &pHost->m_LiveAcqParams, &image_info);
		}
		else
		{
			pdestination = &pHost->m_SnapImage;
			ccd = pHost->m_AcqParams.ccdtransferCode;
			mexGetAcquisitionInfo (pHost->m_GUID, &pHost->m_AcqParams, &image_info);
		}
*/		
		pdestination = &pHost->m_Image;
		ccd = pHost->m_AcqParams.ccdtransferCode;
		mexGetAcquisitionInfo (pHost->m_GUID, &pHost->m_AcqParams, &image_info);
		unsigned short* pimage = new unsigned short [image_info.DimX*image_info.DimY*3];
		unsigned short* pimage0 = pimage;

		switch (ccd)
		{
		case 0:
		case 1:
		case 3:
			if(image_info.Channels == 3)
			{
				if(redChannel && blueChannel && greenChannel)
				{

					pb = blueChannel;
					pg = greenChannel;
					pr = redChannel;
					for(i=0; i < image_info.DimY; i++)
					{
						for(j=0; j < image_info.DimX; j++)
						{
							*pimage++ = *pb++;
				                			
							*pimage++ = *pg++;
										
							*pimage++ = *pr++;
						}
					}
				}
			}
			else // colors == 1
			{
				pb = (unsigned short*) pImg->pBw;
				for(i=0; i < image_info.DimY;i++)
				{
					for(j=0; j < image_info.DimX; j++)
					{
						*pimage++ = *pb;
						*pimage++ = *pb;
						*pimage++ = *pb++;
					}
				}
			}
			RawToView (pimage0, pdestination, pHost);
			break;
		case 2:
			break;
		case 4:
		break;
		}
		delete [] pimage0;

		LeaveCriticalSection (&(pHost->m_CSec));

	}
	pHost->m_SnapFinished = true;
	pHost->SetStopTime();
	return TRUE;
}

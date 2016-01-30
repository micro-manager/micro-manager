
#ifndef _CRT_SECURE_NO_WARNINGS
  #define _CRT_SECURE_NO_WARNINGS
#endif
#include "ImageHeader.h"
#include <string.h>
#include <time.h>
#include "StringTools.h"
#include "common_constants_exp.h"

#ifndef ABS_MAX__TIME64_T
#define ABS_MAX__TIME64_T     0x793406fffi64       /* number of seconds from
                                                   00:00:00, 01/01/1970 UTC to
                                                   23:59:59. 12/31/3000 UTC */
#endif

CImageHeader::CImageHeader(void)
{
  clear();
}

CImageHeader::CImageHeader( const CImageHeader &cImgHdr )
{
  fromClass(cImgHdr);
}

CImageHeader::CImageHeader( const S_IMAGE_HEADER& sImgHdr  )
{
  fromStruct(sImgHdr);
}

CImageHeader::~CImageHeader(void)
{
}

void CImageHeader::clear()
{
  memset( this, 0, sizeof(S_IMAGE_HEADER));
}
  
void CImageHeader::fromStruct( const S_IMAGE_HEADER& sImgHdr )
{
  //clear();
  memcpy( this, &sImgHdr, sizeof(S_IMAGE_HEADER));
}

void CImageHeader::fromClass( const CImageHeader &cImgHdr )
{
  //clear();
  memcpy( this, &cImgHdr, sizeof(S_IMAGE_HEADER));
}

CImageHeader& CImageHeader::operator= ( const CImageHeader& cImgHdr )
{
  fromClass(cImgHdr);
  return *this;
}

CImageHeader& CImageHeader::operator= ( const S_IMAGE_HEADER& sImgHdr )
{
  fromStruct(sImgHdr);
  return *this;
}
 
CImageHeader::operator S_IMAGE_HEADER()
{
  return *ptr();
}

S_IMAGE_HEADER* CImageHeader::ptr()
{  
  return dynamic_cast<S_IMAGE_HEADER*>(this);
}

bool CImageHeader::operator != ( const S_IMAGE_HEADER& sImgHdr )
{
  return (memcmp( this, &sImgHdr, sizeof(S_IMAGE_HEADER) ) != 0);
}

std::string CImageHeader::getTimeStampStr(void)
{
  std::string strTimestamp;
  std::string strMilliseconds;
  const u64 qwTime_ms = getTimeStamp();
  u64 qwTime_s = qwTime_ms / 1000;
  const u32 maxTimeBufferSize = 128;

  strTimestamp.resize( maxTimeBufferSize, 0);


  if  (qwTime_s > ABS_MAX__TIME64_T)
    qwTime_s = 0;

  const __time64_t time64Time = qwTime_s;
  struct tm  stmTemp = {0};

  errno_t error = _localtime64_s( &stmTemp, &time64Time );
  if ( (0!=error) || !strftime( (char *)strTimestamp.c_str(), maxTimeBufferSize, "%Y-%m-%d_%H%M%S", &stmTemp ))
  { 
    strTimestamp[0] = '\0';
  }

  str::ResizeByZeroTermination( strTimestamp );
  str::sprintf( strMilliseconds, ":%03d", qwTime_ms%1000);
  return (strTimestamp + strMilliseconds);
}

bool CImageHeader::isMultiFieldImage() const 
{
  return ((wPayload_ext & PAYLOAD_EXT_TYPE_MULT_FIELD_IMG) == PAYLOAD_EXT_TYPE_MULT_FIELD_IMG);
}

u08 CImageHeader::currentField() const 
{
  return PE_MFI_GET_CURRENT_FIELD( wPayload_ext );
}

u08 CImageHeader::totalFields() const 
{
  return PE_MFI_GET_TOTAL_FIELDS( wPayload_ext );
}

bool CImageHeader::isImageHeaderValid( const S_RESOLUTION_CAPS * pResCaps ) const
{
  if (0 == pResCaps)
    return false;

  const u32 dwMaxTimestamp_high = (ABS_MAX__TIME64_T * 1000ull) >> 32;

  if (( dwSize_x          > pResCaps->wFullSizeX ) ||
      ( dwSize_y          > pResCaps->wFullSizeY ) ||
      ( dwTimestamp_high  > dwMaxTimestamp_high  ) ||
      ( dwOffset_x        < pResCaps->wFullOffsetX ) ||
      ( dwOffset_x        > pResCaps->wFullSizeX ) ||
      ( dwOffset_y        < pResCaps->wFullOffsetY ) ||
      ( dwOffset_y        > pResCaps->wFullSizeY ) )
  {
    return false;
  }


  return true;
}

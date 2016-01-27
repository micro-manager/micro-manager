#include "AbsImgBuffer.h"
#include "CamUSB_API_Util.h"
#include <assert.h>
#include "ImageHeader.h"

CAbsImgBuffer::CAbsImgBuffer(void)
: ImgBuffer()
, imagePtr_( 0 )
, imageHeaderPtr_( 0 )
, bufferType_( eInternBuffer )
{
  memset( &imageHeader_, 0, sizeof(imageHeader_) );
}

CAbsImgBuffer::~CAbsImgBuffer(void)
{

}

unsigned int CAbsImgBuffer::Width() const
{
  CAbsImgBuffer *myThis = const_cast<CAbsImgBuffer*> ( this );
  MMThreadGuard g( *myThis );

  if ( eInternBuffer == myThis->bufferType() )
    return __super::Width();

  return imageHeader_.dwSize_x;
}

unsigned int CAbsImgBuffer::Height() const
{
  CAbsImgBuffer *myThis = const_cast<CAbsImgBuffer*> ( this );
  MMThreadGuard g( *myThis );

  if ( eInternBuffer == myThis->bufferType() )
    return __super::Height();

  return imageHeader_.dwSize_y;
}

unsigned int CAbsImgBuffer::Depth() const
{
  CAbsImgBuffer *myThis = const_cast<CAbsImgBuffer*> ( this );
  MMThreadGuard g( *myThis );

  if ( eInternBuffer == myThis->bufferType() )
    return __super::Depth();
  
  const unsigned int depth = GetBpp( imageHeader_.dwPixel_type ) / 8;

  return max( depth, 1 );
}

const unsigned char* CAbsImgBuffer::GetPixels() 
{
  MMThreadGuard g( *this );

  if ( eInternBuffer == bufferType() )
    return __super::GetPixels();

  return imagePtr_;

}

unsigned char* CAbsImgBuffer::GetPixelsRW()
{
  MMThreadGuard g( *this );

  if ( eInternBuffer == bufferType() )
    return __super::GetPixelsRW();

  return imagePtr_;
}

bool CAbsImgBuffer::setBufferType( EBufferType eType )
{
  MMThreadGuard g( *this );

  bufferType_ = eType;

  return true;
}

CAbsImgBuffer::EBufferType CAbsImgBuffer::bufferType( )
{
  return bufferType_;
}

bool CAbsImgBuffer::getNewImgBuffer( u08* & pImg, S_IMAGE_HEADER* & pImgHdr, u32 & dwImgSize )
{
  MMThreadGuard g( *this );

  if ( eInternBuffer == bufferType() )
  {
    Lock();
    pImg      = GetPixelsRW();
    pImgHdr   = &imageHeader_;
    dwImgSize = Width() * Height() * Depth();
  }
  else
  {
    pImg      = 0;
    pImgHdr   = 0;
    dwImgSize = 0;
  }

  return true;
}

void CAbsImgBuffer::abortNewImgBuffer( )
{
  if ( eInternBuffer == bufferType() )
  {
    Unlock();
  }
}

void CAbsImgBuffer::releasNewImgBuffer( u08* pImg, S_IMAGE_HEADER* pImgHdr )
{
  MMThreadGuard g( *this );

  addUnsedImgBuffers();

  // check if buffers are the same if use internal buffers
  if ( eInternBuffer == bufferType() )
  {
    Unlock();
    assert( pImg == GetPixelsRW() );
    assert( pImgHdr == &imageHeader_ );
  }

  // store new buffer pointers
  imagePtr_       = pImg;
  imageHeaderPtr_ = pImgHdr;

  // copy image pointer
  if (( pImgHdr != &imageHeader_ ) && ( 0 != imageHeaderPtr_ ) )
    imageHeader_ = *imageHeaderPtr_;
}

bool CAbsImgBuffer::getUnusedExternalBuffer( u08* & pImg, S_IMAGE_HEADER* & pImgHdr )
{
  MMThreadGuard g( *this );

  if ( unusedImgBuffers_.size() )
  {
    const CAbsImgBufferItem& ii = unusedImgBuffers_.back( );

    pImg    = ii.image_;
    pImgHdr = ii.imageHeader_;

    unusedImgBuffers_.pop_back();

    return true;
  }
  return false;
}

void CAbsImgBuffer::addUnsedImgBuffers()
{
  // remember unused buffers
  if ( (0 != imagePtr_) && (0 != imageHeaderPtr_) )
  {
    if ( __super::GetPixelsRW() != imagePtr_ )
    {

      const CAbsImgBufferItem absImgBufferItem( imagePtr_, imageHeaderPtr_ );
      bool bAllreadyStored = false;
      for (u32 n=0; n < unusedImgBuffers_.size(); n++)
      {
        bAllreadyStored = bAllreadyStored || (unusedImgBuffers_[n] == absImgBufferItem);        
      }
      if (!bAllreadyStored)
        unusedImgBuffers_.push_back( absImgBufferItem );
    }
  }
}

void CAbsImgBuffer::updateImageHeader()
{
  imageHeader_.dwSize_x = __super::Width();
  imageHeader_.dwSize_y = __super::Height();
  switch ( __super::Depth() )
  {
  case 8:   imageHeader_.dwPixel_type = PIX_BGRA14_PACKED;  break;
  case 6:   imageHeader_.dwPixel_type = PIX_BGR16_PACKED;   break;
  case 4:   imageHeader_.dwPixel_type = PIX_BGRA8_PACKED;   break;
  case 3:   imageHeader_.dwPixel_type = PIX_BGR8_PACKED;    break;
  case 2:   imageHeader_.dwPixel_type = PIX_MONO16;         break;
  case 1:
  default:  imageHeader_.dwPixel_type = PIX_MONO8;          break;
  }
}

void CAbsImgBuffer::prepareHistogramm( unsigned bitDepth )
{
  if ( 0 != imagePtr_ )
    switch ( __super::Depth() )
    {
    case 8:
    case 6:
    case 2:
      *((u16*)imagePtr_) = ((1 << bitDepth) - 1);
      
      break;

    case 4:
    case 3:
    case 1:
    default:
      *imagePtr_ = 255;
      break;
    }
}

void CAbsImgBuffer::Resize(unsigned xSize, unsigned ySize, unsigned pixDepth, unsigned usedBit)
{
  MMThreadGuard g( *this );

  addUnsedImgBuffers();
  
  __super::Resize( xSize, ySize, pixDepth);

  updateImageHeader();

  imagePtr_       = __super::GetPixelsRW();
  imageHeaderPtr_ = &imageHeader_;

  prepareHistogramm( usedBit );
}

void CAbsImgBuffer::Resize(unsigned xSize, unsigned ySize )
{
  MMThreadGuard g( *this );

  addUnsedImgBuffers();
  
  __super::Resize( xSize, ySize);

  updateImageHeader();

  imagePtr_       = GetPixelsRW();
  imageHeaderPtr_ = &imageHeader_;

}

//! return image time stamp as u64 value in ms since 1970;
unsigned long long CAbsImgBuffer::getTimeStamp( )
{
  MMThreadGuard g( *this );
  CImageHeader cImgHdr( imageHeader_ );
  return cImgHdr.getTimeStamp();
}

//! return image time stamp as string value in ms since 1970;
string CAbsImgBuffer::getTimeStampString( )
{
  MMThreadGuard g( *this );
  CImageHeader cImgHdr( imageHeader_ );
  return cImgHdr.getTimeStampStr();
}

#pragma once
#include "ImgBuffer.h"              //!< base  class
#include "DeviceThreads.h"          //!< MMThreadLock class

// ---------------------------Camera - API ------------------------------------
#include "common_structs_exp.h"     //!< ABS Camera API structs

#include <vector>
using namespace std;


//! dual image buffer, one with internal memory / one with external memory
class CAbsImgBuffer :
  public ImgBuffer, public MMThreadLock
{
public:
  enum EBufferType
  {
    eInternBuffer,    // internal allocated image buffer
    eExternBuffer     // external allocated image buffer
  };

public:
  CAbsImgBuffer(void);
  ~CAbsImgBuffer(void);

  
  unsigned int Width() const;
  unsigned int Height() const;
  unsigned int Depth() const;

  const unsigned char* GetPixels();
  unsigned char* GetPixelsRW();

  void Resize(unsigned xSize, unsigned ySize, unsigned pixDepth, unsigned bitDepth );
  void Resize(unsigned xSize, unsigned ySize);

  //! set the buffer operation mode
  bool setBufferType( EBufferType eType );
  EBufferType bufferType( void );

  //! return a lock image buffer for writting by camera API
  bool getNewImgBuffer( u08* & pImg, S_IMAGE_HEADER* & pImgHdr, u32 & dwImgSize );

  //! abort the lock image buffer return by getNewImgBuffer => because API don't return a image
  void abortNewImgBuffer( );

  //! unlock the image buffer and use the pointer as new image buffers
  //! => old external buffer are declared as unused => call getUnusedExternalBuffer 
  //! to remove and recycle them
  void releasNewImgBuffer( u08* pImg, S_IMAGE_HEADER* pImgHdr );

  //! return internal stored and no longer used external buffer pointers to
  //! be recycled by caller
  bool getUnusedExternalBuffer( u08* & pImg, S_IMAGE_HEADER* & pImgHdr );

  //! return image time stamp as u64 value in ms since 1970;
  unsigned long long getTimeStamp( );

  //! return image time stamp as string value in ms since 1970;
  string getTimeStampString( );

private:
  S_IMAGE_HEADER  imageHeader_;
  S_IMAGE_HEADER* imageHeaderPtr_;
  u08*            imagePtr_;
  EBufferType     bufferType_;
protected:

  void  addUnsedImgBuffers();
  void  updateImageHeader();
  void  prepareHistogramm( unsigned bitDepth );
  class CAbsImgBufferItem
  {
  public:
    CAbsImgBufferItem( u08* image = 0, S_IMAGE_HEADER* imageHeader = 0 )
      : image_(image)
      , imageHeader_(imageHeader)
    {};

    CAbsImgBufferItem( const CAbsImgBufferItem & cItem )
    {
      image_ = cItem.image_;
      imageHeader_ = cItem.imageHeader_;
    };

    bool operator==(const CAbsImgBufferItem &other) const
    {
      if ((image_ == other.image_) && (imageHeader_ == other.imageHeader_))
         return true;
      else
         return false;
    }

    u08* image_;
    S_IMAGE_HEADER* imageHeader_; 
  };

  typedef vector<CAbsImgBufferItem> CABSImgBufferVector;

  CABSImgBufferVector   unusedImgBuffers_;
};


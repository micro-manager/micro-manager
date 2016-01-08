#pragma once

// -------------------------- Includes ----------------------------------------
//
#include "common_structs_exp.h"
#include <string>

// -------------------------- Class -------------------------------------------
//
class CImageHeader : public S_IMAGE_HEADER
{
public:
  //! constructor 
  CImageHeader(void);
  //! copy constructor 
  CImageHeader( const S_IMAGE_HEADER &sImgHdr );
  //! copy constructor 
  CImageHeader( const CImageHeader &cImgHdr );
  //! destructor 
  ~CImageHeader(void);

  //! assign operator
  CImageHeader& operator = ( const CImageHeader& cImgHdr );
  //! assign operator
  CImageHeader& operator = ( const S_IMAGE_HEADER& sImgHdr );
  //! operator ()
  operator S_IMAGE_HEADER();

  //! return S_IMAGE_HEADER* 
  S_IMAGE_HEADER* ptr();

  //! compare operator
  bool operator != ( const S_IMAGE_HEADER& sImgHdr );

  // return the current time stamp
  u64 getTimeStamp() const { return ((((u64)dwTimestamp_high) << 32) | dwTimestamp_low); }
  i64 getDeltaTimeStamp( u64 qwTimeStamp) const {return (getTimeStamp() - qwTimeStamp); }

  //! true if image belongs to a multi field image set
  bool  isMultiFieldImage() const;
  //! current field index of a multi field image
  u08   currentField() const;
  //! total number of fields of a multi field image
  u08   totalFields() const;

  //! true if image header appears to be valid based on camera caps
  bool isImageHeaderValid( const S_RESOLUTION_CAPS * pResCaps ) const;

  std::string getTimeStampStr(void);
  
  //! clear all data member
  void clear();
private:
  void fromStruct( const S_IMAGE_HEADER& sImgHdr );
  void fromClass( const CImageHeader &cImgHdr );
};

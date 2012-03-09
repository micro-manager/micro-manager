///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file		CCMFileStd.h
//! 
//! \brief		read / write a ColorCorrectionMatrix (CCM) - file
//! 
//! \author		ABS GmbH Jena (HBau)
//!				Copyright (C) 2008 - All Rights Reserved
//! 
//! \version	1.0
//! \date		2008/02/01 \n
//! 			 -> created \n
//! 
///////////////////////////////////////////////////////////////////////////////
#ifndef __CCMFILESTD_H__
#define __CCMFILESTD_H__

#include "datatypes.h"

// -------------------------- Structs -----------------------------------------
//
//! ColorCorretionMatrix File Content
typedef struct
{
    u16		wSensorType; //!< image sensor type, see ST_XX constants
    char    szFilter[64]; //!< Filter type
    char    szLight[64];  //!< Light type
    f32		fWB_R2G;	//!< Red  : Green Gain ratio
    f32		fWB_B2G;	//!< Blue : Green Gain ratio
    f32		fCCM[9];	//!< CCM Matrix
} S_CCM;


// -------------------------- Class -------------------------------------------
//
class CCCMFile
{
public:
    CCCMFile(void);
    ~CCCMFile(void);
    
	 // read a CCM - file
    static bool read ( char* ccmFilePath, S_CCM & ccm );

    // write sCCM to a CCM - file
    static bool write( char* ccmFilePath, const S_CCM & ccm );

	 // convert at float ccm matrix in a i16 matrix
	 static void f32Toi16( f32 * fCCM, i16 * wCCM);
	 
	 // compare the values of the matrix, if they are equal
	 static bool isEqualCCM( f32 * fCCM, i16 * wCCM );
};

#endif // __CCMFILESTD_H__
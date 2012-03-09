///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file		CCMFile.cpp
//! 
//! \brief		see header file
//! 
//! \author		ABS GmbH Jena (HBau)
//!				Copyright (C) 2010 - All Rights Reserved
//! 
//! \version	1.0
//! \date		2010/01/18 \n
//! 			 -> created \n
//! 
///////////////////////////////////////////////////////////////////////////////

#ifndef _CRT_SECURE_NO_WARNINGS
	#define _CRT_SECURE_NO_WARNINGS
	#define DEFINED_CRT_SECURE_NO_WARNINGS
#endif

// -------------------------- Includes ----------------------------------------
//
#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>   
   #define snprintf _snprintf 
   #pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#endif

#include "ccmfilestd.h"
#include "memoryinifile.h"
#include "stringtools.h"
#include <cassert>
#include "abscommontools.h"




// -------------------------- Class -------------------------------------------
//
CCCMFile::CCCMFile(void)
{
}

CCCMFile::~CCCMFile(void)
{
}
// read a CCM - file
bool CCCMFile::read ( char* ccmFilePath, S_CCM & sCCM )
{
  bool				    bRC;      
  u32				      dwVersion;  
  std::string	    strValue;
  std::string	    strFilter; 
  std::string	    strLight;  
  CMemoryIniFile  cCCMFile;    
 
  // clear current content
  memset( &sCCM, 0, sizeof(sCCM) );

  // set ccm for use
  cCCMFile.setIniFile( ccmFilePath );

  // read the version
  bRC = cCCMFile.Update( "Version", "Type", dwVersion, false);
  if ( false == bRC ) 
    dwVersion = 1; // default version
    
  // read sensor type
  bRC = cCCMFile.Update( "Sensor", "Type", strValue, false);
    
  if ( true == bRC )
  {
    sCCM.wSensorType = str::hexTo<u16>( strValue );

    // read filter type
    if ( false == cCCMFile.Update( "Filter", "Type", strFilter, false) )
      strFilter = "?";
    // copy filter name        
    strncpy(sCCM.szFilter, strFilter.c_str(), min( sizeof(sCCM.szFilter) - 1, strFilter.size() ) );

    // read light type
    if ( false == cCCMFile.Update( "Light", "Type", strLight, false) )
    {
      // value not set use file name
      strLight = ABSTools::getBaseFilename( ccmFilePath );
    }
    // copy light name        
    strncpy(sCCM.szLight, strLight.c_str(), min( sizeof(sCCM.szLight) - 1, strLight.size() ) );

    // read red to green ratio
    if (!cCCMFile.Update( "wb", "fWB_R2G", sCCM.fWB_R2G, false))	
      sCCM.fWB_R2G = 1.0f;

    // read blue to green ratio
    if (!cCCMFile.Update( "wb", "fWB_B2G", sCCM.fWB_B2G, false)) 
      sCCM.fWB_B2G = 1.0f;

    // read matrix
    switch (dwVersion)
    {
    case 3:
      bRC &= cCCMFile.Update( "CCM_RGB", "R11", sCCM.fCCM[0], false);
      bRC &= cCCMFile.Update( "CCM_RGB", "R12", sCCM.fCCM[1], false);
      bRC &= cCCMFile.Update( "CCM_RGB", "R13", sCCM.fCCM[2], false);
      bRC &= cCCMFile.Update( "CCM_RGB", "G21", sCCM.fCCM[3], false);
      bRC &= cCCMFile.Update( "CCM_RGB", "G22", sCCM.fCCM[4], false);
      bRC &= cCCMFile.Update( "CCM_RGB", "G23", sCCM.fCCM[5], false);
      bRC &= cCCMFile.Update( "CCM_RGB", "B31", sCCM.fCCM[6], false);
      bRC &= cCCMFile.Update( "CCM_RGB", "B32", sCCM.fCCM[7], false);
      bRC &= cCCMFile.Update( "CCM_RGB", "B33", sCCM.fCCM[8], false);
      break;

    case 2:
      bRC &= cCCMFile.Update( "CCM", "R11", sCCM.fCCM[0], false);
      bRC &= cCCMFile.Update( "CCM", "R12", sCCM.fCCM[1], false);
      bRC &= cCCMFile.Update( "CCM", "R13", sCCM.fCCM[2], false);
      bRC &= cCCMFile.Update( "CCM", "G21", sCCM.fCCM[3], false);
      bRC &= cCCMFile.Update( "CCM", "G22", sCCM.fCCM[4], false);
      bRC &= cCCMFile.Update( "CCM", "G23", sCCM.fCCM[5], false);
      bRC &= cCCMFile.Update( "CCM", "B31", sCCM.fCCM[6], false);
      bRC &= cCCMFile.Update( "CCM", "B32", sCCM.fCCM[7], false);
      bRC &= cCCMFile.Update( "CCM", "B33", sCCM.fCCM[8], false);
      break;

    case 1: // no break use default
    default:
      {
        CStdStringLst strSectionData;
        bRC = cCCMFile.getSection( "CCM", strSectionData );
        if ( true == bRC )
        {   
          for ( i32 i=0; i < 9; i++ )
          {
            sCCM.fCCM[i] = str::floatTo<f32>( strSectionData[i] );
          }
        }
      }
      break;  
    }
  }

  // update old version to the new one
  if ( true == bRC )    
  {
    switch(dwVersion)
    {
    case 3: // Version 3 do nothing
      break;

    case 2: // Version 2 (obsolete)
    case 1: // Version 1 (obsolete)
      DeleteFileA( ccmFilePath );
      write( ccmFilePath, sCCM ); // try to convert file type (will be compatible to version 1 and 2)
      break;

    default:		
      assert(false); // unknown version detected!!!
      break;
    }   
  }
	return bRC;
}

// write sCCM to a CCM - file use only version 3
bool CCCMFile::write ( char* ccmFilePath, const S_CCM & sCCM )
{
  bool				    bRC = true;      
  u32				      dwVersion     = 3;  
  std::string	    strFilter     = std::string( sCCM.szFilter ); 
  std::string	    strLight      = std::string( sCCM.szLight );  
  std::string     strSensorType = str::asHex<u16>( sCCM.wSensorType );
  std::string	    strValue;  
  CStdStringLst   strSectionData;
  CMemoryIniFile  cCCMFile;    

  // setup CCM version 1
  for ( i32 i=0; i < 9; i++ )
  {
    str::sprintf( strValue, "%1.3f \n", sCCM.fCCM[i] );
    strSectionData.push_back( strValue );    
  }
  
  // set file path for ccm - file
  cCCMFile.setIniFile( ccmFilePath ); 

  if (bRC) bRC = cCCMFile.Update( "version",  "type",     dwVersion,    true); // write version
  if (bRC) bRC = cCCMFile.Update( "sensor",   "type",     strSensorType,true); // write sensor type
  if (bRC) bRC = cCCMFile.Update( "filter",   "type",     strFilter,    true); // write filter type
  if (bRC) bRC = cCCMFile.Update( "light",    "type",     strLight,     true); // write light info
  if (bRC) bRC = cCCMFile.Update( "wb",       "fWB_R2G",  (float&)sCCM.fWB_R2G, true); // write red/green gain channel ratio
  if (bRC) bRC = cCCMFile.Update( "wb",       "fWB_B2G",  (float&)sCCM.fWB_B2G, true); // write blue/green gain channel ratio

  // write CCM like version 1
  if (bRC) bRC = cCCMFile.setSection( "ccm", strSectionData );

  // write CCM like version 2
  if (bRC) bRC = cCCMFile.Update( "ccm_rgb", "R11", (float&)sCCM.fCCM[0], true);
  if (bRC) bRC = cCCMFile.Update( "ccm_rgb", "R12", (float&)sCCM.fCCM[1], true);
  if (bRC) bRC = cCCMFile.Update( "ccm_rgb", "R13", (float&)sCCM.fCCM[2], true);
  if (bRC) bRC = cCCMFile.Update( "ccm_rgb", "G21", (float&)sCCM.fCCM[3], true);
  if (bRC) bRC = cCCMFile.Update( "ccm_rgb", "G22", (float&)sCCM.fCCM[4], true);
  if (bRC) bRC = cCCMFile.Update( "ccm_rgb", "G23", (float&)sCCM.fCCM[5], true);
  if (bRC) bRC = cCCMFile.Update( "ccm_rgb", "B31", (float&)sCCM.fCCM[6], true);
  if (bRC) bRC = cCCMFile.Update( "ccm_rgb", "B32", (float&)sCCM.fCCM[7], true);
  if (bRC) bRC = cCCMFile.Update( "ccm_rgb", "B33", (float&)sCCM.fCCM[8], true);    
  
  return bRC;
}

// ----------------------------------------------------------------------------

void CCCMFile::f32Toi16( f32 * fCCM, i16 * wCCM)
{
	for(int i=0; i < 9; i++)
	{
		wCCM[i] = (i16) (fCCM[i] * 1000.0f + 0.5f);
	}
}
	 
// ----------------------------------------------------------------------------

bool CCCMFile::isEqualCCM( f32 * fCCM, i16 * wCCM )
{
	i16 ccm[9];

	f32Toi16( fCCM, ccm );

	return ( memcmp( &ccm, wCCM, sizeof(ccm) ) == 0);
}

// ----------------------------------------------------------------------------

#ifdef DEFINED_CRT_SECURE_NO_WARNINGS
	#undef _CRT_SECURE_NO_WARNINGS
#endif
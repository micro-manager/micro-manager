///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file		ABSCommonTools.h
//! 
//! \brief		
//! 
//! \author		ABS GmbH Jena (HBau)
//!				Copyright (C) 2009 - All Rights Reserved
//! 
//! \version	1.0
//! \date		2009/08/06 \n
//! 			 -> created \n
//! 
///////////////////////////////////////////////////////////////////////////////
#ifndef _ABSCOMMONTOOLS_H_
#define _ABSCOMMONTOOLS_H_

#ifndef _CRT_SECURE_NO_WARNINGS
  #define _CRT_SECURE_NO_WARNINGS  // disable warning C4996
#endif

#if _MSC_VER < 1400
	#pragma warning(disable: 4786)
#endif
#include "Windows.h"

#if _MSC_VER	< 1300
    typedef enum {
        SHGFP_TYPE_CURRENT  = 0,   // current value for user, verify it exists
        SHGFP_TYPE_DEFAULT  = 1,   // default value, may not exist
    } SHGFP_TYPE;
  #ifndef CSIDL_COMMON_APPDATA
	#define CSIDL_COMMON_APPDATA            0x0023        // All Users\Application Data	
  #endif
#else
	#include "shlobj.h"
#endif

#include <vector>
#include <string>
typedef std::vector<std::string> CStdStringLst;

namespace ABSTools
{
	enum EOSVersionFlags
	{
		EOS_None,		// os must match exactly
		EOS_OrHigher,	// requested os or a newer one		
		EOS_OrLess		// requested os or a older one		
	};

    // return true on Windows9x systems
    bool IsWindows9x(EOSVersionFlags eFlag = EOS_None);
    // return true on WindowsNT systems
    bool IsWindowsNT(EOSVersionFlags eFlag = EOS_None);
	// return true on WindowsXP systems
    bool IsWindowsXP(	EOSVersionFlags eFlag = EOS_None);
	// return true on Windows2000 systems
	bool IsWindows2000(	EOSVersionFlags eFlag = EOS_None);
	// return true on Vista systems
	bool IsWindowsVista(EOSVersionFlags eFlag = EOS_None);
	// return true on Windos7 systems
	bool IsWindows7(	EOSVersionFlags eFlag = EOS_None);

	// return true if a windows GUI theme is active
	bool IsWindowsThemeActive(void);

	// return true if a localized version of the path is available
	bool GetLocalizedName( WCHAR*,  WCHAR*, UINT);
	// return true if the resource string was load from the resource file wzSrcFile by it ID (nID) and stored at wzDst
	bool LoadStringFromResource(WCHAR* wzSrcFile, int nID, WCHAR* wzDst, UINT nDstSize);
	
	// convert an non localized path to a localized one (OS: Vista and above)
	bool PathToLocalizedPath( WCHAR* wzSrcPath, WCHAR* wzDstPath, UINT nDstSize );
	bool PathToLocalizedPath( char* szSrcPath, char* szDstPath, UINT nDstSize );
	
    // return the OSVERSIONINFOEX structure
    bool GetOsVersion(OSVERSIONINFOEX&);

	// return the current application path
	char* GetAppPath( char*, int ); 
	// Application name without extension
	char* GetAppName( char*, int ); 
	// Application name plus extension
	char* GetAppNameFull( char*, int ); 
    // create an folder and all needed subdirectory
    bool CreateFolder(char*);
    bool CreateFolder( WCHAR* );

    bool ChangeACLtoAllowUserRW( char* );
    bool ChangeACLtoAllowUserRW( WCHAR* );

    bool Sid2Name(BYTE auth, BYTE count, DWORD sa0, DWORD sa1, DWORD sa2, DWORD sa3, DWORD sa4, DWORD sa5, DWORD sa6, DWORD sa7, PWCHAR &wzSidName);

	// case sensitive wild char compare
    bool WildCmp(const char *wild, const char *string);

	bool GetFolderPath( int nFolder, char*  szPath, UINT nSize, DWORD dwFlag = SHGFP_TYPE_CURRENT);
	bool GetFolderPath( int nFolder, WCHAR* wzPath, UINT nSize, DWORD dwFlag = SHGFP_TYPE_CURRENT);
	
	BYTE  range_b  (int const nValue, int const nMin, int const nMax);
	WORD  range_w  (int const nValue, int const nMin, int const nMax);
    DWORD range_dw (int const nValue, int const nMin, int const nMax);
	BYTE  round_f_range_b (float const fValue,  int const nMin, int const nMax);    
    WORD  round_d_range_w (double const dValue, int const nMin, int const nMax);

    int   round( float const x );    // Round to nearest integer;
    int   round( double const x );    // Round to nearest integer;


    void IdentityMatrix( float* pfMatrix, int nWidth, int nHeight );
    void MultiplyMatrix( float* pfSrc1, float* pfSrc2, float* pfDst, int nXYDim );

    void IdentityMatrix( double* pfMatrix, int nWidth, int nHeight );
    void MultiplyMatrix( double* pfSrc1, double* pfSrc2, double* pfDst, int nXYDim );

  unsigned int GetShell32DllVersion( void );


  // return the number of bits set to 1 at the passed BitMask values
  unsigned int  getBitCount( unsigned int dwBitMask );
  unsigned int  getBitCount( unsigned long long ulBitMask );

  std::string   getBaseFilename( const std::string &  strFilePath );
  std::string   getFileExtension( const std::string & strFilePath );
  std::string   getFilename( const std::string &  strFilePath );
  std::string   getFileDir( const std::string & strFilePath );
  unsigned int  findFiles( const std::string & strFileMask, CStdStringLst & strFiles );

  // return the current application path
  std::string   getAppPath( );
  std::string   getQualified( const std::string & strFilePath );

}


#endif // _ABSCOMMONTOOLS_H_

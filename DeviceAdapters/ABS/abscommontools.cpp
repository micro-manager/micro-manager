///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file		ABSCommonTools.cpp
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

// ----------------------------------------------------------------------------
//
//! Visual studio version defines
// _MSC_VER	= 1500	VC9.0 (Visual Studio 2008)
// _MSC_VER	= 1400	VC8.0 (Visual Studio 2005)
// _MSC_VER	= 1310	VC7.1 (Visual Studio .Net 2003)
// _MSC_VER	= 1300	VC7.0 (Visual Studio 2002)
// _MSC_VER	= 1200	VC6.0  (Visual Studio C++ 6.0)
// _MSC_VER	= 1100	VC5.0  (Visual Studio C++ 5.0)
//

// set winver if not allready set
#ifndef WINVER
	#define WINVER  0x0500
#endif

#include "ABSCommonTools.h"
#include "SafeUtil.h"
#include "shlobj.h"
#include "shlwapi.h"
#pragma comment(lib, "shlwapi.lib")
#pragma message("Automatically linking with shlwapi.lib")
#include "Aclapi.h"
#include <string>
#include <vector>
#include "StringTools.h"

//#include "Windows.h"

//Library: Use Advapi32.lib.

#if WINVER > 0x0500
	#include "uxtheme.h"
	#pragma comment(lib, "UxTheme.lib")
	#pragma message("Automatically linking with UxTheme.lib")
#endif

#if WINVER >= 0x0600
	#include "shellapi.h"
#endif

// -------------------------- Defines -----------------------------------------
//
typedef BOOL	(CALLBACK* LPIsThemeActive)( VOID );
typedef HRESULT (CALLBACK* LPSHGetLocalizedName)( LPCWSTR, LPWSTR, UINT, int*);
typedef HRESULT (CALLBACK* LPSHGetFolderPathW)( HWND, int, HANDLE, DWORD, LPWSTR );
typedef int		(CALLBACK* LPSHCreateDirectoryExW)( HWND, LPCWSTR, LPSECURITY_ATTRIBUTES);
						




// ----------------------------------------------------------------------------
//
bool ABSTools::IsWindows7(EOSVersionFlags eFlag)
{
  bool bRC = false;
	OSVERSIONINFOEX sOSVerEx;
	if (ABSTools::GetOsVersion(sOSVerEx))
	{
		if (sOSVerEx.dwPlatformId == VER_PLATFORM_WIN32_NT)
		{
			switch (eFlag)
			{
			case EOS_OrHigher:
				bRC =  (sOSVerEx.dwMajorVersion >= 6) ||
					  ((sOSVerEx.dwMajorVersion == 6) &&
					   (sOSVerEx.dwMinorVersion >= 1));	

				break;

			case EOS_OrLess:
				bRC = (sOSVerEx.dwMajorVersion <  6) ||  
					 ((sOSVerEx.dwMajorVersion == 6) && 
					  (sOSVerEx.dwMinorVersion <= 1));	
				break;
			default:
				bRC = (sOSVerEx.dwMajorVersion == 6) && 
					  (sOSVerEx.dwMinorVersion == 1);					
				break;
			}

      if (!bRC)
        bRC = (GetShell32DllVersion() > 0x06010000); // 6.1.xxxx Windows 7
		}
	}
	return bRC;
}

bool ABSTools::IsWindowsVista(EOSVersionFlags eFlag)
{
  bool bRC = false;
	OSVERSIONINFOEX sOSVerEx;
	if (ABSTools::GetOsVersion(sOSVerEx))
	{
		if (sOSVerEx.dwPlatformId == VER_PLATFORM_WIN32_NT)
		{
			switch (eFlag)
			{
			case EOS_OrHigher:
				bRC = (sOSVerEx.dwMajorVersion >= 6);        
				break;

			case EOS_OrLess:
				bRC =  (sOSVerEx.dwMajorVersion <  6) ||  
					  ((sOSVerEx.dwMajorVersion == 6) && 
					   (sOSVerEx.dwMinorVersion == 0));	
				break;
			default:
				bRC = (sOSVerEx.dwMajorVersion == 6) && 
					  (sOSVerEx.dwMinorVersion == 0);					
				break;
			}

      if (!bRC) // vista test
        bRC = (GetShell32DllVersion() > 0x06001000); // 6.0.1xxx Vista
		}
	}
	return bRC;
}

bool ABSTools::IsWindowsXP(EOSVersionFlags eFlag)
{
  bool bRC = false;
    OSVERSIONINFOEX sOSVerEx;
    if (ABSTools::GetOsVersion(sOSVerEx))
    {
        if (sOSVerEx.dwPlatformId == VER_PLATFORM_WIN32_NT)
		{
			switch (eFlag)
			{
			case EOS_OrHigher:
				bRC =  (sOSVerEx.dwMajorVersion >  5) || 
					  ((sOSVerEx.dwMajorVersion == 5) && 
					   (sOSVerEx.dwMinorVersion <= 2) &&
					   (sOSVerEx.dwMinorVersion >= 1)) ;
				break;

			case EOS_OrLess:
				bRC =   (sOSVerEx.dwMajorVersion == 5) && 
					    (sOSVerEx.dwMinorVersion <= 2) &&
						(sOSVerEx.dwMinorVersion >= 0);
				break;
			default:
				bRC = (sOSVerEx.dwMajorVersion == 5) && 
					  (sOSVerEx.dwMinorVersion <= 2) &&
					  (sOSVerEx.dwMinorVersion >= 1);
				break;
			}
		}
    }
    return bRC;
}

bool ABSTools::IsWindows2000(EOSVersionFlags eFlag)
{
  bool bRC = false;
	OSVERSIONINFOEX sOSVerEx;
	if (ABSTools::GetOsVersion(sOSVerEx))
	{
		if (sOSVerEx.dwPlatformId == VER_PLATFORM_WIN32_NT)
		{
			switch (eFlag)
			{
			case EOS_OrHigher:
				bRC = (sOSVerEx.dwMajorVersion >=  5);
				break;

			case EOS_OrLess:
				bRC = (sOSVerEx.dwMajorVersion <  5) ||
					 ((sOSVerEx.dwMajorVersion == 5) &&
					  (sOSVerEx.dwMinorVersion == 0));
				break;
			default:
				bRC =(sOSVerEx.dwMajorVersion == 5) &&
					 (sOSVerEx.dwMinorVersion == 0);
				break;
			}
		}
	}
	return bRC;
}

bool ABSTools::IsWindowsNT(EOSVersionFlags eFlag)
{
  bool bRC = false;
  OSVERSIONINFOEX sOSVerEx;
  if (ABSTools::GetOsVersion(sOSVerEx))
  {
    if (sOSVerEx.dwPlatformId == VER_PLATFORM_WIN32_NT)
    {
      switch (eFlag)
      {
      case EOS_OrHigher:
        bRC = (sOSVerEx.dwMajorVersion >=  4);
        break;

      case EOS_OrLess:
        bRC = (sOSVerEx.dwMajorVersion <= 4);
        break;
      default:
        bRC =(sOSVerEx.dwMajorVersion <= 4);
        break;
      }
    }
  }
  return bRC;
}

bool ABSTools::IsWindows9x(EOSVersionFlags eFlag)
{
  bool bRC = false;
  OSVERSIONINFOEX sOSVerEx;
  if (ABSTools::GetOsVersion(sOSVerEx))
  {
    switch (eFlag)
    {
    case EOS_OrHigher:
      bRC = (sOSVerEx.dwPlatformId == VER_PLATFORM_WIN32_WINDOWS) ||
            (sOSVerEx.dwPlatformId == VER_PLATFORM_WIN32_NT);
      break;

    case EOS_OrLess:
      bRC = (sOSVerEx.dwPlatformId == VER_PLATFORM_WIN32_WINDOWS);
      break;
    default:
      bRC = (sOSVerEx.dwPlatformId == VER_PLATFORM_WIN32_WINDOWS);
      break;
    }
  }
  return bRC;
}

bool ABSTools::GetOsVersion(OSVERSIONINFOEX &sOSVerEx)
{
    bool bOsVersionInfoEx = false;

    // Try calling GetVersionEx using the OSVERSIONINFOEX structure.
    // If that fails, try using the OSVERSIONINFO structure.
    memset(&sOSVerEx, 0, sizeof(OSVERSIONINFOEX));
    sOSVerEx.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);

    bOsVersionInfoEx = ( TRUE == GetVersionEx( (OSVERSIONINFO *) &sOSVerEx ) );

    if ( false == bOsVersionInfoEx )
    {
      // OSVersionEx - Struct wird nicht unterstützt!
      // lese die default Struct...
      sOSVerEx.dwOSVersionInfoSize = sizeof (OSVERSIONINFO);
      if (!GetVersionEx ( (OSVERSIONINFO *) &sOSVerEx) ) 
      {
        return false;
      }
    }
    return bOsVersionInfoEx;
}
  
bool ABSTools::CreateFolder(char* szPath)
{
    WCHAR   wzPath[2*MAX_PATH+2] = {0};    
    mbstowcs( wzPath, szPath, 2*MAX_PATH);    
    return ABSTools::CreateFolder( (WCHAR*) wzPath);
}

bool ABSTools::CreateFolder( WCHAR* wzPath)
{
	int nRC;
	#if _MSC_VER < 1310   		
		HINSTANCE hShell32;
		LPSHCreateDirectoryExW SHCreateDirectoryExW = NULL;
	
		hShell32 = LoadLibraryA( "Shell32.dll");
		if (NULL != hShell32)
		{
			SHCreateDirectoryExW = (LPSHCreateDirectoryExW) GetProcAddress( hShell32, "SHCreateDirectoryExW" );
			
			if (NULL != SHCreateDirectoryExW) 
			{
				#ifndef __GNUWIN32__
				__try
				#endif
				{
					nRC = SHCreateDirectoryExW( NULL, wzPath, NULL );
				}
				#ifndef __GNUWIN32__
				__except(GetExceptionCode() == EXCEPTION_ACCESS_VIOLATION ? EXCEPTION_EXECUTE_HANDLER : EXCEPTION_CONTINUE_SEARCH)
				{
					nRC = ERROR_CANCELLED;						
				}
				#endif
			}
		}
			
		FreeLibrary(hShell32);
	#else
		nRC = SHCreateDirectoryExW( NULL, wzPath, NULL );
	#endif

    if ((nRC == ERROR_SUCCESS) ||
        (nRC == ERROR_FILE_EXISTS) ||
        (nRC == ERROR_ALREADY_EXISTS) )
    {
        return true;
    }
   
    return false;            
}

bool ABSTools::ChangeACLtoAllowUserRW( char* szDir )
{
    WCHAR   wzDir[2*MAX_PATH+2] = {0};
    mbstowcs( wzDir, szDir, 2*MAX_PATH);
    return ABSTools::ChangeACLtoAllowUserRW( (WCHAR*) wzDir);

}

bool ABSTools::Sid2Name(BYTE auth, BYTE count, DWORD sa0, DWORD sa1, DWORD sa2, DWORD sa3, DWORD sa4, DWORD sa5, DWORD sa6, DWORD sa7, PWCHAR &wzSidName)
{
    bool bRC = false;
    // SID's look like S-version-auth-count-sub0-...subn
    // For an SID of S-1-a-b-c-d
    // call sid2name(a, b, c, d,0,0,0,0,0,0, szName)
    // for example, S-1-5-32-544
    // would be sid2name(5,2,32,544,0,0,0,0,0,0,szName)

    SID_IDENTIFIER_AUTHORITY sSia = {0};
    SID_NAME_USE eUse = (SID_NAME_USE)0;
    PSID pSID;
    WCHAR wzName[1024]; 
    WCHAR wzReferencedDomainName[1024];
    DWORD cchName, cchReferencedDomainName;
    DWORD nRC;
    
    
    sSia.Value[5] = auth; // set it to the requested authority
    nRC = AllocateAndInitializeSid(&sSia,count,sa0,sa1,sa2,sa3,sa4,sa5,sa6,sa7,&pSID);

    // if allocate succeeded
    if (nRC != 0) 
    {
        cchName = CNT_ELEMENTS(wzName); // will hold the name
        cchReferencedDomainName = CNT_ELEMENTS(wzReferencedDomainName);

        nRC = LookupAccountSidW(0, pSID, wzName, &cchName, wzReferencedDomainName, &cchReferencedDomainName, &eUse);

        if (nRC != 0) 
        {
            // if it was successful, then set caller's string
            int nSize = (int) wcslen(wzName)+2;
            wzSidName = new WCHAR[nSize];
            memset(wzSidName, 0, sizeof(WCHAR) * nSize);            
            wcscpy(wzSidName, wzName);
            bRC = true;
        }
    }    
    // nResult = Err.LastDllError; for debugging: 1332 means it can't do the lookup
    FreeSid(pSID);
    return bRC;
}

bool ABSTools::ChangeACLtoAllowUserRW( WCHAR* strDir )
{    
    EXPLICIT_ACCESS_W explicitaccess;
    PACL NewAcl = NULL;
    DWORD dwError;
    
    
    WCHAR* wzName = NULL;
/*
    WCHAR* wzRefDomain = NULL;
    DWORD  nNameSize = 0;
    DWORD  nRefDomainSize = 0;        
    SID_IDENTIFIER_AUTHORITY siaWorldSidAuthority={0};
    PSID psidWorldSid = (PSID) LocalAlloc(LPTR, GetSidLengthRequired(1) );
    BOOL bRC = InitializeSid( psidWorldSid, &siaWorldSidAuthority,  1);
    *(GetSidSubAuthority(psidWorldSid, 0)) =   SECURITY_WORLD_RID;
*/
    /*
    // S-1-1-0 = Everyone
    sid2name(WORLD_AUTH,1,0,0,0,0,0,0,0,0,szEveryone);

    // Aministrators is S-1-5-32-544
    //SECURITY_BUILTIN_DOMAIN_RID = 0x20 (32), DOMAIN_ALIAS_RID_ADMINS = 0x220 (544)
    sid2name(NT_AUTH, 2, SECURITY_BUILTIN_DOMAIN_RID, DOMAIN_ALIAS_RID_ADMINS, 0, 0, 0, 0, 0, 0, szAdministrators); // Administators

    // SID: S-1-5-32-545 is users (0x221)
    sid2name(5, 2, SECURITY_BUILTIN_DOMAIN_RID, DOMAIN_ALIAS_RID_USERS, 0, 0, 0, 0, 0, 0, szUsers);
    */
    // SID: S-1-5-32-545 is users (0x221)
    ABSTools::Sid2Name(5, 2, SECURITY_BUILTIN_DOMAIN_RID, DOMAIN_ALIAS_RID_USERS, 0, 0, 0, 0, 0, 0, wzName);

    if (NULL == wzName) return false;
   
    BuildExplicitAccessWithNameW( &explicitaccess, 
                                 wzName,
                                 GENERIC_ALL, GRANT_ACCESS,
                                 SUB_CONTAINERS_AND_OBJECTS_INHERIT );

    dwError = SetEntriesInAclW( 1, &explicitaccess, NULL, &NewAcl );

    SAFE_DELETE_ARRAY(wzName);

    if( dwError == ERROR_SUCCESS) 
    {
        dwError = SetNamedSecurityInfoW( strDir, SE_FILE_OBJECT,
                                         DACL_SECURITY_INFORMATION,
                                         NULL, NULL, NewAcl, NULL );
        if( dwError == ERROR_SUCCESS)
        {
            if( NewAcl != NULL ) AccFree( NewAcl );
            return true;
        }
    }

    if( NewAcl != NULL ) AccFree( NewAcl );
    return false;
}

// return the current application path
char* ABSTools::GetAppPath( char* szAppPath, int nAppPathSize )
{
	if (0 != ::GetModuleFileNameA( NULL, szAppPath, nAppPathSize ))
	{
		::PathRemoveFileSpecA(szAppPath);		
		return szAppPath;
	}
	else
	{
		return NULL;
	}
}
// return the current application name plus extension
char* ABSTools::GetAppNameFull( char* szAppName, int nAppNameSize )
{
	if ((NULL == szAppName) || (nAppNameSize <= 1)) return NULL;

	char  szAppPath[2*MAX_PATH+2] = {0};

	if (0 != ::GetModuleFileNameA( NULL, szAppPath, 2*MAX_PATH ))
	{
		char* szTmp = NULL;
		szTmp = PathFindFileNameA( szAppPath );		
		strncpy(szAppName, szTmp, min((int)(strlen(szTmp)+1), (nAppNameSize-1)) );
		szAppName[nAppNameSize-1] = '\0';
		return szAppName;		
	}
	else
	{
		return NULL;
	}
}
// return the current application path
char* ABSTools::GetAppName( char* szAppName, int nAppNameSize )
{
	char  szTmp[MAX_PATH+2] = {0};
	if (NULL != GetAppNameFull( szTmp, MAX_PATH ))
	{
		PathRemoveExtensionA( szTmp );	
		strncpy(szAppName, szTmp, min((int)(strlen(szTmp)+1), (nAppNameSize-1)) );
		szAppName[nAppNameSize-1] = '\0';
		return szAppName;
	}
	else
	{
		return NULL;
	}
}

// return true if a windows GUI theme is active
bool ABSTools::IsWindowsThemeActive(void)
{
	bool bThemeActive = false;
	if (IsWindowsXP( ABSTools::EOS_OrHigher ))
	{
		#if WINVER <= 0x0500
			HINSTANCE hUxTheme;
			LPIsThemeActive IsThemeActive = NULL;

			hUxTheme = LoadLibraryA( "UxTheme.dll");
			if (NULL != hUxTheme)
			{
				IsThemeActive = (LPIsThemeActive) GetProcAddress( hUxTheme, "IsThemeActive" );

				if (NULL != IsThemeActive) 
				{
					#ifndef __GNUWIN32__
					__try
					#endif
					{
						bThemeActive = (IsThemeActive() == TRUE);
					}
					#ifndef __GNUWIN32__
					__except(GetExceptionCode() == EXCEPTION_ACCESS_VIOLATION ? EXCEPTION_EXECUTE_HANDLER : EXCEPTION_CONTINUE_SEARCH)
					{
						bThemeActive = false;						
					}
					#endif
				}

				FreeLibrary(hUxTheme);
			}			
		#else // windows XP or higher compile switch
			bThemeActive = (IsThemeActive() == TRUE);
		#endif		
	}

	return bThemeActive;
}


// return true if a localized version of the path is available
bool ABSTools::GetLocalizedName( WCHAR* wzSrc,  WCHAR* wzDst, UINT nDstSize)
{
	bool  bLocalized = false;
	WCHAR wzTmp[2*MAX_PATH+2] = {0};
	UINT  nTmpSize = 2*MAX_PATH;
	int	  nID = 0;

	if (IsWindowsVista( ABSTools::EOS_OrHigher ))
	{
    #if WINVER < 0x0601
			HINSTANCE hShell32;			
			LPSHGetLocalizedName SHGetLocalizedName = NULL;

			hShell32 = LoadLibraryW( L"Shell32.dll");
			if (NULL != hShell32)
			{
				SHGetLocalizedName = (LPSHGetLocalizedName) GetProcAddress( hShell32, "SHGetLocalizedName" );

				if (NULL != SHGetLocalizedName) 
				{
					#ifndef __GNUWIN32__
					__try
					#endif
					{						
						bLocalized = (SHGetLocalizedName( wzSrc,  wzTmp, nTmpSize, &nID) == S_OK);						
					}
					#ifndef __GNUWIN32__
					__except(GetExceptionCode() == EXCEPTION_ACCESS_VIOLATION ? EXCEPTION_EXECUTE_HANDLER : EXCEPTION_CONTINUE_SEARCH)
					{
						bLocalized = false;						
					}
					#endif
				}

				FreeLibrary(hShell32);
			}			
		#else // windows Vista or higher compile switch
				bLocalized = (SHGetLocalizedName( wzSrc, wzTmp, nTmpSize, &nID) == S_OK);
		#endif		

		if (bLocalized) 
		{
			bLocalized = ABSTools::LoadStringFromResource(wzTmp, nID, wzDst, nDstSize);
		}
	}
	return bLocalized;
}

bool ABSTools::LoadStringFromResource(WCHAR* wzSrcFile, int nID, WCHAR* wzDst, UINT nDstSize)
{
	bool		bRC = false;
	WCHAR		wzTmp[2*MAX_PATH+2] = {0};					
	UINT		nTmpSize = 2*MAX_PATH;
	HINSTANCE	hLocalized = NULL;

	bRC = (ExpandEnvironmentStringsW(wzSrcFile, wzTmp, nTmpSize) != 0);

	if (bRC)
	{
		hLocalized = LoadLibraryExW( wzTmp, NULL, DONT_RESOLVE_DLL_REFERENCES | LOAD_LIBRARY_AS_DATAFILE);

		if (NULL != hLocalized)
		{	
			bRC = (LoadStringW(hLocalized, nID, wzDst, nDstSize) != 0);								
			FreeLibrary(hLocalized);
		}
	}			
	return bRC;
}

bool ABSTools::PathToLocalizedPath( char* szSrcPath, char* szDstPath, UINT nDstSize )
{
	bool	bRC = false;
	WCHAR*  wzSrcPath = NULL;
	WCHAR*	wzDstPath = NULL;
	size_t	nSrcSize  = 2 * MAX_PATH;


#if _MSC_VER > 1310   
    nSrcSize = strnlen(szSrcPath, nSrcSize);
#else
    nSrcSize = strlen(szSrcPath);
#endif
    wzSrcPath = new WCHAR[ nSrcSize + 2];
	if (NULL == wzSrcPath) goto PathToLocalizedPath_ENDE;
	
	memset( wzSrcPath, 0, sizeof(WCHAR) * (nSrcSize + 2));
	mbstowcs( wzSrcPath, szSrcPath, nSrcSize);   

	wzDstPath = new WCHAR[ nDstSize + 2];
	if (NULL == wzDstPath) goto PathToLocalizedPath_ENDE;
	memset( wzDstPath, 0, sizeof(WCHAR) * (nDstSize + 2));

	bRC = ABSTools::PathToLocalizedPath( wzSrcPath, wzDstPath, nDstSize );
	
	if (bRC)
	{
		wcstombs(szDstPath, wzDstPath, nDstSize);
	}

PathToLocalizedPath_ENDE:

	SAFE_DELETE_ARRAY(wzSrcPath);
	SAFE_DELETE_ARRAY(wzDstPath);

	return bRC;
}

bool ABSTools::PathToLocalizedPath( WCHAR* wzSrcPath, WCHAR* wzDstPath, UINT nDstSize )
{
  bool          bRC = false;
  LPWSTR        wzExt = NULL;
  int           nDriveNr = -1;
  std::wstring  wstrFileName;
  std::wstring  wstrSrcPath = std::wstring(wzSrcPath);
  std::wstring  wstrDstPath;
  WCHAR*        wzSrc;
  WCHAR         wzSrcNew[MAX_PATH] = {0};
  std::vector<std::wstring> wzPathList;

  // invalid destination buffer size
  if (0 == nDstSize)
    return false; 

  wzSrc = (WCHAR*) wstrSrcPath.c_str();

  // get the drive number
  nDriveNr = PathGetDriveNumberW( wzSrc );

  // it is not possible to localize this path abort (if driver letter is invalid)
  if (nDriveNr == -1)
   return false; 

  wzSrc = PathSkipRootW( wzSrc );
  wzExt = PathFindExtensionW(wzSrc);
  if (NULL != wzExt) // store and remove file informations from path
  {
    wstrFileName = std::wstring( PathFindFileNameW( wzSrc ) );
    PathRemoveFileSpecW( wzSrc );
  }
  else
  {

#if _MSC_VER < 1300
    wstrFileName.erase(wstrFileName.begin(), wstrFileName.end());
#else
    wstrFileName.clear();
#endif

  }

  while ( !PathIsFileSpecW(wzSrc) )
  {
    const std::wstring strTmp = std::wstring( PathFindFileNameW(wzSrc) ) ;
    wzPathList.push_back( strTmp );
    PathRemoveFileSpecW(wzSrc);
  }


  // check for non localized path
  if (GetLocalizedName( (WCHAR*) wstrSrcPath.c_str(), wzSrcNew, MAX_PATH) )
  {
    // set internal destination buffer
    wstrDstPath.resize( MAX_PATH, 0 );

    PathBuildRootW( (LPWSTR) wstrDstPath.c_str(), nDriveNr);
    PathAppendW(    (LPWSTR) wstrDstPath.c_str(), wzSrcNew);

    // reconstruct the original path
    for (int i = (int)max(wzPathList.size()-1, 0); i >= 0; i--)
    {
      PathAppendW((LPWSTR) wstrDstPath.c_str(), wzPathList[i].c_str());
    }
    PathAppendW((LPWSTR) wstrDstPath.c_str(), wstrFileName.c_str());

    str::ResizeByZeroTermination(wstrDstPath);

    if ( nDstSize > wstrDstPath.size() )
    {
      wcsncpy( wzDstPath, (LPWSTR) wstrDstPath.c_str(), min(wstrDstPath.size(), (nDstSize-1) ) );
      bRC = true;
    }
  }

  return bRC;
}

bool ABSTools::WildCmp(const char *wild, const char *string) 
{
    // Written by Jack Handy - jakkhandy@hotmail.com

    const char *cp = NULL, *mp = NULL;

    while ((*string) && (*wild != '*')) {
        if ((*wild != *string) && (*wild != '?')) {
            return false;
        }
        wild++;
        string++;
    }

    while (*string) {
        if (*wild == '*') {
            if (!*++wild) {
                return true;
            }
            mp = wild;
            cp = string+1;
        } else if ((*wild == *string) || (*wild == '?')) {
            wild++;
            string++;
        } else {
            wild = mp;
            string = cp++;
        }
    }

    while (*wild == '*') {
        wild++;
    }
    return ((!*wild) != 0);
}


bool ABSTools::GetFolderPath( int nFolder, char* szPath, UINT nSize, DWORD dwFlag /*= SHGFP_TYPE_CURRENT*/)
{
	bool bRC = false;
	
	WCHAR*  wzPath		= NULL;	

    wzPath = new WCHAR[ nSize + 2];
	if (NULL == wzPath) goto GetFolderPath_ENDE;
	memset( wzPath, 0, sizeof(WCHAR) * (nSize + 2));
	
	bRC = ABSTools::GetFolderPath( nFolder, wzPath, nSize, dwFlag);
	
	if (bRC)
	{
		wcstombs(szPath, wzPath, nSize);
	}
	
GetFolderPath_ENDE:
	SAFE_DELETE_ARRAY(wzPath);
	return bRC;
}



bool ABSTools::GetFolderPath( int nFolder, WCHAR* wzPath, UINT nSize, DWORD dwFlag /*= SHGFP_TYPE_CURRENT*/)
{
  bool bRC = false;

  if ( nSize < MAX_PATH )
    return false;

#if _MSC_VER < 1310

  HINSTANCE hShell32;
  LPSHGetFolderPathW SHGetFolderPathW = NULL;

  hShell32 = LoadLibraryA( "Shell32.dll");
  if (NULL != hShell32)
  {
    SHGetFolderPathW = (LPSHGetFolderPathW) GetProcAddress( hShell32, "SHGetFolderPathW" );

    if (NULL != SHGetFolderPathW) 
    {
				#ifndef __GNUWIN32__
      __try
				#endif
      {
        bRC = (S_OK == SHGetFolderPathW( NULL, nFolder, NULL, dwFlag, (WCHAR*) wzPath));
      }
				#ifndef __GNUWIN32__
      __except(GetExceptionCode() == EXCEPTION_ACCESS_VIOLATION ? EXCEPTION_EXECUTE_HANDLER : EXCEPTION_CONTINUE_SEARCH)
      {
        bRC = false;
      }
				#endif
    }
  }
  FreeLibrary(hShell32);
#else
  bRC = (S_OK == SHGetFolderPathW( NULL, nFolder, NULL, dwFlag, (WCHAR*) wzPath));
#endif

  return bRC;
}

#pragma warning( disable : 4035 ) 
//! keep an integer value within the given WORD range
BYTE ABSTools::range_b(int const nValue, int const nMin, int const nMax)
{
#if defined(_WIN64) || defined(__GNUWIN32__)
	return (BYTE) max(min(nValue, nMax), nMin);
#else
	__asm   mov     eax, nValue
	__asm   cmp     eax, nMin
	__asm   cmovl   eax, nMin   // if lower than min set to min
	__asm   cmp     eax, nMax
	__asm   cmovg	eax, nMax	// if greater max set to max
#endif
}

//! keep an float value within the given BYTE range
BYTE ABSTools::round_f_range_b (float const fValue, int const nMin, int const nMax)
{
#if defined(_WIN64) || defined(__GNUWIN32__)
	return (BYTE) max(min((int)fValue, nMax), nMin);
#else
	int nValue;
	//   WORD wValue;
	__asm   fld     dword ptr fValue;
	__asm   fistp   dword ptr nValue;
	__asm   mov     eax, nValue
	__asm   cmp     eax, nMin
	__asm   cmovl   eax, nMin   // if low than min set to min
	__asm   cmp     eax, nMax
	__asm   cmovg	eax, nMax	// if greater max set to max
		// __asm   mov     wValue, ax
		// return wValue;
#endif
}	

//! keep an integer value within the given WORD range
WORD ABSTools::range_w(int const nValue, int const nMin, int const nMax)
{
#if defined(_WIN64) || defined(__GNUWIN32__)
	return (WORD) max(min(nValue, nMax), nMin);
#else
	__asm   mov     eax, nValue
	__asm   cmp     eax, nMin
	__asm   cmovl   eax, nMin   // if lower than min set to min
	__asm   cmp     eax, nMax
	__asm   cmovg	eax, nMax	// if greater max set to max
#endif
}


//! keep an integer value within the given WORD range
WORD ABSTools::round_d_range_w (double const dValue, int const nMin, int const nMax)
{
#if defined(_WIN64) || defined(__GNUWIN32__)
    return (WORD) max(min((int)dValue, nMax), nMin);
#else
    int nValue;
    //   WORD wValue;
    __asm   fld     qword ptr dValue;
    __asm   fistp   dword ptr nValue;
    __asm   mov     eax, nValue
    __asm   cmp     eax, nMin
    __asm   cmovl   eax, nMin   // if low than min set to min
    __asm   cmp     eax, nMax
    __asm   cmovg	eax, nMax	// if greater max set to max
    // __asm   mov     wValue, ax
    // return wValue;
#endif
}


//! keep an integer value within the given WORD range
DWORD ABSTools::range_dw (int const nValue, int const nMin, int const nMax)
{
#if defined(_WIN64) || defined(__GNUWIN32__)
    return (DWORD) max(min(nValue, nMax), nMin);
#else
    __asm   mov     eax, nValue
    __asm   cmp     eax, nMin
    __asm   cmovl   eax, nMin   // if lower than min set to min
    __asm   cmp     eax, nMax
    __asm   cmovg	eax, nMax	// if greater max set to max
#endif
}


#pragma warning( default : 4035 )


void ABSTools::IdentityMatrix( float* pfMatrix, int nWidth, int nHeight )
{    
    float fValue;
    // 1 0 0 0
    // 0 1 0 0
    // 0 0 1 0
 
    for (int y=0; y < nHeight; y++)
    {
        int nOffset = y*nWidth;
        for (int x=0; x < nWidth; x++)
        {
            fValue = (y == x) ? 1.0f : 0;
            *(pfMatrix+(nOffset+x)) = fValue;

        }
    }  
}

void ABSTools::IdentityMatrix( double* pfMatrix, int nWidth, int nHeight )
{    
  double fValue;
  // 1 0 0 0
  // 0 1 0 0
  // 0 0 1 0

  for (int y=0; y < nHeight; y++)
  {
    int nOffset = y*nWidth;
    for (int x=0; x < nWidth; x++)
    {
      fValue = (y == x) ? 1.0 : 0;
      *(pfMatrix+(nOffset+x)) = fValue;

    }
  }  
}


void ABSTools::MultiplyMatrix(float* pfSrc1, float* pfSrc2,  float* pfDst, int nXYDim )
{    
    float fValue;
    float* fTmp = new float [nXYDim * nXYDim];
    // 1 0 0 0
    // 0 1 0 0
    // 0 0 1 0

    for (int y=0; y < nXYDim; y++)
    {
        int nOffset = y*nXYDim;
        for (int x=0; x < nXYDim; x++)
        {   
            fValue = 0.0f;
            for (int n=0; n < nXYDim; n++)
            {   
                fValue += pfSrc2[ nOffset + n ] * pfSrc1[ n * nXYDim + x ];
            }
            fTmp[ nOffset + x ] = fValue;
        }
    }  

    memcpy(pfDst, fTmp, sizeof(float) * (nXYDim*nXYDim));

    SAFE_DELETE_ARRAY(fTmp);
}

void ABSTools::MultiplyMatrix(double* pfSrc1, double* pfSrc2,  double* pfDst, int nXYDim )
{    
  double fValue;
  double* fTmp = new double [nXYDim * nXYDim];
  // 1 0 0 0
  // 0 1 0 0
  // 0 0 1 0

  for (int y=0; y < nXYDim; y++)
  {
    int nOffset = y*nXYDim;
    for (int x=0; x < nXYDim; x++)
    {   
      fValue = 0.0;
      for (int n=0; n < nXYDim; n++)
      {   
        fValue += pfSrc2[ nOffset + n ] * pfSrc1[ n * nXYDim + x ];
      }
      fTmp[ nOffset + x ] = fValue;
    }
  }  

  memcpy(pfDst, fTmp, sizeof(double) * (nXYDim*nXYDim));

  SAFE_DELETE_ARRAY(fTmp);
}


// 
// --- Shell32.dll ---
// Version	Distribution Platform
//  4.0	    Windows 95 and Microsoft Windows NT 4.0
//  4.71	  Microsoft Internet Explorer 4.0. See note 1.
//  4.72	  Microsoft Internet Explorer 4.01 and Windows 98. See note 1.
//  5.0	    Windows 2000 and Windows Millennium Edition (Windows Me). See note 2.
//  6.0	    Windows XP
//  6.0.1	  Windows Vista
//  6.1	    Windows 7
//
#define PACKVERSION(_major, _minor, _build) MAKELONG( _build, MAKEWORD( _minor, _major) )
unsigned int ABSTools::GetShell32DllVersion( void )
{
  HINSTANCE hinstDll;
  DWORD dwVersion = 0;

  /* For security purposes, LoadLibrary should be provided with a fully-qualified 
  path to the DLL. The lpszDllName variable should be tested to ensure that it 
  is a fully qualified path before it is used. */
  hinstDll = LoadLibraryA( "Shell32.dll" );

  if(hinstDll)
  {
    DLLGETVERSIONPROC pDllGetVersion;
    pDllGetVersion = (DLLGETVERSIONPROC)GetProcAddress(hinstDll, "DllGetVersion");

    /* Because some DLLs might not implement this function, you must test for 
    it explicitly. Depending on the particular DLL, the lack of a DllGetVersion 
    function can be a useful indicator of the version. */

    if(pDllGetVersion)
    {
      DLLVERSIONINFO2 dvi;
      HRESULT hr;

      ZeroMemory(&dvi, sizeof(dvi));
      dvi.info1.cbSize = sizeof(dvi);

      hr = (*pDllGetVersion)( (DLLVERSIONINFO*)&dvi);

      if(SUCCEEDED(hr))
      {
        dwVersion = PACKVERSION( dvi.info1.dwMajorVersion, dvi.info1.dwMinorVersion, dvi.info1.dwBuildNumber );
      }
    }
    FreeLibrary(hinstDll);
  }
  return dwVersion;
}

// ---------------------------------------------------------------------------

unsigned int ABSTools::getBitCount( unsigned int dwBitMask )
{
  dwBitMask -= (dwBitMask>>1) & 0x55555555;
  dwBitMask = ((dwBitMask>>2) & 0x33333333) + (dwBitMask & 0x33333333);
  dwBitMask = ((dwBitMask>>4) + dwBitMask) & 0x0f0f0f0f;
  dwBitMask *= 0x01010101;
  return dwBitMask>>24;
}

// ---------------------------------------------------------------------------

unsigned int ABSTools::getBitCount(unsigned long long ulBitMask)
{
  ulBitMask -= (ulBitMask>>1) & 0x5555555555555555;
  ulBitMask = ((ulBitMask>>2) & 0x3333333333333333) + (ulBitMask & 0x3333333333333333);
  ulBitMask = ((ulBitMask>>4) + ulBitMask) & 0x0f0f0f0f0f0f0f0f;
  ulBitMask *= 0x0101010101010101;
  return (unsigned int) (ulBitMask>>56);
}

// ---------------------------------------------------------------------------

std::string ABSTools::getBaseFilename( const std::string &  strFilePath )
{
  std::string strBaseFileName = getFilename( strFilePath );
  PathRemoveExtensionA( (char*) strBaseFileName.c_str() );
  str::ResizeByZeroTermination(strBaseFileName);
  return strBaseFileName;
}

// ---------------------------------------------------------------------------

std::string ABSTools::getFileExtension( const std::string &  strFilePath )
{
  return std::string( PathFindExtensionA( strFilePath.c_str() ) );
}

// ---------------------------------------------------------------------------

std::string ABSTools::getFilename( const std::string &  strFilePath )
{
  return std::string( PathFindFileNameA( (char*) strFilePath.c_str() ) );  
}

// ---------------------------------------------------------------------------

std::string ABSTools::getFileDir( const std::string & strFilePath )
{
  std::string strFileDir = strFilePath;
  PathRemoveFileSpecA( (char*) strFileDir.c_str() );
  str::ResizeByZeroTermination(strFileDir);
  return strFileDir;
}

// ---------------------------------------------------------------------------

unsigned int ABSTools::findFiles( const std::string & strFileMask, CStdStringLst & strFiles )
{
  HANDLE           hFileFind     = INVALID_HANDLE_VALUE;
  WIN32_FIND_DATAA sFindFileData = {0};

  strFiles.clear();
  // find all matching files
  hFileFind = FindFirstFileA( (char*)strFileMask.c_str(), &sFindFileData);       

  if (hFileFind != INVALID_HANDLE_VALUE)
  {
    strFiles.push_back( std::string( sFindFileData.cFileName ) );

    while (FindNextFileA(hFileFind, &sFindFileData) != 0) 
    {
      strFiles.push_back( std::string( sFindFileData.cFileName ) );        
    }
    FindClose(hFileFind);
  }

  return (unsigned int) strFiles.size();
}

// ---------------------------------------------------------------------------

std::string ABSTools::getAppPath( )
{
  std::string strAppPath( MAX_PATH, '\0' );

  ABSTools::GetAppPath( (char *)strAppPath.c_str(), (int)strAppPath.size() );

  std::string::size_type iPos = strAppPath.find('\0');
  if ( iPos != std::string::npos )
    strAppPath.erase( iPos, std::string::npos );

  return strAppPath;
}

// ---------------------------------------------------------------------------

std::string ABSTools::getQualified( const std::string & strFilePath )
{
  std::string strQualified( MAX_PATH, '\0' );
  if ( PathSearchAndQualifyA( strFilePath.c_str(), (char*) strQualified.c_str(), MAX_PATH) )
    return strQualified;
  else
    return strFilePath;
}

// ---------------------------------------------------------------------------

int ABSTools::round( float const x )    // Round to nearest integer
{
    int n;
#if defined(__unix__) || defined(__GNUC__)
    // 32-bit Linux, Gnu/AT&T syntax:
    __asm ("fldl %1 \n fistpl %0 " : "=m"(n) : "m"(x) : "memory" );
#else
    #ifdef _M_X64 // For 64-bit apps 
        n = (int) x;
    #else
    // 32-bit Windows, Intel/MASM syntax:
    __asm fld qword ptr x;
    __asm fistp dword ptr n;
    #endif
#endif
    return n;
}

// ---------------------------------------------------------------------------

int ABSTools::round( double const x )    // Round to nearest integer
{
  int n;
#if defined(__unix__) || defined(__GNUC__)
  // 32-bit Linux, Gnu/AT&T syntax:
  __asm ("fldl %1 \n fistpl %0 " : "=m"(n) : "m"(x) : "memory" );
#else
  #ifdef _M_X64 // For 64-bit apps 
    n = (int) x;
  #else
    // 32-bit Windows, Intel/MASM syntax:
    __asm fld qword ptr x;
    __asm fistp dword ptr n;
  #endif
#endif
  return n;
}

// ---------------------------------------------------------------------------
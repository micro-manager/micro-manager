///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file		MemoryIniFile.cpp
//! 
//! \brief		see header file
//! 
//! \author		ABS GmbH Jena (HBau)
//!				Copyright (C) 2009 - All Rights Reserved
//! 
//! \version	1.0
//! \date		2009/03/05 \n
//! 			 -> created \n
//! 
///////////////////////////////////////////////////////////////////////////////

// -------------------------- Includes ----------------------------------------
//
#ifndef _CRT_SECURE_NO_WARNINGS
  #define _CRT_SECURE_NO_WARNINGS
#endif
#include "stdlib.h"
#include <stdio.h>
#include <iostream>
#include <fstream>
#include <new> 
#include <cassert> 
#include <algorithm>

#include "memoryinifile.h"
#include "stringtools.h"

#include "SafeUtil.h"					// SAFE Utils
#include "shlobj.h"						// SHGetFolderPath
#include "shlwapi.h"					// path utilities
#pragma comment(lib,"shlwapi.lib")		// path utilities lib
#include "ABSCommonTools.h"				// common utilities


// -------------------------- Defines -----------------------------------------
//
//! parser states
enum EParseState
{
	EPSNone,			// no state yet
	EPSSectionName,		// section name
	EPSEntryName,		// entry name in a section
	EPSEntryValue,		// entry value in a section
	EPSWaitEol,			// wait for end of line
  EPSWaitSol,			// wait for start of line
	EPSEOF				// end of file reached
};

#define MIN_INI_FILE_SIZE	(1)						// 1 Byte
#define MAX_INI_FILE_SIZE	(5 * 1024 * 1024)		// 5 MByte

// -------------------------- Class -------------------------------------------
//
CMemoryIniFile::CMemoryIniFile(void)
{

}
//
// ----------------------------------------------------------------------------
//
CMemoryIniFile::~CMemoryIniFile(void)
{
	clear();
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::isHex(const std::string &strValue)
{
	bool bRC = false;
	if (strValue.size() >= 3)
	{
        bRC = ((strValue.find("0x") == std::string::npos) || 
               (strValue.find("0X") == std::string::npos));
	}
	return bRC;
}
//
// ----------------------------------------------------------------------------
//
void CMemoryIniFile::clear(void)
{
	m_cIniFile.clear();
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::loadIniFromFile( const std::string &strIniFileName )
{
	bool	bRC			= false;	
	char*	szIniBuffer	= 0;
	unsigned long dwIniBufferSize;

    // abort if file is not available
    //if (!isPathExisting() || !isFileExisting()) return false;
    if (!isFileExisting()) return false;
    if (!isPathExisting()) return false;
    

    // create the stream object => file position is at the end of file
    std::ifstream iniFile (strIniFileName.c_str(), std::ios::in|std::ios::binary|std::ios::ate);
    // check if object is open
    if (false == iniFile.is_open()) goto loadIniFromFile_End;
    
    dwIniBufferSize = (unsigned long) iniFile.tellg();    // get file size

    // ini file more than 0 Byte and less than 5MB
    if ((dwIniBufferSize < MIN_INI_FILE_SIZE) && 
        (dwIniBufferSize > MAX_INI_FILE_SIZE))
    {   
        // abort with error        
        goto loadIniFromFile_End;
    }

    // allocate buffer
    try { 
        szIniBuffer = (char*) new char[dwIniBufferSize+2];
        memset(szIniBuffer, 0, dwIniBufferSize+2);
    } 
    catch (std::bad_alloc&) {
        szIniBuffer = 0;
        dwIniBufferSize = 0;
    }

    // read buffer
    if (0 == szIniBuffer) goto loadIniFromFile_End;

    iniFile.seekg (0, std::ios::beg);                 // seek to file start
    iniFile.read (szIniBuffer, dwIniBufferSize); // read file to buffer
    
    bRC = loadIniFromMemory(szIniBuffer, dwIniBufferSize);
	
loadIniFromFile_End:
    SAFE_DELETE_ARRAY(szIniBuffer);
    iniFile.close();
    return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::loadIniFromMemory( const char* szIniBuffer, unsigned long dwIniBufferSize )
{
	// check buffer and if ini file more than 0 Byte and less than 5MB
	if ((0 == szIniBuffer) ||
		(dwIniBufferSize < MIN_INI_FILE_SIZE) ||
		(dwIniBufferSize > MAX_INI_FILE_SIZE))
	{
		return false;
	}

	clear();

	CIniSectionLst::iterator sectionIter = m_cIniFile.end();
    CIniEntryLst::iterator   entryIter;

    std::string strSectionName;
	std::string strEntryName;
	std::string strEntryValue;
	EParseState eSate	  = EPSNone;
	EParseState eLastSate = EPSNone;
	char* szItemStart = (char*)0;
	char* szPos		  = (char*)szIniBuffer;
	char* szPosEnd	  = (char*)szIniBuffer + (dwIniBufferSize-1);

	// parse ini file
	while (szPos && (szPos <= szPosEnd))
	{
		if (szPos == szPosEnd)
		{
			eLastSate	= eSate;
			eSate		= EPSEOF;
		}

		switch(eSate)
		{
		case EPSNone:
			if (*szPos == ';') 
			{
				eLastSate		= eSate;
				eSate			= EPSWaitEol;
			}
			else if (*szPos == '[') 
			{
				strSectionName.clear();
				strEntryName.clear();
				szItemStart		= szPos+1;
				eSate			= EPSSectionName;
			}
			break;

		case EPSSectionName:
			if (*szPos == ';') 
			{
				eLastSate		= eSate;
				eSate			= EPSWaitEol;
			}
			else if (*szPos == ']') 
			{						
        strSectionName	= std::string( (std::string::value_type*) szItemStart, (std::string::size_type)(szPos-szItemStart));
        str::ToLower( strSectionName );

				if ( false == strSectionName.empty() )
				{					
          m_cIniFile.push_back(CMemoryIniFileSection(strSectionName));
          sectionIter     =  m_cIniFile.end(); // get last element
          if (sectionIter != m_cIniFile.begin()) sectionIter--;

					szItemStart		= szPos;
					eLastSate		  = EPSEntryName;
					eSate			    = EPSWaitEol;
				}
				else
				{
					szItemStart		= szPos+1;
					eSate			    = EPSNone;
				}				
			}
			break;

		case EPSEntryName:
			if ((*szPos == '=') || (*szPos == ';') || (*szPos == '[') || (*szPos == '\r') || (*szPos == '\n') )
			{				
        strEntryName	= std::string( (std::string::value_type*) szItemStart, (std::string::size_type)(szPos-szItemStart));                
        str::Trim(strEntryName, " \t\r\n");

				if ( false == strEntryName.empty() )
				{
          CMemoryIniFileEntry cMIFE;
          str::ToLower(strEntryName);                        
					strEntryValue.clear();

          if (*szPos == '=')
          {
           cMIFE = CMemoryIniFileEntry(strEntryName, strEntryValue);
          }
          else if ( (*szPos == '\r') || (*szPos == '\n') )
          {
            cMIFE = CMemoryIniFileEntry(strEntryName, CMemoryIniFileEntry::eList );
          }

          if (sectionIter !=  m_cIniFile.end())
          {                                    
            sectionIter->cEntries.push_back( cMIFE );
            entryIter = sectionIter->cEntries.end();
            if (entryIter != sectionIter->cEntries.begin()) entryIter--;
            
          }
					szItemStart	= szPos+1;
					eSate			  = EPSEntryValue;				
				}
				else
				{
					szItemStart		= szPos+1;
					eSate			    = EPSEntryName;				
				}
							
				if (*szPos == '[') 
				{
					strSectionName.clear();
					strEntryName.clear();					
					eSate			= EPSSectionName;
				}
				else if (*szPos == ';') 
				{
					eLastSate	= eSate;
					eSate			= EPSWaitEol;
				}				
        else  if ( (*szPos == '\r') || (*szPos == '\n') )
        {
          eLastSate	= eSate;
					eSate			= EPSWaitSol;
        }
			}			
			break;

		case EPSEntryValue:
			if ((*szPos == ';') || (*szPos == '\r') || (*szPos == '\n') || (*szPos == '[') )
			{
          strEntryValue	= std::string( (std::string::value_type*) szItemStart, (std::string::size_type)(szPos-szItemStart));                
          str::Trim(strEntryValue, " \t\r\n");
          // remember value
          if ((sectionIter != m_cIniFile.end()) &&
              (entryIter != sectionIter->cEntries.end()))
          {
            if ( entryIter->type() == CMemoryIniFileEntry::eValue )
              entryIter->setValue(strEntryValue);
            else if ( strEntryValue.size() != 0 )
              entryIter->addValue(strEntryValue);
          }
                				
				szItemStart	= szPos;
        eLastSate		= ( entryIter->type() == CMemoryIniFileEntry::eValue ) ? EPSEntryName : EPSEntryValue;
        eSate		    = EPSWaitEol;

				if (*szPos == '[')
				{
					strSectionName.clear();
					strEntryName.clear();
					strEntryValue.clear();
					szItemStart		= szPos+1;
					eSate			= EPSSectionName;
				}				
			}			
			break;

    case EPSWaitSol:
      if ((*szPos != '\n') && (*szPos != '\r')) 
			{				
        if (*szPos == '[')
        {
          szItemStart	= szPos+1;
          eSate	= EPSSectionName;
        }
        else
        {
          szItemStart	= szPos;
				  eSate	= eLastSate;				
        }
			}
			break;

		case EPSWaitEol:
			if ((*szPos == '\n') || (*szPos == '\r')) 
			{
				szItemStart	= szPos;
				eSate			  = EPSWaitSol;				
			}
			break;

		case EPSEOF:
			switch(eLastSate)
			{			
			case EPSEntryName:		
                strEntryName	= std::string( (std::string::value_type*) szItemStart, (std::string::size_type)(szPos-szItemStart+1));
                str::Trim(strEntryName, " \t\r\n");
                
				if ( false == strEntryName.empty() )
				{
                    str::ToLower(strEntryName);
					strEntryValue.clear();

                    if ((sectionIter != m_cIniFile.end()) &&
                        (sectionIter->getName() != strSectionName))
                    {
                        m_cIniFile.push_back(CMemoryIniFileSection(strSectionName));                          
                        sectionIter     = m_cIniFile.end(); // get last element 
                        if (sectionIter != m_cIniFile.begin()) sectionIter--;

                    }

                    if (sectionIter != m_cIniFile.end())
                    {                        
                        sectionIter->cEntries.push_back( CMemoryIniFileEntry(strEntryName, strEntryValue) );
                        entryIter = sectionIter->cEntries.end();
                        if (entryIter != sectionIter->cEntries.begin()) entryIter--;
                    }                    
				}
				break;

			case EPSEntryValue:

                strEntryValue	= std::string( (std::string::value_type*) szItemStart, (std::string::size_type)(szPos-szItemStart+1));
                str::Trim(strEntryValue, " \t\r\n");
                // remember value
                if ((sectionIter != m_cIniFile.end()) &&
                    (entryIter   != sectionIter->cEntries.end()))
                {
                    entryIter->setValue(strEntryValue);
                }
                break;

			default:
				break;
			}
			break;

		default:
			assert(false);
			break;
		}
		szPos++;
	}

	return true;
}

// ----------------------------------------------------------------------------

bool CMemoryIniFile::ReadBySecID( unsigned long nSectionID, char* szName, std::string& strValue)
{
    bool bRC = false;
    std::string strName(szName);
    str::ToLower(strName);    
    CIniEntryLst::iterator entryIter;

    if (m_cIniFile.size() <= nSectionID) return false;

    entryIter = m_cIniFile[nSectionID].cEntries.begin();
    while( entryIter != m_cIniFile[nSectionID].cEntries.end() )
    {
        if (entryIter->getName() == strName)
        {
            strValue = entryIter->getValue();
            bRC = true;
            break;
        }
        entryIter++;
    }       
  
    return bRC;
}

// ----------------------------------------------------------------------------

bool CMemoryIniFile::Update( char* szSection, char* szName, std::string& strValue, bool bWrite)
{
	bool bRC = false;

    std::string strSection(szSection); 
    std::string strName(szName);
        
    str::ToLower(strSection);
    str::ToLower(strName);
	
    bool bSectionFound = false;    
    bool bEntryFound = false;
    CIniSectionLst::iterator sectionIter;    
    CIniEntryLst::iterator entryIter;

    sectionIter = m_cIniFile.begin();
    while( sectionIter != m_cIniFile.end() )
    {
        if (sectionIter->getName() == strSection)
        {
            bSectionFound = true;
            break;
        }
        sectionIter++;
    }

    if (bSectionFound)
    {
        entryIter = sectionIter->cEntries.begin();
        while( entryIter != sectionIter->cEntries.end() )
        {
            if (entryIter->getName() == strName)
            {
                bEntryFound = true;
                break;
            }
            entryIter++;
        }       
    }
    // read or write value
    if (true == bWrite)
    {
        if (bSectionFound && bEntryFound) // value exist => overwrite
        {
            entryIter->setValue( strValue );
        }
        else if (bSectionFound) // section exist => create value 
        {
            sectionIter->cEntries.push_back( CMemoryIniFileEntry(strName, strValue) );
        }
        else // create section and value 
        {
            m_cIniFile.push_back( CMemoryIniFileSection(strSection, CMemoryIniFileEntry(strName, strValue) ) );
        }
        bRC = true;
    }
    else // read a existing value
    {
        if (bSectionFound && bEntryFound) // value exist => read
        {
            strValue = entryIter->getValue();
            bRC = true;
        }        
    }

	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, ULONGLONG& ulValue, bool bWrite)
{
	bool bRC;
	LONGLONG  nlValue;
	if (bWrite) nlValue = ulValue;
	bRC = Update(  szSection,  szName, nlValue, bWrite);

	if (bRC && !bWrite)
	{		
		ulValue = (ULONGLONG) nlValue;
	}

	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, LONGLONG&  nlValue, bool bWrite)
{
	bool bRC;
    std::string strValue;
    
	if (bWrite) str::sprintf(strValue,"%I64d", nlValue);
	bRC = Update(  szSection,  szName, strValue, bWrite);

	if (bRC && !bWrite)
	{		
        str::Trim(strValue);
		if (isHex(strValue))
		{
			str::ToLower(strValue);
			sscanf(strValue.c_str(), "%I64x", &nlValue);
		}
		else
		{
			nlValue = _atoi64(strValue.c_str());
		}		
	}
	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, long& nValue, bool bWrite)
{
	bool bRC;
	std::string strValue;

	if (bWrite) str::sprintf(strValue,"%d", nValue);
	bRC = Update(  szSection,  szName, strValue, bWrite) == TRUE;

	if (bRC && !bWrite)
	{		
        str::Trim(strValue);
        if (isHex(strValue))
        {
            str::ToLower(strValue);
            sscanf(strValue.c_str(), "%x", &nValue);
        }
		else
		{
			nValue = atol(strValue.c_str());
		}		
	}
	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, int& nValue, bool bWrite)
{
	bool bRC;
	std::string strValue;

	if (bWrite) str::sprintf(strValue,"%d", nValue);
	bRC = Update(  szSection,  szName, strValue, bWrite) == TRUE;

	if (bRC && !bWrite)
	{		
        str::Trim(strValue);
        if (isHex(strValue))
        {
            str::ToLower(strValue);
            sscanf(strValue.c_str(), "%x", &nValue);
        }
        else
        {
            nValue = atoi(strValue.c_str());
        }			
	}
	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, SHORT& nValue, bool bWrite)
{
	bool bRC;
	int  iValue;

	if (bWrite) iValue = nValue;
	bRC = Update(  szSection,  szName, iValue, bWrite);

	if (bRC && !bWrite)
	{		
		nValue = (SHORT) iValue;
	}

	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, DWORD& dwValue, bool bWrite)
{
	bool bRC;
	std::string strValue;

	if (bWrite) str::sprintf(strValue,"%u", dwValue); 
	bRC = Update(  szSection,  szName, strValue, bWrite) == TRUE;
	
	if (bRC && !bWrite)
	{		
        str::Trim(strValue);
        if (isHex(strValue))
        {
            str::ToLower(strValue);
            sscanf(strValue.c_str(), "%x", &dwValue);
        }
        else
        {
            dwValue = atol(strValue.c_str());
        }					
	}
	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, UINT& dwValue, bool bWrite)
{
	return Update(  szSection,  szName, (DWORD&)dwValue, bWrite);
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, WORD& wValue, bool bWrite)
{
	bool    bRC;
	DWORD   dwValue;
	
	if (bWrite) dwValue = wValue;
	bRC = Update(  szSection,  szName, dwValue, bWrite);

	if (bRC && !bWrite)
	{				
		wValue = (WORD)dwValue;
	}
	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, BYTE& nValue, bool bWrite)
{
	bool    bRC;
	DWORD   dwValue;
	
	if (bWrite) dwValue = nValue;
	bRC = Update(  szSection,  szName, dwValue, bWrite);

	if (bRC && !bWrite)
	{				
		nValue = (BYTE)dwValue;
	}
	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, double& fValue, bool bWrite)
{
	bool bRC;
	std::string strValue;

	if (bWrite) str::sprintf(strValue,"%f", fValue);
	bRC = Update(  szSection,  szName, strValue, bWrite) == TRUE;

	if (bRC && !bWrite)
	{		
		str::Trim(strValue);
		fValue = atof(strValue.c_str());		
	}
	return bRC;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::Update( char* szSection, char* szName, float& fValue, bool bWrite)
{
	
	bool    bRC;
	double  dfValue;
	
	if (bWrite) dfValue = fValue;
	bRC = Update(  szSection,  szName, dfValue, bWrite);

	if (bRC && !bWrite)
	{				
		fValue = (float)dfValue;
	}

	return bRC;
}

// ----------------------------------------------------------------------------

void CMemoryIniFile::setIniFile( const char *szIniFileName )
{
    setIniFile( std::string(szIniFileName) );
}

// ----------------------------------------------------------------------------

void CMemoryIniFile::setIniFile( const std::string &strIniFileName )
{    
    m_strIniFile = strIniFileName;
    loadIniFromFile( m_strIniFile );
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::isFileExisting(void)
{
    BOOL bRC = PathFileExistsA( (char*) m_strIniFile.c_str() );
    return ( bRC != FALSE);
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::isFileWriteAccess(void)
{
    bool			bResult;
    BOOL			bDeleteFile = FALSE;
    HANDLE			hFile;

    if (!isFileExisting())
    {
        bDeleteFile = TRUE;
        hFile = CreateFileA(	(char*) m_strIniFile.c_str(), 
            GENERIC_READ | GENERIC_WRITE, 
            FILE_SHARE_READ, 
            NULL, 
            OPEN_ALWAYS, 
            FILE_ATTRIBUTE_NORMAL, 
            NULL );

    }
    else
    {
        hFile = CreateFileA(	(char*) m_strIniFile.c_str(), 
            GENERIC_READ | GENERIC_WRITE, 
            FILE_SHARE_READ, 
            NULL, 
            OPEN_EXISTING, 
            FILE_ATTRIBUTE_NORMAL, 
            NULL );
    }

    // if file successfully opened
    bResult = (hFile != INVALID_HANDLE_VALUE);

    SAFE_CLOSEHANDLE(hFile);

    if (bDeleteFile) DeleteFileA( (char*) m_strIniFile.c_str());

    // on error return error with failure
    return bResult;
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::isPathExisting(void)
{
    std::string strIniFile = m_strIniFile;
    strIniFile.reserve ( 2*MAX_PATH );
    LPSTR   szPath = (LPSTR) strIniFile.c_str();
    if (!std::string( PathFindExtensionA( szPath )).empty())
    {
        PathRemoveFileSpecA( szPath );
    }    
    str::ResizeByZeroTermination( strIniFile );
    BOOL bRC = PathIsDirectoryA( (char*) strIniFile.c_str() ); 

    return (bRC != FALSE);
}
//
// ----------------------------------------------------------------------------
//
void CMemoryIniFile::initIniFilePath( void )
{
    char szAppName[MAX_PATH+2] = {0};
    std::string strModuleName;
    std::string strCommonAppPath;

    getCommonAppFolder(strCommonAppPath);
    // build config file name
    m_strIniFile = strCommonAppPath + "\\" + std::string(ABSTools::GetAppName(szAppName, MAX_PATH)) + ".ini";
}
//
// ----------------------------------------------------------------------------
//
bool CMemoryIniFile::getCommonAppFolder(std::string &strCommonAppPath)
{
  bool bRC = false;
	if (!ABSTools::GetFolderPath(CSIDL_COMMON_APPDATA, (char*)strCommonAppPath.c_str(), 2*MAX_PATH, SHGFP_TYPE_CURRENT))
    strCommonAppPath.clear();
  else
  {    
    char szAppName[MAX_PATH+2] = {0};
    str::ResizeByZeroTermination( strCommonAppPath );
    strCommonAppPath += "\\ABS GmbH";
    strCommonAppPath += + "\\" + std::string( ABSTools::GetAppName( szAppName, MAX_PATH ) );    
    bRC = true;
  }
  return bRC;  
}

// ----------------------------------------------------------------------------

//! \brief Get all Section names
//! \param strSectionNames	list of available sections
bool CMemoryIniFile::getSectionNames( CStdStringLst &strSectionNames )
{
    CIniSectionLst::iterator iter;
    strSectionNames.clear();
    strSectionNames.reserve(m_cIniFile.size());
    iter = m_cIniFile.begin();
    while (iter != m_cIniFile.end())
    {
        strSectionNames.push_back(iter->getName());
        iter++;
    }
    return true;
}
//! \brief Get a by  section names
//! \param strSectionNames	list of available sections
bool CMemoryIniFile::getSection( const std::string & strSection, CStdStringLst &strSectionData )
{  
  CIniSectionLst::iterator iter = m_cIniFile.begin();

  while ( iter != m_cIniFile.end() )
  { 
    // sections are equal ?
    if ( 0 == str::compareNoCase( iter->getName(), strSection ) )
    {
      assert( iter->cEntries.size() == 1 );
      strSectionData = iter->cEntries[0].getValueList();
      return true;
    }
  }

  return false;
}

//! \brief Get a by  section names
//! \param strSectionNames	list of available sections
bool CMemoryIniFile::setSection( const std::string & strSection, const CStdStringLst & strSectionData )
{  
  CIniSectionLst::iterator iter = m_cIniFile.begin();

  while ( iter != m_cIniFile.end() )
  { 
    // sections are equal ?
    if ( 0 == str::compareNoCase( iter->getName(), strSection ) )
    {
      assert( iter->cEntries.size() == 1 );
      iter->cEntries[0] = CMemoryIniFileEntry( strSectionData );
      return true;
    }
  }
  m_cIniFile.push_back( CMemoryIniFileSection( strSection, CMemoryIniFileEntry( strSectionData ) ) );
  return true;
}
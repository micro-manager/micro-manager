///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file		MemoryIniFile.h
//! 
//! \brief		Allow easy access to an ini file for multiple datatypes
//! 
//! \author		ABS GmbH Jena (HBau)
//!				Copyright (C) 2009 - All Rights Reserved
//! 
//! \version	1.0
//! \date		2009/03/05 \n
//! 			 -> created \n
//! 
///////////////////////////////////////////////////////////////////////////////
#ifndef __MEMORYINIFILE_H__
#define __MEMORYINIFILE_H__

// -------------------------- Includes ----------------------------------------
//
#define _CRT_SECURE_NO_WARNINGS
#include <cstdio>
#include <vector>
#include <string>
#include <map>

// -------------------------- Typedefs ----------------------------------------
//
typedef std::vector<std::string> CStdStringLst;

class CMemoryIniFileSection;
class CMemoryIniFileEntry;
typedef std::vector<CMemoryIniFileSection> CIniSectionLst;
typedef std::vector<CMemoryIniFileEntry>   CIniEntryLst;

// -------------------------- Class -------------------------------------------
//
class CMemoryIniFileEntry
{
public:
  enum EType
  {
    eValue,
    eList
  };

  CMemoryIniFileEntry() {
    clear();
  };

  ~CMemoryIniFileEntry(){};

	CMemoryIniFileEntry( const CMemoryIniFileEntry& cMIFE ) {
    m_eType     = cMIFE.m_eType;
		m_strName	  = cMIFE.m_strName;
		m_strValue	= cMIFE.m_strValue;
	};
   
  CMemoryIniFileEntry( const std::string& strName, const std::string& strValue ) {
    m_eType   = eValue;
		m_strName	= strName;
		m_strValue.clear();
    m_strValue.push_back( strValue );
	};

  CMemoryIniFileEntry( const std::vector<std::string> & strValues ) {    
    m_eType    = eList;
		m_strName.clear();
    m_strValue = strValues;    
	};

  CMemoryIniFileEntry( const std::string& strValue,  const EType eList ) {    
    m_eType   = eList;
		m_strName.clear();
    m_strValue.clear();
    m_strValue.push_back( strValue );
	};

  CMemoryIniFileEntry& operator= (const CMemoryIniFileEntry& cMIFE ) {
      m_eType    = cMIFE.m_eType;
      m_strName	 = cMIFE.m_strName;
      m_strValue = cMIFE.m_strValue;
      return *this;
  };

    void setName(const std::string& strName) {
        m_strName	= strName;
    }
    void setValue(const std::string& strValue) {
      m_eType   = eValue;
		  m_strValue.clear();
      m_strValue.push_back( strValue );
    }

    void addValue(const std::string& strValue) {
      m_eType   = eList;		  
      m_strValue.push_back( strValue );
    }
    

    std::string getName(void) const {
        return m_strName;
    }

    std::string getValue( unsigned long index = 0) {
      if (type() == eValue)
        return m_strValue[0];
      else
        return ((m_strValue.size() > index) ? m_strValue[ index ] : "");
    }

    std::vector<std::string> getValueList() const {
      return m_strValue;
    }

    EType type() const { 
      return m_eType; 
    }

    void clear(void){
        m_eType = eValue;
        m_strName.clear();
        m_strValue.clear();
        m_strValue.push_back("");
    };
	
protected:
  EType       m_eType;
	std::string m_strName;
  std::vector<std::string> m_strValue;
};

class CMemoryIniFileSection
{
public:
    CMemoryIniFileSection( const std::string& strName = ""){
        
        clear();
        m_strName	= strName;        
    };

    ~CMemoryIniFileSection(){};

    CMemoryIniFileSection( const CMemoryIniFileSection& cMIFS){
        m_strName	= cMIFS.m_strName;
        cEntries	= cMIFS.cEntries;
    };

    CMemoryIniFileSection( const std::string& strName, const CIniEntryLst& cEntryLst){
        m_strName	= strName;
        cEntries	= cEntryLst;
    };

    CMemoryIniFileSection( const std::string& strName, const CMemoryIniFileEntry& cEntry){
        m_strName	= strName;
        cEntries.push_back(cEntry);
    };

    CMemoryIniFileSection& operator = (const std::string& strName) {
        clear();
        m_strName	= strName;        
        return *this;
    };    

    CMemoryIniFileSection& operator = (const CMemoryIniFileSection& cMIFS) {
        m_strName	= cMIFS.m_strName;
        cEntries	= cMIFS.cEntries;
        return *this;
    };    

    void setName(const std::string& strName) {
        m_strName	= strName;
    }

    std::string getName(void) const {
        return m_strName;
    }
  
    void clear(void){
        m_strName.clear();
        cEntries.clear();
    };

    CIniEntryLst cEntries;
protected:
    std::string  m_strName;    
    
};

// -------------------------- Class -------------------------------------------
//
class CMemoryIniFile
{
public:
	CMemoryIniFile(void);
	~CMemoryIniFile(void);

  bool    loadIniFromFile( const std::string &strIniFileName );


	//! \brief Load the INI-File from a memory buffer
	//! \param szIniBuffer		pointer to the buffer
	//! \param dwIniBufferSize	size of the memory buffer
	//!
	bool	loadIniFromMemory( const char* szIniBuffer, unsigned long dwIniBufferSize );


	//! \brief Read/Write an STRING value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param cszValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
    bool	Update( char* szSection, char* szName, std::string& strValue, bool bWrite);
    bool    ReadBySecID( unsigned long nSectionID, char* szName, std::string& strValue);

	//! \brief Read/Write an ULONGLONG value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param nValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool Update( char* szSection, char* szName, unsigned long long& ulValue, bool bWrite);

	//! \brief Read/Write an LONGLONG value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param nValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool Update( char* szSection, char* szName, long long&  nlValue, bool bWrite);

	//! \brief Read/Write an LONG value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param nValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool	Update( char* szSection, char* szName, long& nValue, bool bWrite);

	//! \brief Read/Write an INTEGER value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param nValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool	Update( char* szSection, char* szName, int& nValue, bool bWrite);

	//! \brief Read/Write an SHORT value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param nValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool	Update( char* szSection, char* szName, short & nValue, bool bWrite);

	//! \brief Read/Write an DOUBLE WORD value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param dwValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool	Update( char* szSection, char* szName, unsigned long & dwValue, bool bWrite);

	//! \brief Read/Write an UINT value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param dwValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool	Update( char* szSection, char* szName, unsigned int & dwValue, bool bWrite);

	//! \brief Read/Write an WORD value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param wValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool	Update( char* szSection, char* szName, unsigned short & wValue, bool bWrite);

	//! \brief Read/Write an BYTE value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param nValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool	Update( char* szSection, char* szName, unsigned char & nValue, bool bWrite);

	//! \brief Read/Write an DOUBLE value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param fValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool	Update( char* szSection, char* szName, double& fValue, bool bWrite);

	//! \brief Read/Write an FLOAT value to/from the ini file
	//! \param cszSection	ini file section
	//! \param cszName		element name at section
	//! \param fValue		value to read/write
	//! \param bWrite		if TRUE value will be written to the ini file
	//!						if FALSE value will be read from the ini file
	//!	\retval				TRUE if succuessfull
	//!	\retval				FALSE if failed
	bool	Update( char* szSection, char* szName, float& fValue, bool bWrite);

  //! \brief Get all Section names
  //! \param strSectionNames	list of available sections
  bool	getSectionNames( CStdStringLst &strSectionNames );

  //! \brief Get a by  section names
  //! \param strSection	    name of section which should be returned
  //! \param strSectionData	section data
  bool  getSection( const std::string &strSection, CStdStringLst &strSectionData );

  //! \brief Set a by  section names
  //! \param strSection	    name of section which should be set
  //! \param strSectionData	section data
  bool  setSection( const std::string & strSection, const CStdStringLst & strSectionData );

  //! \brief Set the ini file name and path, if this function isn't called
  //! \brief the application path and name + ".ini" will be used
  //! \param cszIniFileName name and path of the ini file
  //!
  void  setIniFile( const char *szIniFileName );
  
  //! \brief Set the ini file name and path, if this function isn't called
  //! \brief the application path and name + ".ini" will be used
  //! \param cszIniFileName name and path of the ini file
  //!
  void  setIniFile( const std::string &strIniFileName );

  //! \brief Get the ini file name and path
  //! \retval name and path of the ini file
  //!
  std::string getIniFile( void ) const {return m_strIniFile;};
	
	//! \brief Perform a check if the file exist. (see SetIniFile)
	//!
	bool		isFileExisting(void);

	//! \brief Perform a check if the file hast write access. (see SetIniFile)
	//!
	bool		isFileWriteAccess(void);

	//! \brief Perform a check if the file path exist. (see SetIniFile)
	//!
	bool		isPathExisting(void);

  //! \brief Return the path  of the common application folder
  //!
  bool getCommonAppFolder(std::string &strCommonAppPath);

protected: // functions
	bool isHex( const std::string &strValue );
	void clear(void);
	
	//! \brief look for the application name and build the ini file path
	//!
	void initIniFilePath( void );

protected: // member variable    
	std::string     m_strIniFile;   // file path of used init file
  CIniSectionLst  m_cIniFile;	    // list of all ini file entries

};


#endif // __MEMROYINIFILE_H__


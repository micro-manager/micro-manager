#ifndef __AUTOTIMEMEASURE_H__
#define __AUTOTIMEMEASURE_H__

#include "TimeSpanPC.h"
#include <string>
#include "StringTools.h"

class CAutoTimeMeasure : public CTimeSpanPC
{ 
public:
  CAutoTimeMeasure( char* szName, bool bClassMode = false );
  ~CAutoTimeMeasure();

  void rc( ULONG rc );

private:
  std::string m_strName;
  bool m_bClassMode;
  bool m_bRCSet;
  ULONG m_rc;
};

#ifdef _DEBUG    
  #define TIMEDEBUG                 CAutoTimeMeasure __cATMe( __FUNCTION__ );
  #define TIMEDEBUG_RC(_value)      __cATMe.rc( _value );
  #define TIMEDEBUG_INFO(_info)     CAutoTimeMeasure __cATMInfo( __FUNCTION__ " (" #_info ")" );
  #define TIMEDEBUG_STRING(_info)   CAutoTimeMeasure __cATMInfo( (char*)(std::string( std::string( __FUNCTION__ ) + _info ).c_str()) );
  #define TIMEDEBUG_CLASS           CAutoTimeMeasure __cATMClass;
  #define TIMEDEBUG_CLASS_CONSTRUCTOR( _className ) ,__cATMClass( #_className , true)
  
#else
  #define TIMEDEBUG
  #define TIMEDEBUG_RC(_value)
  #define TIMEDEBUG_INFO(_info)
  #define TIMEDEBUG_STRING
  #define TIMEDEBUG_CLASS
  #define TIMEDEBUG_CLASS_CONSTRUCTOR( _className )
#endif

#endif // __AUTOTIMEMEASURE_H__
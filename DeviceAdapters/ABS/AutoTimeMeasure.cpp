
#include "AutoTimeMeasure.h"

CAutoTimeMeasure::CAutoTimeMeasure( char* szName, bool bClassMode ) 
: CTimeSpanPC( true )
, m_strName( szName )
, m_bRCSet( false )
, m_bClassMode( bClassMode )
//, m_bClassMode( true )
{ 
  if (m_bClassMode)
  {
    std::string strDbgOut;
    //TRACE2( "%s (08%X) => start\n", m_strName.c_str(), (u32)this );
    str::sprintf( strDbgOut, "%s (08%X) => start\n", m_strName.c_str(), (ULONG)this );
    OutputDebugStringA( strDbgOut.c_str() );
  }
};

CAutoTimeMeasure::~CAutoTimeMeasure()
{
  std::string strDbgOut;
  if (!m_bClassMode)
  {
    if (!m_bRCSet)
      str::sprintf( strDbgOut, "%s \t\t=> %7.3fms [%04X]\n", m_strName.c_str(), getTime_ms(), GetCurrentThreadId() );
    else
      str::sprintf( strDbgOut, "%s \t\t=> %7.3fms [%04X] == %6d\n", m_strName.c_str(), getTime_ms(), GetCurrentThreadId(), m_rc );
  }
  else
    str::sprintf( strDbgOut, "%s (08%X) => end exec: %7.3fms\n", m_strName.c_str(), (ULONG)this, getTime_ms() );
 
  OutputDebugStringA( strDbgOut.c_str() );
}

void CAutoTimeMeasure::rc( ULONG rc )
{
  m_bRCSet = true;
  m_rc     = rc;
}

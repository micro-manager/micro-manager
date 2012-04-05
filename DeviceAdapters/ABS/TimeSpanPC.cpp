#include "TimeSpanPC.h"

CTimeSpanPC::CTimeSpanPC( bool bStart )
{
  if (bStart)  start();
  else  {
    m_qwStart.QuadPart = 0; // reset start
    resetStop();
  }
}

CTimeSpanPC::~CTimeSpanPC(void)
{
}

void CTimeSpanPC::resetStop()
{
  m_qwStop.QuadPart = 0;
}

void CTimeSpanPC::start()
{
  resetStop();
  QueryPerformanceCounter( &m_qwStart );
}

void CTimeSpanPC::stop()
{
  if (0 == m_qwStop.QuadPart)
    QueryPerformanceCounter( &m_qwStop );
}

LARGE_INTEGER CTimeSpanPC::getTime_Delta( bool bResetStop )
{
  if (0 == m_qwStart.QuadPart) start();
  else if (bResetStop) resetStop();
  stop();  
  LARGE_INTEGER liDelta;
  liDelta.QuadPart = m_qwStop.QuadPart - m_qwStart.QuadPart;
  return liDelta;
}

ULONGLONG CTimeSpanPC::getTickCount( bool bResetStop )
{
  LARGE_INTEGER qwFreq;
  LARGE_INTEGER qwDelta = getTime_Delta( bResetStop );  
  QueryPerformanceFrequency( &qwFreq );  
  return  (ULONGLONG)((qwDelta.QuadPart * 1000 + qwFreq.QuadPart / 2) / qwFreq.QuadPart); 
}

double CTimeSpanPC::getTime_ms( bool bResetStop )
{
  LARGE_INTEGER qwFreq;
  LARGE_INTEGER qwDelta = getTime_Delta( bResetStop );  
  QueryPerformanceFrequency( &qwFreq );  
  
  return ((qwDelta.QuadPart * 1000) / (double) qwFreq.QuadPart); 
}

double CTimeSpanPC::getTime_us( bool bResetStop )
{
  LARGE_INTEGER qwFreq;
  LARGE_INTEGER qwDelta = getTime_Delta( bResetStop );  
  QueryPerformanceFrequency( &qwFreq );  
  return ((qwDelta.QuadPart * 1000 * 1000) / (double) qwFreq.QuadPart); 
}
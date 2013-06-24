///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file    SafeUtil.h
//! 
//! \brief    macros for safe release of objects..
//! 
//! \author    ABS GmbH Jena (HBau)
//! 
//! \date    13.2.2006 \n
//!        -> erstellt \n
//! 
///////////////////////////////////////////////////////////////////////////////
#pragma once

/////////////////////////////////////////////////////////////////////////////
//! \name Macros: safe utilities
/////////////////////////////////////////////////////////////////////////////
//!@{

// ----------------------------------------------------------------------------
//
//! \brief <b>Check the file handle if not invalid and close it, then set
//! \brief the file handle to invalid </b>
//!
#define SAFE_CLOSEHANDLE(Handle)    {if ((Handle!=0) && (Handle != INVALID_HANDLE_VALUE)) CloseHandle(Handle); Handle = INVALID_HANDLE_VALUE;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check the event handle if not invalid and close it, then set
//! \brief the event handle to zero </b>
//!
#define SAFE_CLOSEEVENT(Handle)      {if ((Handle!=0) && (Handle != INVALID_HANDLE_VALUE)) CloseHandle(Handle); Handle = 0;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check the socket handle if not invalid and close it, then set
//! \brief the socket handle to invalid </b>
//!
#define SAFE_CLOSESOCKET(_s)          {if (((_s)!=0) && ((_s) != INVALID_SOCKET)) closesocket((_s)); (_s) = INVALID_SOCKET;}


// ----------------------------------------------------------------------------
//
//! \brief <b>Check the socket handle if not invalid and shut it down, then set
//! \brief the socket handle to invalid </b>
//!
#define SAFE_SHUTDOWN(_s, _flag)          {if (( (_s)!=0) && ((_s) != INVALID_SOCKET)) shutdown((_s), (_flag));}


// ----------------------------------------------------------------------------
//
//! \brief close an socketevent
//!
#define SAFE_CLOSESOCKETEVENT(_sevt)  {if ( (_sevt) != WSA_INVALID_EVENT) WSACloseEvent( (_sevt) ); (_sevt)=WSA_INVALID_EVENT;}
#define SAFE_WSA_CLOSEEVENT(_sevt)    {if ( (_sevt) != WSA_INVALID_EVENT) WSACloseEvent( (_sevt) ); (_sevt)=WSA_INVALID_EVENT;}


// ----------------------------------------------------------------------------
//
//! \brief <b>Check if the pointer is not 0, release it and set 
//! \brief it to 0 </b>
//!
#define SAFE_DELETE(Pointer)      {if (Pointer != 0) delete Pointer; Pointer = 0;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check if the pointer to array is not 0, release it
//!  \brief and set it to 0 </b>
//!
#define SAFE_DELETE_ARRAY(Pointer)    {if (Pointer != 0) delete [] Pointer; Pointer = 0;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check if the pointer is not 0, free it and set it to 0 </b>
//!
#define SAFE_FREE(Pointer)            {if (Pointer != 0) free(Pointer); Pointer = 0;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check if the pointer is not 0, free it via IPP and set it to 0 </b>
//!
#define SAFE_FREE_IPP(Pointer)            {if (Pointer != 0) ippsFree(Pointer); Pointer = 0;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Retrun the number of elements of an array </b>
//!
#define CNT_ELEMENTS(pElements)      (sizeof(pElements) / sizeof(pElements[0]))

// ----------------------------------------------------------------------------
//
//! \brief <b>Kill a Windows-Timer with KillTimer by it's ID and 
//! \brief set the ID to zero </b>
//!
#define SAFE_KILLTIMER(dwTimerID)    {if (dwTimerID!=0) KillTimer(dwTimerID); dwTimerID=0;}


// ----------------------------------------------------------------------------
//
//! \brief Release an DirectX object
//!
#define SAFE_RELEASE(Pointer)  {if (Pointer != 0) Pointer->Release(); Pointer = 0;}

//!@}
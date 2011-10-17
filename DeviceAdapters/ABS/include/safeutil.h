///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file		SafeUtil.h
//! 
//! \brief		macros for safe release of objects..
//! 
//! \author		ABS GmbH Jena (HBau)
//! 
//! \date		13.2.2006 \n
//! 			 -> erstellt \n
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
#define SAFE_CLOSEHANDLE(Handle)		{if ((Handle!=0) && (Handle != INVALID_HANDLE_VALUE)) CloseHandle(Handle); Handle = INVALID_HANDLE_VALUE;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check the event handle if not invalid and close it, then set
//! \brief the event handle to zero </b>
//!
#define SAFE_CLOSEEVENT(Handle)			{if ((Handle!=0) && (Handle != INVALID_HANDLE_VALUE)) CloseHandle(Handle); Handle = 0;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check the socket handle if not invalid and close it, then set
//! \brief the socket handle to invalid </b>
//!
#define SAFE_CLOSESOCKET(s)			    {if ((s!=0) && (s != INVALID_SOCKET)) closesocket(s); s = INVALID_SOCKET;}


// ----------------------------------------------------------------------------
//
//! \brief <b>Check the socket handle if not invalid and shut it down, then set
//! \brief the socket handle to invalid </b>
//!
#define SAFE_SHUTDOWN(s, flag)			    {if ((s!=0) && (s != INVALID_SOCKET)) shutdown(s, flag);}


// ----------------------------------------------------------------------------
//
//! \brief close an socketevent
//!
#define SAFE_CLOSESOCKETEVENT(sevt)    {if (sevt != WSA_INVALID_EVENT) WSACloseEvent(sevt); sevt=WSA_INVALID_EVENT;}


// ----------------------------------------------------------------------------
//
//! \brief <b>Check if the pointer is not NULL, release it and set 
//! \brief it to NULL </b>
//!
#define SAFE_DELETE(Pointer)			{if (Pointer != NULL) delete Pointer; Pointer = NULL;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check if the pointer to array is not NULL, release it
//!	\brief and set it to NULL </b>
//!
#define SAFE_DELETE_ARRAY(Pointer)		{if (Pointer != NULL) delete [] Pointer; Pointer = NULL;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check if the pointer is not NULL, free it and set it to NULL </b>
//!
#define SAFE_FREE(Pointer)		        {if (Pointer != NULL) free(Pointer); Pointer = NULL;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Check if the pointer is not NULL, free it via IPP and set it to NULL </b>
//!
#define SAFE_FREE_IPP(Pointer)		        {if (Pointer != NULL) ippsFree(Pointer); Pointer = NULL;}

// ----------------------------------------------------------------------------
//
//! \brief <b>Retrun the number of elements of an array </b>
//!
#define CNT_ELEMENTS(pElements)			(sizeof(pElements) / sizeof(pElements[0]))

// ----------------------------------------------------------------------------
//
//! \brief <b>Kill a Windows-Timer with KillTimer by it's ID and 
//! \brief set the ID to zero </b>
//!
#define SAFE_KILLTIMER(dwTimerID)		{if (dwTimerID!=0) KillTimer(dwTimerID); dwTimerID=0;}


// ----------------------------------------------------------------------------
//
//! \brief Release an DirectX object
//!
#define SAFE_RELEASE(Pointer)  {if (Pointer != NULL) Pointer->Release(); Pointer = NULL;}

//!@}
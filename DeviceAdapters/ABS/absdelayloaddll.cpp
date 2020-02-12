#ifdef WIN32
  #define WIN32_LEAN_AND_MEAN
  #include <windows.h>   
  #pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#endif
#include "FixSnprintf.h"
#include <stdio.h>
#include "delayimp.h"
#include "ABSDelayLoadDll.h"

#pragma comment(lib, "DelayImp.lib")    // add __delayLoadHelper


// ------------------------------ Functions -----------------------------------
//
LONG WINAPI DelayLoadDllExceptionFilter(PEXCEPTION_POINTERS pExcPointers)
{
  LONG lDisposition = EXCEPTION_EXECUTE_HANDLER;
  PDelayLoadInfo pDelayLoadInfo =
    PDelayLoadInfo(pExcPointers->ExceptionRecord->ExceptionInformation[0]);

  switch (pExcPointers->ExceptionRecord->ExceptionCode) {
  case VcppException(ERROR_SEVERITY_ERROR, ERROR_MOD_NOT_FOUND):
    printf("Dll %s was not found\n", pDelayLoadInfo->szDll);
    break;

  case VcppException(ERROR_SEVERITY_ERROR, ERROR_PROC_NOT_FOUND):
    if (pDelayLoadInfo->dlp.fImportByName) {
      printf("Function %s was not found in %s\n",
        pDelayLoadInfo->dlp.szProcName, pDelayLoadInfo->szDll);
    } else {
      printf("Function ordinal %d was not found in %s\n",
        pDelayLoadInfo->dlp.dwOrdinal, pDelayLoadInfo->szDll);
    }
    break; 

  default:
    // Exception is not related to delay loading
    lDisposition = EXCEPTION_CONTINUE_SEARCH;
    break;
  }

  return(lDisposition);
}


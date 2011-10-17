// Delay Load DLL header
#pragma once

#include "delayimp.h"
#pragma comment(lib, "DelayImp.lib")					// add __delayLoadHelper

// ------------------------------ Functions -----------------------------------
//
static LONG WINAPI DelayLoadDllExceptionFilter(PEXCEPTION_POINTERS pExcPointers) {
   LONG lDisposition = EXCEPTION_EXECUTE_HANDLER;
   PDelayLoadInfo pDelayLoadInfo =
    PDelayLoadInfo(pExcPointers->ExceptionRecord->ExceptionInformation[0]);

   switch (pExcPointers->ExceptionRecord->ExceptionCode) {
   case VcppException(ERROR_SEVERITY_ERROR, ERROR_MOD_NOT_FOUND):
      printf("Dll %s was not foundn", pDelayLoadInfo->szDll);
      break;

   case VcppException(ERROR_SEVERITY_ERROR, ERROR_PROC_NOT_FOUND):
      if (pDelayLoadInfo->dlp.fImportByName) {
      printf("Function %s was not found in %sn",
      	      pDelayLoadInfo->dlp.szProcName, pDelayLoadInfo->szDll);
      } else {
      printf("Function ordinal %d was not found in %sn",
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

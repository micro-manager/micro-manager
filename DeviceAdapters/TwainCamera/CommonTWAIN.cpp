/***************************************************************************
* Copyright © 2007 TWAIN Working Group:  
*   Adobe Systems Incorporated, AnyDoc Software Inc., Eastman Kodak Company, 
*   Fujitsu Computer Products of America, JFL Peripheral Solutions Inc., 
*   Ricoh Corporation, and Xerox Corporation.
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above copyright
*       notice, this list of conditions and the following disclaimer in the
*       documentation and/or other materials provided with the distribution.
*     * Neither the name of the TWAIN Working Group nor the
*       names of its contributors may be used to endorse or promote products
*       derived from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY TWAIN Working Group ``AS IS'' AND ANY
* EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
* WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL TWAIN Working Group BE LIABLE FOR ANY
* DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
* (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
* LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
* SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
***************************************************************************/

/**
* @file CommonTWAIN.cpp
* Utilities functions used by TWAIN Data Sources
* 
* @author TWAIN Working Group
* @date October 2007
*/
#include "CommonTWAIN.h"

extern TW_HANDLE _DSM_Alloc(TW_UINT32 _size);
extern TW_MEMREF _DSM_LockMemory(TW_HANDLE _hMemory);
extern void _DSM_UnlockMemory(TW_HANDLE _hMemory);
extern void _DSM_Free(TW_HANDLE _hMemory);


//////////////////////////////////////////////////////////////////////////////
TW_FIX32 FloatToFIX32 (float floater)
{
  TW_FIX32 Fix32_value;
  TW_INT32 value = (TW_INT32) (floater * 65536.0 + 0.5);
  Fix32_value.Whole = (TW_UINT16)(value >> 16);
  Fix32_value.Frac = (TW_UINT16)(value & 0x0000ffffL);
  return (Fix32_value);
}


//////////////////////////////////////////////////////////////////////////////
float FIX32ToFloat(const TW_FIX32& _fix32)
{
  return float(_fix32.Whole) + float(_fix32.Frac / 65536.0);
}


//////////////////////////////////////////////////////////////////////////////
bool getcurrent(TW_CAPABILITY *pCap, TW_UINT32& val)
{
  bool bret = false;

  if(0 != pCap->hContainer)
  {
    if(TWON_ENUMERATION == pCap->ConType)
    {
      pTW_ENUMERATION pCapPT = (pTW_ENUMERATION)_DSM_LockMemory(pCap->hContainer);
      switch(pCapPT->ItemType)
      {
      case TWTY_INT32:
        val = (TW_INT32)((pTW_INT32)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
        bret = true;
        break;

      case TWTY_UINT32:
        val = (TW_INT32)((pTW_UINT32)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
        bret = true;
        break;

      case TWTY_INT16:
        val = (TW_INT32)((pTW_INT16)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
        bret = true;
        break;

      case TWTY_UINT16:
        val = (TW_INT32)((pTW_UINT16)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
        bret = true;
        break;

      case TWTY_INT8:
        val = (TW_INT32)((pTW_INT8)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
        bret = true;
        break;

      case TWTY_UINT8:
        val = (TW_INT32)((pTW_UINT8)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
        bret = true;
        break;

      case TWTY_BOOL:
        val = (TW_INT32)((pTW_BOOL)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
        bret = true;
        break;

      }
      _DSM_UnlockMemory(pCap->hContainer);
    }
    else if(TWON_ONEVALUE == pCap->ConType)
    {
      pTW_ONEVALUE pCapPT = (pTW_ONEVALUE)_DSM_LockMemory(pCap->hContainer);
      val = pCapPT->Item;
      bret = true;
      _DSM_UnlockMemory(pCap->hContainer);
    }
  }

  return bret;
}


//////////////////////////////////////////////////////////////////////////////
bool getcurrent(TW_CAPABILITY *pCap, string& val)
{
  bool bret = false;

  if(0 != pCap->hContainer)
  {
    if(TWON_ENUMERATION == pCap->ConType)
    {
      pTW_ENUMERATION pCapPT = (pTW_ENUMERATION)_DSM_LockMemory(pCap->hContainer);
      switch(pCapPT->ItemType)
      {
      case TWTY_STR32:
        {
          pTW_STR32 pStr = &((pTW_STR32)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
          if(32 < strlen(pStr))
            pStr[32] = 0;
          val = pStr;
          bret = true;
        }
        break;

      case TWTY_STR64:
        {
          pTW_STR64 pStr = &((pTW_STR64)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
          if(64 < strlen(pStr))
            pStr[64] = 0;
          val = pStr;
          bret = true;
        }
        break;

      case TWTY_STR128:
        {
          pTW_STR128 pStr = &((pTW_STR128)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
          if(128 < strlen(pStr))
            pStr[128] = 0;
          val = pStr;
          bret = true;
        }
        break;

      case TWTY_STR255:
        {
          pTW_STR255 pStr = &((pTW_STR255)(&pCapPT->ItemList))[pCapPT->CurrentIndex];
          if(255 < strlen(pStr))
            pStr[255] = 0;
          val = pStr;
          bret = true;
        }
        break;
      }
      _DSM_UnlockMemory(pCap->hContainer);
    }
    else if(TWON_ONEVALUE == pCap->ConType)
    {
      pTW_ONEVALUE pCapPT = (pTW_ONEVALUE)_DSM_LockMemory(pCap->hContainer);

      switch(pCapPT->ItemType)
      {
      case TWTY_STR32:
        {
          pTW_STR32 pStr = ((pTW_STR32)(&pCapPT->Item));
          if(32 < strlen(pStr))
            pStr[32] = 0;
          val = pStr;
          bret = true;
        }
        break;

      case TWTY_STR64:
        {
          pTW_STR64 pStr = ((pTW_STR64)(&pCapPT->Item));
          if(64 < strlen(pStr))
            pStr[64] = 0;
          val = pStr;
          bret = true;
        }
        break;

      case TWTY_STR128:
        {
          pTW_STR128 pStr = ((pTW_STR128)(&pCapPT->Item));
          if(128 < strlen(pStr))
            pStr[128] = 0;
          val = pStr;
          bret = true;
        }
        break;

      case TWTY_STR255:
        {
          pTW_STR255 pStr = ((pTW_STR255)(&pCapPT->Item));
          if(255 < strlen(pStr))
            pStr[255] = 0;
          val = pStr;
          bret = true;
        }
        break;
      }
      _DSM_UnlockMemory(pCap->hContainer);
    }
  }

  return bret;
}
//////////////////////////////////////////////////////////////////////////////

//////////////////////////////////////////////////////////////////////////////
bool getcurrent(TW_CAPABILITY *pCap, TW_FIX32& val)
{
  bool bret = false;

  if(0 != pCap->hContainer)
  {
    if(TWON_ENUMERATION == pCap->ConType)
    {
      pTW_ENUMERATION_FIX32 pCapPT = (pTW_ENUMERATION_FIX32)_DSM_LockMemory(pCap->hContainer);

      if(TWTY_FIX32 == pCapPT->ItemType)
      {
        val = pCapPT->ItemList[pCapPT->CurrentIndex];
        bret = true;
      }
      _DSM_UnlockMemory(pCap->hContainer);
    }
    else if(TWON_ONEVALUE == pCap->ConType)
    {
      pTW_ONEVALUE_FIX32 pCapPT = (pTW_ONEVALUE_FIX32)_DSM_LockMemory(pCap->hContainer);
      
      if(TWTY_FIX32 == pCapPT->ItemType)
      {
        val = pCapPT->Item;
        bret = true;
      }
      _DSM_UnlockMemory(pCap->hContainer);
    }
  }
  
  return bret;
}

//////////////////////////////////////////////////////////////////////////////
bool getcurrent(TW_CAPABILITY *pCap, TW_FRAME& frame)
{
  bool bret = false;

  if(0 != pCap->hContainer)
  {
    if(TWON_ENUMERATION == pCap->ConType)
    {
      pTW_ENUMERATION_FRAME pCapPT = (pTW_ENUMERATION_FRAME)_DSM_LockMemory(pCap->hContainer);

      if(TWTY_FRAME == pCapPT->ItemType)
      {
        frame = pCapPT->ItemList[pCapPT->CurrentIndex];
        bret = true;
      }
      _DSM_UnlockMemory(pCap->hContainer);
    }
    else if(TWON_ONEVALUE == pCap->ConType)
    {
      pTW_ONEVALUE_FRAME pCapPT = (pTW_ONEVALUE_FRAME)_DSM_LockMemory(pCap->hContainer);
      
      if(TWTY_FRAME == pCapPT->ItemType)
      {
        frame = pCapPT->Item;
        bret = true;
      }
      _DSM_UnlockMemory(pCap->hContainer);
    }
  }
  
  return bret;
}

//////////////////////////////////////////////////////////////////////////////
int getTWTYsize(TW_UINT16  ItemType)
{
  int TypeSize = 0;

  switch(ItemType)
  {
  case TWTY_INT8:
    TypeSize = sizeof(TW_INT8);
    break;
  case TWTY_INT16:
    TypeSize = sizeof(TW_INT16);
    break;
  case TWTY_INT32:
    TypeSize = sizeof(TW_INT32);
    break;
  case TWTY_UINT8:
    TypeSize = sizeof(TW_UINT8);
    break;
  case TWTY_UINT16:
    TypeSize = sizeof(TW_UINT16);
    break;
  case TWTY_UINT32:
    TypeSize = sizeof(TW_UINT32);
    break;
  case TWTY_BOOL:
    TypeSize = sizeof(TW_BOOL);
    break;
  case TWTY_FIX32:
    TypeSize = sizeof(TW_FIX32);
    break;
  case TWTY_FRAME:
    TypeSize = sizeof(TW_FRAME);
    break;
  case TWTY_STR32:
    TypeSize = sizeof(TW_STR32);
    break;
  case TWTY_STR64:
    TypeSize = sizeof(TW_STR64);
    break;
  case TWTY_STR128:
    TypeSize = sizeof(TW_STR128);
    break;
  case TWTY_STR255:
    TypeSize = sizeof(TW_STR255);
    break;
  case TWTY_STR1024:
    TypeSize = sizeof(TW_STR1024);
    break;
  case TWTY_UNI512:
    TypeSize = sizeof(TW_UNI512);
    break;

  default:
    break;
  }
  return TypeSize;
}
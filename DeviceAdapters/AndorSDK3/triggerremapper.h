#ifndef _TRIGGERREMAPPER_H_
#define _TRIGGERREMAPPER_H_

#include "atcore++.h"

using namespace andor;

class SnapShotControl;

class TTriggerRemapper : public TAndorEnumFilter
{
public:
   TTriggerRemapper(SnapShotControl* snapShotController, IEnum* _enum)
   :m_snapShotController(snapShotController),TAndorEnumFilter(_enum)
   {
      internal_index = findIndexFor(L"Internal");
      extStart_index = findIndexFor(L"External Start");
   }
  
   int GetIndex()
   {
      if (m_snapShotController->isInternal())
      {
         return internal_index;
      }
      else if ( m_snapShotController->isSoftware() && m_snapShotController->isExternal() )
      {
         return extStart_index;
      }
      else 
      {
         return m_enum->GetIndex();    
      }
   }
  
private:
   SnapShotControl* m_snapShotController;
   int internal_index;
   int extStart_index;

private:
   int findIndexFor(const std::wstring & _pTriggerMode)
   {
      for (int i=0; i<m_enum->Count(); i++)
      {
         if (m_enum->GetStringByIndex(i).compare(_pTriggerMode) == 0) 
         {
            return i;
         }
      }
      return(-1);
   }
};

#endif // _TRIGGERREMAPPER_H_

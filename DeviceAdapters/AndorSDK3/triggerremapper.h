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
      internal_index = findInternal();
   }
  
   int GetIndex()
   {
      if (m_snapShotController->isInternal())
      {
         return internal_index;
      }
      else {
         return m_enum->GetIndex();    
      }
   }
  
   private:
   SnapShotControl* m_snapShotController;
   int internal_index;

   int findInternal()
   {
      for (int i=0; i<m_enum->Count(); i++)
      {
         if (m_enum->GetStringByIndex(i).compare(L"Internal") == 0) {
            return i;
         }
      }
      return(-1);
   }
};

#endif // _TRIGGERREMAPPER_H_
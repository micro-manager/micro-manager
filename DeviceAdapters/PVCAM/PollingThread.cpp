#include "PollingThread.h"

#include "PVCAMAdapter.h"

int PollingThread::svc(void)
{
   int ret = DEVICE_ERR;
   try 
   {
      ret = camera_->PollingThreadRun();
   }
   catch(...)
   {
      camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
   }
   return ret;
}
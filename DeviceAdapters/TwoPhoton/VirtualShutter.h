/**
 * DAShutter: Adds shuttering capabilities to a DA device
 */
class VirtualShutter : public CShutterBase<VirtualShutter>
{
public:
   VirtualShutter();
   ~VirtualShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy(){return false;}

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnDADevice1(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDADevice2(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableDAs_;
   std::string DADeviceName1_;
   std::string DADeviceName2_;
   MM::SignalIO* DADevice1_;
   MM::SignalIO* DADevice2_;
   bool initialized_;
};
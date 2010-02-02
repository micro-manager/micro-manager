///////////////////////////////////////////////////////////////////////////////
// FILE:          Instrutech-ITC18.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ITC18 from Instrutech 
// COPYRIGHT:     University of Massachusetts, Worcester, 2009
// LICENSE:       LGPL
// AUTHOR:        Karl Bellve, Karl.Bellve@umassmed.edu
//

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf
#else
    #include <dlfcn.h> 
#endif

#include "MMITC18.h"
#include "../../MMDevice/ModuleInterface.h"


#define PIPELINE 3 // Size of ITC-18 hardware pipeline
#define FAST_SAMP_INTERVAL_US 0.0001 // seconds
#define SLOW_SAMP_INTERVAL_US 0.001 // seconds
#define ERROR_MESSAGE_LENGTH 100

const char* g_DeviceNameITC18Hub =      "ITC18-Hub";
const char* g_DeviceNameITC18Shutter =  "ITC18-Shutter";
const char* g_DeviceNameITC18DAC =      "ITC18-DAC";
const char* g_DeviceNameITC18ADC =      "ITC18-ADC";
const char* g_DeviceNameITC18Protocol = "ITC18-Protocol";
const char* g_Open = "Open";
const char* g_Close = "Close";
const char* g_Run = "Run";
const char* g_Stop = "Stop";
std::string g_port; 
std::string g_protocol_file = "undefined";  
bool g_DeviceITC18Available = false;
char errorMsg[ERROR_MESSAGE_LENGTH];
int g_ranges[MAX_ADCHANNELS];
unsigned short g_TTLOutState = 0; // bits for setting the TTL devices for shutter control
bool g_triggerMode = false;
bool g_protocol_file_available = false;
const std::string g_version = "1.01";
AcqSequenceThread* g_seqThread;
int *g_Sequence;

// g_currentTime is 1 indexed when accessed by the GUI, but -1 to get 0 indexed to lookup values in pDataOut_ and pDataIn_
// g_currentTime = 0 means don't look into the protocol data, just look at the last live data
// g_currentTime > 0 look at the protocol data, which is why it is 1 indexed.
long g_currentTime;

// one shot for running the shutter or the DA for simple devices
#define ONESHOTDATASIZE 10
#define ONESHOTSEQUENCESIZE 9
static int g_OneShotInstructions[ONESHOTSEQUENCESIZE];
short g_OneShotDataIn[ONESHOTDATASIZE * ONESHOTSEQUENCESIZE];
short g_OneShotDataOut[ONESHOTDATASIZE * ONESHOTSEQUENCESIZE];
short g_currentAD[MAX_ADCHANNELS];
short g_currentDA[MAX_DACHANNELS];

const char* g_ITC18_AD_RANGE_10VString = "±10 Volt";
const char* g_ITC18_AD_RANGE_5VString = "±5 Volt";
const char* g_ITC18_AD_RANGE_2VString = "±2 Volt";
const char* g_ITC18_AD_RANGE_1VString = "±1 Volt";
std::string g_range = g_ITC18_AD_RANGE_10VString; 


void *itc;

#ifdef WIN32
// Windows dll entry routine
bool APIENTRY DllMain( HANDLE /*hModule*/, 
                       DWORD  ul_reason_for_call, 
                       LPVOID /*lpReserved*/ ) {
     switch (ul_reason_for_call) {
          case DLL_PROCESS_ATTACH:
          break;
          case DLL_THREAD_ATTACH:
          case DLL_THREAD_DETACH:
          case DLL_PROCESS_DETACH:
          break;
     }
  return TRUE;
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
    AddAvailableDeviceName(g_DeviceNameITC18Hub, "Hub");
    AddAvailableDeviceName(g_DeviceNameITC18Shutter, "Shutter");
    AddAvailableDeviceName(g_DeviceNameITC18DAC, "DAC");
    AddAvailableDeviceName(g_DeviceNameITC18ADC, "ADC");
    AddAvailableDeviceName(g_DeviceNameITC18Protocol, "Protocol");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (deviceName == 0)
        return 0;

    if (strcmp(deviceName, g_DeviceNameITC18Hub) == 0)
    {
        return new CITC18Hub;
    } else if (strcmp(deviceName, g_DeviceNameITC18Shutter) == 0)
    {
        return new CITC18Shutter;
    } else if (strcmp(deviceName, g_DeviceNameITC18DAC) == 0)
    {
        return new CITC18DAC;
    } else if (strcmp(deviceName, g_DeviceNameITC18ADC) == 0)
    {
        return new CITC18ADC;
    } else if (strcmp(deviceName, g_DeviceNameITC18Protocol) == 0)
    {
        return new CITC18Protocol;
    }

     return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CITC18Hub implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~
//
CITC18Hub::CITC18Hub() :
initialized_ (false)
{
    InitializeDefaultErrorMessages();

    SetErrorText(ERR_USB_ERROR, "USB-18 communication error");
    SetErrorText(ERR_BOARD_NOT_FOUND, "USB-18 board not found");
    SetErrorText(ERR_NO_MEMORY, "USB-18 Out of memory");
    SetErrorText(ERR_ITC18_LIBRARY, "libitc18.0.1.so or itcmm.dll Library not found");
    SetErrorText(ERR_FILE_NOT_FOUND, "Protocol file not found");
    SetErrorText(ERR_ITC18_NO_CHANNELS,"ITC18 needs at least one channel activated");
    SetErrorText(ERR_PROTOCOL_NOT_LOADED,"No protocol loaded to run");

    #ifndef WIN32
    hITC18Dll = dlopen("libitc18.0.1.so", RTLD_LAZY|RTLD_GLOBAL);
    if (!hITC18Dll)
    {	
        LogMessage("CITC18Hub(): Failed to find libitc18.0.1.so library",false);
        //fprintf(stderr,"CITC18Hub(): Failed to find itc18 library\n");
        exit(ERR_ITC18_LIBRARY);
    }
    #else
    hITC18Dll = ::GetModuleHandle("itcmm.dll"); 
    if (!hITC18Dll)
    {	
        LogMessage("CITC18Hub(): Failed to find itcmm.dll library",false);
        //fprintf(stderr,"CITC18Hub(): Failed to find itc18 library\n");
        exit(ERR_ITC18_LIBRARY);
    }
    #endif
    g_seqThread = new AcqSequenceThread(this);

}

CITC18Hub::~CITC18Hub()
{
    Shutdown();

    #ifndef WIN32
    if (hITC18Dll) dlclose(hITC18Dll);
    hITC18Dll = NULL;
    #endif

    g_seqThread->Stop();
    //g_seqThread->wait();

    #ifdef WIN32
    Sleep(10);
    #else 
    usleep(10 * 1000);
    #endif
    
    delete g_seqThread;
    g_seqThread = NULL;
}

void CITC18Hub::GetName(char* name) const
{
    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, g_DeviceNameITC18Hub);
}


bool CITC18Hub::Busy()
{
    //fprintf(stderr,"CITC18Hub::Busy() %d\n",busy_);
    return busy_;
}


int CITC18Hub::Initialize()
{
    int structSize;
    int deviceNumber = 0;
    int busy;
    std::ostringstream eMsg;
    
    // Name
    int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameITC18Hub, MM::String, true);
    if (DEVICE_OK != nRet)
        return nRet;

    // Description
    nRet = CreateProperty(MM::g_Keyword_Description, "Instrutech ITC18 Hub", MM::String, true);
    if (DEVICE_OK != nRet)
        return nRet;

    CPropertyAction* pAct = new CPropertyAction(this, &CITC18Hub::OnVersion);
    CreateProperty("Version", g_version.c_str(), MM::String, true, pAct);  

    // Setup and initialize the ITC18 before we had control over to the SVC thread
    //fprintf(stderr,"AcqSequenceThread::Start() Starting Acquisition Thread\n");
    structSize = ITC18_GetStructureSize();
    
    // Allocate memory for the itc device structure, on which all depends
    itc = calloc(1, structSize);
    if (itc == NULL) {
        LogMessage("CITC18Hub: can't allocate itc structure", false);
        exit(-1);
    }

    if (ITC18_Open(itc, deviceNumber)) {
        LogMessage( "CITC18Hub: ITC18_Open: Failed to find PCI18, Checking for USB18",false);
        if (ITC18_Open(itc, USB18_CL | deviceNumber)) 
        {
            ITC18_GetStatusText(itc, nRet, errorMsg, ERROR_MESSAGE_LENGTH);
            LogMessage(errorMsg,false);
            LogMessage("CITC18Hub: ITC18_Open: Failed to find USB18",false);
            free (itc);
            exit(DEVICE_ERR);
        }
        else LogMessage("CITC18Hub: ITC18_Open: USB18 device Found",false);
    }
    else LogMessage("CITC18Hub: ITC18_Open: PCI18 device Found",false);

    if (ITC18_Reserve(itc, &busy)) {
        ITC18_GetStatusText(itc, nRet, errorMsg, ERROR_MESSAGE_LENGTH);
        LogMessage(errorMsg,false);
        ITC18_Close(itc);
        free (itc);
        exit(DEVICE_ERR);
    }
    busy_ = (bool)busy;

    if (ITC18_Initialize(itc,deviceNumber)) 
    {
        ITC18_GetStatusText(itc, nRet, errorMsg, ERROR_MESSAGE_LENGTH);
        LogMessage(errorMsg,false);
        ITC18_Close(itc);
        free (itc);
        exit(DEVICE_ERR);
    }   
    
    for (int i = 0; i < 8; i++) {
        g_ranges[i] = ITC18_AD_RANGE_10V;
    }

    if (ITC18_SetRange(itc, g_ranges)) {   
        ITC18_GetStatusText(itc, nRet, errorMsg, ERROR_MESSAGE_LENGTH);
        LogMessage(errorMsg,false);
        ITC18_Close(itc);
        free (itc);
        return DEVICE_ERR;
    }

    g_seqThread->SetDevice(itc);

    // This is short sequence instructions for when running shutter or DA in one shot mode
    g_OneShotInstructions[0] = ITC18_OUTPUT_DA0 | ITC18_INPUT_AD0;
    g_OneShotInstructions[1] = ITC18_OUTPUT_DA1 | ITC18_INPUT_AD1;
    g_OneShotInstructions[2] = ITC18_OUTPUT_DA2 | ITC18_INPUT_AD2;
    g_OneShotInstructions[3] = ITC18_OUTPUT_DA3 | ITC18_INPUT_AD3;
    g_OneShotInstructions[4] = ITC18_INPUT_AD4  | ITC18_OUTPUT_SKIP;
    g_OneShotInstructions[5] = ITC18_INPUT_AD5  | ITC18_OUTPUT_SKIP;
    g_OneShotInstructions[6] = ITC18_INPUT_AD6  | ITC18_OUTPUT_SKIP;
    g_OneShotInstructions[7] = ITC18_INPUT_AD7  | ITC18_OUTPUT_SKIP;
    g_OneShotInstructions[8] = ITC18_OUTPUT_DIGITAL1 | ITC18_INPUT_DIGITAL | ITC18_INPUT_UPDATE | ITC18_OUTPUT_UPDATE;

    initialized_ = true;
    g_DeviceITC18Available = true;
    g_currentTime = 0;

    return DEVICE_OK;
}

int CITC18Hub::Shutdown()
{
    if (itc != NULL) {
        ITC18_Close(itc);
        free(itc);
        itc = NULL;
    }
    initialized_ = false;
    g_DeviceITC18Available = false;

    return DEVICE_OK;
}

int CITC18Hub::OnVersion(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    if (pAct == MM::BeforeGet)
    {
        pProp->Set(g_version.c_str());
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CITC18Protocol implementation
// ~~~

CITC18Protocol::CITC18Protocol() :
                    initialized_ (false),
                    busy_(false),
                    images_(0),
                    frames_(1),
                    channels_(1),
                    slices_(1),
                    minTime_(0),
                    maxTime_(0)
{
    InitializeDefaultErrorMessages();
    SetErrorText(ERR_ITC18_HUB_NOT_FOUND,"Hub Device not found.  The ITC18 Hub device is needed to create this device");

}

CITC18Protocol::~CITC18Protocol()
{
    Shutdown();
    if (g_Sequence) delete[] g_Sequence;
}

int CITC18Protocol::Initialize()
{
    int x;

    if (!g_DeviceITC18Available) {
        return ERR_ITC18_HUB_NOT_FOUND;
    }

    // initialize local variables
    for (x = 0; x< MAX_ADCHANNELS; x++) {
        b_AD_[x] = 0;
        g_currentAD[x] = 0;
    }
    for (x = 0; x< MAX_DACHANNELS; x++) {
        StartDAC_[x] = 0;
        b_DA_[x] = 0;
        g_currentDA[x] = 0;
    }
    g_currentTime = 0;
    StartTTL_ = 0;

    g_v = NULL;
    pDataIn_ = NULL;
    pDataOut_ = NULL;

    // Name
    int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameITC18Protocol, MM::String, true);
    if (DEVICE_OK != nRet)
        return nRet;
    
    // Description
    nRet = CreateProperty(MM::g_Keyword_Description, "Instrutech ITC18 Protocol", MM::String, true);
    if (DEVICE_OK != nRet)
        return nRet;

    CPropertyAction* pAct = new CPropertyAction(this, &CITC18Protocol::OnProtocolFile);
    CreateProperty("ProtocolFile",g_protocol_file.c_str(), MM::String, false, pAct);

    pAct = new CPropertyAction(this, &CITC18Protocol::OnRunProtocolFile);
    CreateProperty("Script",g_Stop, MM::String, false, pAct);
    AddAllowedValue("Script", g_Run);
    AddAllowedValue("Script", g_Stop);

    pAct = new CPropertyAction(this, &CITC18Protocol::OnTime);
    CreateProperty("Time","0", MM::Integer, false, pAct);
    SetPropertyLimits("Time", minTime_, maxTime_);

    CreateProperty("Time Tip1", "Set Time = 0 to look at live DA and AD values", MM::String, true);
    CreateProperty("Time Tip2", "Set Time > 0 to look at values of an executed protocol", MM::String, true);

    pAct = new CPropertyAction(this, &CITC18Protocol::OnFrames);
    CreateProperty("Frames","1", MM::Integer, true, pAct);

    pAct = new CPropertyAction(this, &CITC18Protocol::OnSlices);
    CreateProperty("Slices","1", MM::Integer, true, pAct);
    CreateProperty("Slices Tip1", "Slices currently not used", MM::String, true);

    pAct = new CPropertyAction(this, &CITC18Protocol::OnChannels);
    CreateProperty("Channels","1", MM::Integer, true, pAct);

    pAct = new CPropertyAction(this, &CITC18Protocol::OnImages);
    CreateProperty("Images","0", MM::Integer, true, pAct);
    
    initialized_ = true;

    return DEVICE_OK;
}

int CITC18Protocol::Shutdown()
{
     initialized_ = false;

     return DEVICE_OK;
}

bool CITC18Protocol::Busy()
{
     return busy_;
}

void CITC18Protocol::GetName(char* name) const
{
     assert(name_.length() < CDeviceUtils::GetMaxStringLength());
     CDeviceUtils::CopyLimitedString(name, g_DeviceNameITC18Hub);
}


int CITC18Protocol::GetADid(int ID)
{
    switch (ID)    {
        case 0: return (ITC18_INPUT_AD0); break;
        case 1: return (ITC18_INPUT_AD1); break;
        case 2: return (ITC18_INPUT_AD2); break;
        case 3: return (ITC18_INPUT_AD3); break;
        case 4: return (ITC18_INPUT_AD4); break;
        case 5: return (ITC18_INPUT_AD5); break;
        case 6: return (ITC18_INPUT_AD6); break;
        case 7: return (ITC18_INPUT_AD7); break;
    }
    return -1;
}

int CITC18Protocol::GetDAid(int ID)
{
    switch (ID) {
        case 0: return (ITC18_OUTPUT_DA0); break;
        case 1: return (ITC18_OUTPUT_DA1); break;
        case 2: return (ITC18_OUTPUT_DA2); break;
        case 3: return (ITC18_OUTPUT_DA3); break;
    }
    return -1;

}

int CITC18Protocol::RunProtocolFile()
{

    //double sampling_interval;
    //fprintf(stderr," CITC18Hub::RunProtocolFile() g_seqThread->Stop(); \n");
    if (!g_seqThread) 
    {
        fprintf(stderr,"CITC18Hub::RunProtocolFile(): svc() doesn't exist!\n");
        return 0;
    }
    g_seqThread->Stop();
    
    do 
    {
        #ifdef WIN32
        Sleep(10);
        #else 
        usleep(10 * 1000);
        #endif
    } while (g_seqThread->Busy() == true);
    //g_seqThread->wait();

    
    MaxChannels_ = MaxChannels();
    if (MaxChannels_ < 1) return ERR_ITC18_NO_CHANNELS;
    //fprintf(stderr,"CITC18Hub::RunProtocolFile() MaxChannels %d\n",MaxChannels_);
    
    // Data
    DataSize_ = ((MaxChannels_ * maxTime_) + PIPELINE);
    if (pDataIn_) free(pDataIn_);
    pDataIn_ = (short *)calloc(DataSize_, sizeof(short));
    if (!pDataIn_) { 
        fprintf(stderr,"CITC18Hub::RunProtocolFile() Failed to allocate pDataIn_! %d\n",DataSize_);
        return ERR_NO_MEMORY;
    }
    
    if (pDataOut_) free(pDataOut_);
    pDataOut_ = (short *)calloc(DataSize_,sizeof(short));
    if (!pDataOut_) 
    {
        free (pDataIn_); 
        pDataIn_ = NULL;
        fprintf(stderr," CITC18Hub::RunProtocolFile() Failed to allocate pDataOut_! %d\n",DataSize_);
        return ERR_NO_MEMORY;
    }

    // build buffer
    //fprintf(stderr," CITC18Hub::RunProtocolFile() BuildBuffer()\n");
    BuildBuffer(pDataOut_,0, maxTime_);
    
    // Sequence 
    SetupSequence();
    //fprintf(stderr," CITC18Hub::RunProtocolFile() SetSequence()\n");
    g_seqThread->SetSequence(MaxChannels_,g_Sequence);

    // send buffer to ITC18 acquisition thread
    //fprintf(stderr," CITC18Hub::RunProtocolFile() SetDataOut()\n");
    g_seqThread->SetDataOut(DataSize_,pDataOut_);

    //fprintf(stderr," CITC18Hub::RunProtocolFile() SetDataIn()()\n");
    g_seqThread->SetDataIn(DataSize_,pDataIn_);

    
    // must be loaded after sequence is loaded
    //fprintf(stderr," CITC18Hub::RunProtocolFile() SetInterval()\n");
    g_seqThread->SetInterval(SLOW_SAMP_INTERVAL_US);
    
    
    // mode 1 = one shot, using the runonce mode
    //fprintf(stderr," CITC18Hub::RunProtocolFile() Start()\n");
    g_seqThread->Start(0);

    return DEVICE_OK;

}

void CITC18Protocol::BuildBuffer(short *pBuffer, int start, int end) // in units of time, at the given rate, but at the number of buffer points
{
    int DACposition[MAX_DACHANNELS];
    int y = 0;

    //fprintf(stderr,"CITC18Hub::BuildBuffer() MaxChannels_ %d start %d end %d\n",MaxChannels_,start,end);

    for (int x = 0; x < MAX_DACHANNELS; x++)
    {
        if (b_DA_[x]) 
        {
            //fprintf(stderr,"CITC18Hub::BuildBuffer() building b_DA_[%d]\n",x);
            if (start > StartDAC_[x]) 
                DACposition[x] = myremainder (start,v_DA_[x].size()); // sets the correct starting in the DAC buffer
            else DACposition[x] = start;           
            
            // copy the buffer into the main buffer
            for (int index = start + y; index < (end * MaxChannels_); index += MaxChannels_)
            {
                if (DACposition[x] >= v_DA_[x].size()) DACposition[x] = StartDAC_[x]; // restart at the beginning of the DAC0 buffer
                
                pBuffer[index] = v_DA_[x].at(DACposition[x]); // increment AFTER, since it is zero indexed
                
                DACposition[x]++;
            }
            y++;
            
        }
    }

    // build TTL buffer, if needed 
    // #define myremainder(a,b) ((a) - (int)((a) / (b)) * (b))
    if (b_TTLOUT_) 
    {
        if (start > StartTTL_)
            TTLposition_= myremainder (start,v_TTLOUT_.size());
        else TTLposition_ = start;
        
        for (int index = (start + MaxChannels_ - 1); index < (end * MaxChannels_); index += MaxChannels_)
        {
            //fprintf(stderr,"CITC18Hub::BuildBuffer() building b_TTLOUT_ %d\n",index);
            if (TTLposition_ >= v_TTLOUT_.size()) TTLposition_ = StartTTL_; // restart at the beginning of the DAC0 buffer

            pBuffer[index]    = v_TTLOUT_.at(TTLposition_); // increment AFTER, since it is zero indexed
            TTLposition_++;
        }
    }

    return;
}

int CITC18Protocol::SetupSequence()
{   
    int DA = 0, AD = 0, y;

    if (MaxChannels_ < 1) return false;

    if (g_Sequence) delete[] g_Sequence;
    g_Sequence = new int[MaxChannels_];

    
    for (int x = 0; x < MaxChannels_; x++)
    {
        g_Sequence[x] = 0; // clear the memory

        if (DA == MAX_DACHANNELS) g_Sequence [x] |= ITC18_OUTPUT_SKIP; // set all remaining channels to ITC18_OUTPUT_SKIP
        for (y = AD; y < 8; y++,AD++) // 8 is the maxinum number of channels for ADC
        {
            
            if (b_AD_[y]) 
            {
                g_Sequence [x] |= GetADid(y);
                AD++;
                break;
            }
            else if (y == 7) g_Sequence [x] |= ITC18_INPUT_SKIP; // set all remaining channels to   
        }
        for (y = DA; y < MAX_DACHANNELS; y++,DA++) // 4 is the maxinum number of channels for DAC
        {
            if (b_DA_[y]) 
            {
                g_Sequence [x] |= GetDAid(y);
                DA++;
                break;
            }
            else if (y == 3) g_Sequence [x] |= ITC18_OUTPUT_SKIP; // set all remaining channels to   
        }      
    }

    // need to set up the TTL in/out channels
    if (b_TTLIN_ || b_TTLOUT_) 
    {
        g_Sequence [MaxChannels_-1] = 0;
        if (b_TTLIN_)  g_Sequence [MaxChannels_-1]  =  ITC18_INPUT_DIGITAL | ITC18_INPUT_UPDATE;
        else g_Sequence [MaxChannels_-1]  =  ITC18_INPUT_SKIP;
        if (b_TTLOUT_) g_Sequence [MaxChannels_-1] |=  ITC18_OUTPUT_DIGITAL1 | ITC18_OUTPUT_UPDATE;
        else g_Sequence [MaxChannels_-1]  |=  ITC18_OUTPUT_SKIP;
    } 

    if (MaxDAChannels_ > 0) g_Sequence[MaxChannels_-1] |= ITC18_OUTPUT_UPDATE;
    if (MaxADChannels_ > 0) g_Sequence[MaxChannels_-1] |= ITC18_INPUT_UPDATE;

    //for (y = 0; y <MaxChannels_; y++)
    //{
    //   fprintf(stderr,"CITC18Hub::SetupSequence() %4x\n",g_Sequence[y]);
    //}
    
    return true;
}

int CITC18Protocol::MaxChannels()
{
    int x;

    MaxDAChannels_ = 0;
    MaxADChannels_ = 0;
    MaxChannels_ = 0;


    // initialize local variables
    for (x = 0; x< MAX_ADCHANNELS; x++) if (b_AD_[x]) MaxADChannels_++;
    for (x = 0; x< MAX_DACHANNELS; x++) if (b_DA_[x]) MaxDAChannels_++;

    MaxChannels_ = (MaxADChannels_ > MaxDAChannels_) ? MaxADChannels_ : MaxDAChannels_;
    if (b_TTLOUT_ || b_TTLIN_) MaxChannels_++; 

    return MaxChannels_;

}

int CITC18Protocol::LoadProtocolFile(const char* fileName)
{
    std::ifstream is;
    is.open(fileName, std::ifstream::in);
    int value = 0, index,x;
    
    if (!is.is_open())
    {
        fprintf(stderr,"CITC18Hub::LoadProtocolFile failed\n");
        return ERR_FILE_NOT_FOUND;
    }

    // clear out buffers
    for (x = 0; x< MAX_ADCHANNELS; x++) 
    {
        b_AD_[x] = false;
        v_AD_[x].clear();
    }
    for (x = 0; x< MAX_DACHANNELS; x++) 
    {
        StartDAC_[x] = 0;
        b_DA_[x] = false;
        v_DA_[x].clear();
    }
    v_TTLIN_.clear();
    v_TTLOUT_.clear();
    maxTime_ = 1;

    if (pDataIn_) free(pDataIn_);
    if (pDataOut_) free(pDataOut_);
    pDataIn_ = pDataOut_ = NULL;

    // Process commands
    const int maxLineLength = 4 * MM::MaxStrLength + 4; // accomodate up to 4 strings and delimiters
    char line[maxLineLength+1];

    while (is.getline(line, maxLineLength, '\n')) 
    {
        busy_ = true;
        // strip a potential Windows/dos CR
        std::istringstream il(line);
        il.getline(line, maxLineLength, '\r');
        if (strlen(line) > 0)
        {
            if (!ParseHeader(line))
            {
                
                if (g_v) {
                        if ((line[0] != '#') &&
                        (line[0] != '@') &&
                        (line[0] != '&')) 
                        {
                        sscanf(line,"%d %d",&index,&value);
                        g_v->push_back((short)value);
                        
                        }
                }
            }
        }
    }
    
    /*
    if (b_AD_[0]) fprintf(stderr,"CITC18Hub::LoadProtocolFile AD0 size %d\n",v_AD_[0].size());
    if (b_DA_[0]) fprintf(stderr,"CITC18Hub::LoadProtocolFile DA0 size %d\n",v_DA_[0].size());
    if (b_DA_[1]) fprintf(stderr,"CITC18Hub::LoadProtocolFile DA1 size %d\n",v_DA_[1].size());
    if (b_DA_[2]) fprintf(stderr,"CITC18Hub::LoadProtocolFile DA2 size %d\n",v_DA_[2].size());
    if (b_DA_[3]) fprintf(stderr,"CITC18Hub::LoadProtocolFile DA3 size %d\n",v_DA_[3].size());
    if (b_TTLOUT_) fprintf(stderr,"CITC18Hub::LoadProtocolFile TTLOUT size %d\n",v_TTLOUT_.size());
    */
    /* std::vector<short>::iterator iter;
    
    if (v_TTLOUT_.size() > 0) {
        for( iter = v_TTLOUT_.begin(); iter != v_TTLOUT_.end(); iter++ ) {
            fprintf(stderr,"CITC18Hub::LoadProtocolFile TTLOUT value %d\n",*iter);
        }
    }
    */


    busy_ = false;

    return true;

}

int CITC18Protocol::ParseHeader(char *line_)
{
     int length;
     std::string line = line_;
     std::string line2;
     length = line.length();
               
          if (line[0] == '@')
          {    // these are some official xmgrace params that we will use for our own as well
               
               if ((line.find("@with ") != std::string::npos) || (line.find("@WITH ") != std::string::npos))
               {    
                    if ((line.find("g0") != std::string::npos) || (line.find("G0") != std::string::npos)) g_v = &v_AD_[0];
                    if ((line.find("g1") != std::string::npos) || (line.find("G1") != std::string::npos)) g_v = &v_TTLIN_;
                    if ((line.find("g2") != std::string::npos) || (line.find("G2") != std::string::npos)) g_v = &v_TTLOUT_;
                    if ((line.find("g3") != std::string::npos) || (line.find("G3") != std::string::npos)) g_v = &v_DA_[0];
                    if ((line.find("g4") != std::string::npos) || (line.find("G4") != std::string::npos)) g_v = &v_DA_[1];
                    if ((line.find("g5") != std::string::npos) || (line.find("G5") != std::string::npos)) g_v = &v_DA_[2];
                    if ((line.find("g6") != std::string::npos) || (line.find("G6") != std::string::npos)) g_v = &v_DA_[3];
               }
               return (true);

          }    

          if (line[0] == '#') 
          {    // these are comment lines for Xmgr, but we are storing some parameters in them as well
               if (line.find("# IMAGES") != std::string::npos )  {
                    if (length > 8) {
                         line2 = line.substr(8,length - 8);
                         images_ = atoi(line2.c_str());
                    }
               } else
               if (line.find("# SLICES ") != std::string::npos )  {
                    if (length > 9) {
                         line2 = line.substr(9,length - 9);
                         slices_ = atoi(line2.c_str());
                         if (slices_ < 1) slices_ = 1;
                    }
               } else
               if (line.find("# CHANNELS ") != std::string::npos )  {
                    if (length > 11) {
                         line2 = line.substr(11,length - 11);
                         channels_ = atoi(line2.c_str());
                         if (channels_ < 1) channels_ = 1;
                    }
               } else
               if (line.find("# TIME") != std::string::npos )  {
                    if (length > 6) {
                         line2 = line.substr(6,length - 6);
                         maxTime_ = atoi(line2.c_str());
                         SetPropertyLimits("Time", minTime_, maxTime_);
                    }
               } else
               if (line.find("# TTLIN on") != std::string::npos )   b_TTLIN_ = true;
               else

               if (line.find("# TTLOUT on") != std::string::npos )   b_TTLOUT_ = true;
               else

               if (line.find("# DA0 on") != std::string::npos )   b_DA_[0] = true;
               else

               if (line.find("# DA0 off") != std::string::npos )  b_DA_[0] = false;
               else

               if (line.find("# DA1 on") != std::string::npos )   b_DA_[1] = true;
               else

               if (line.find("# DA1 off") != std::string::npos )  b_DA_[1] = false;
               else

               if (line.find("# DA2 on") != std::string::npos )   b_DA_[2] = true;
               else

               if (line.find("# DA2 off") != std::string::npos )  b_DA_[2] = false;
               else

               if (line.find("# DA3 on") != std::string::npos )   b_DA_[3] = true;
               else

               if (line.find("# DA3 off") != std::string::npos )  b_DA_[3] = false;
               else

               if (line.find("# AD0 on") != std::string::npos )   b_AD_[0] = true;
               else

               if (line.find("# AD0 off") != std::string::npos )  b_AD_[0] = false;
               else
                    
               if (line.find("# AD1 on") != std::string::npos )   b_AD_[1] = true;
               else

               if (line.find("# AD1 off") != std::string::npos )  b_AD_[1] = false;
               else

               if (line.find("# AD2 on") != std::string::npos )   b_AD_[2] = true;
               else

               if (line.find("# AD2 off") != std::string::npos )  b_AD_[2] = false;
               else

               if (line.find("# AD3 on") != std::string::npos )   b_AD_[3] = true;
               else

               if (line.find("# AD3 off") != std::string::npos )  b_AD_[3] = false;
               else

               if (line.find("# AD4 on") != std::string::npos )   b_AD_[4] = true;
               else

               if (line.find("# AD4 off") != std::string::npos )  b_AD_[4] = false;  
               else

               if (line.find("# AD5 on") != std::string::npos )   b_AD_[5] = true;
               else

               if (line.find("# AD5 off") != std::string::npos )  b_AD_[5] = false;
               else

               if (line.find("# AD6 on") != std::string::npos )   b_AD_[6] = true;
               else

               if (line.find("# AD6 off") != std::string::npos )  b_AD_[6] = false;
               else
                    
               if (line.find("# AD7 on") != std::string::npos )   b_AD_[7] = true;
               else

               if (line.find("# AD7 off") != std::string::npos )  b_AD_[7] = false;
          
               return true;
          }

     return false;
}

int CITC18Protocol::FindInSequence(int ID)
{
    if (!g_Sequence) return 0;

    if (ID == 0) return 0; // default position for ADC 0 and DAC 0

    for (int x = 1; x < MaxChannels_;x++)
    {
        if (g_Sequence[x] & ID) return x;
    }

    return 0;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int CITC18Protocol::OnProtocolFile(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::string prop;
    pProp->Get(prop);
    //fprintf(stderr, "CITC18Hub: OnProtocolFile: %s.\n", prop.c_str());
    
    if (pAct == MM::BeforeGet)
    {
        pProp->Set(g_protocol_file.c_str());
    }
    else {
        if (pAct == MM::AfterSet) 
        {
            pProp->Get(prop);
            // changing protocol file, lets load the new file
            if (prop != g_protocol_file)
            {  
                g_protocol_file = prop;
                //fprintf(stderr,"CITC18Hub::OnProtocolFile %s\n",g_protocol_file.c_str());
                LoadProtocolFile(g_protocol_file.c_str());
            }
        }
    }
return DEVICE_OK;
}

int CITC18Protocol::OnRunProtocolFile (MM::PropertyBase* pProp, MM::ActionType pAct)
{

    if (pAct == MM::BeforeGet)
    {
        pProp->Set(g_Stop);
    }
    else if (pAct == MM::AfterSet)
    {
        std::string command;
        pProp->Get(command);

        if (g_protocol_file.c_str() == "undefined") return DEVICE_OK;
        if (command == g_Run) RunProtocolFile();
        pProp->Set(g_Stop);
    }

return DEVICE_OK;
}

int CITC18Protocol::OnTime (MM::PropertyBase* pProp, MM::ActionType pAct)
{
    int x;

    if (pAct == MM::BeforeGet)
    {
        pProp->Set(g_currentTime);
    }
    else if (pAct == MM::AfterSet)
    {
        long time;
        pProp->Get(time);
        if (g_currentTime != time && g_Sequence != NULL)
        {
            if (time <= maxTime_) 
            {
                g_currentTime = time;
                for (x = 0; x < MAX_ADCHANNELS; x++) 
                {
                    if (b_AD_[x] && pDataIn_ != NULL) 
                    {
                        g_currentAD[x] = pDataIn_[((g_currentTime - 1) * MaxChannels_) + FindInSequence(GetADid(x)) + PIPELINE];
                        fprintf(stderr,"%d AD %d = %d %d\n",g_currentTime -1,x,g_currentAD[x],((g_currentTime - 1) * MaxChannels_) + FindInSequence(GetADid(x)) + PIPELINE);
                    }
                    else g_currentAD[x] = 0;
                }
                for (x = 0; x < MAX_DACHANNELS; x++) 
                {
                    if (b_DA_[x] && pDataOut_ != NULL) 
                    {
                        g_currentDA[x] = pDataOut_[((g_currentTime - 1) * MaxChannels_) + FindInSequence(GetDAid(x))];
                        fprintf(stderr,"%d DA %d = %d %d\n",g_currentTime -1,x,g_currentDA[x],((g_currentTime -1 ) * MaxChannels_) + FindInSequence(GetDAid(x) ));
                    }
                    else g_currentDA[x] = 0;
                }
            }
        }
    }
    return DEVICE_OK;
}

int CITC18Protocol::OnImages (MM::PropertyBase* pProp, MM::ActionType pAct)
{

    if (pAct == MM::BeforeGet)
    {
        pProp->Set(images_);
    }

     return DEVICE_OK;
}

int CITC18Protocol::OnFrames (MM::PropertyBase* pProp, MM::ActionType pAct)
{

    if (pAct == MM::BeforeGet)
    {
        frames_ = images_/channels_;
        pProp->Set(frames_);
    }

     return DEVICE_OK;
}

int CITC18Protocol::OnSlices (MM::PropertyBase* pProp, MM::ActionType pAct)
{

    if (pAct == MM::BeforeGet)
    {
        pProp->Set(slices_);
    }

     return DEVICE_OK;
}
int CITC18Protocol::OnChannels (MM::PropertyBase* pProp, MM::ActionType pAct)
{

    if (pAct == MM::BeforeGet)
    {
        pProp->Set(channels_);
    }

     return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CITC18Shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CITC18Shutter::CITC18Shutter() : 
    initialized_(false),
    busy_(false),
    TTLPort_(0),
    lastCommand_("Undefined"),
    closingTimeMs_(2.5),
    openingTimeMs_(2.5),
    name_(g_DeviceNameITC18Shutter)
{
    InitializeDefaultErrorMessages();
    EnableDelay();
    SetErrorText(ERR_ITC18_HUB_NOT_FOUND,"Hub Device not found.  The ITC18 Hub device is needed to create this device");

    // create pre-initialization properties
    // ------------------------------------

    // TTL Port
    CPropertyAction *pAct = new CPropertyAction (this, &CITC18Shutter::OnTTLPort);
    CreateProperty("TTLPort", "0", MM::Integer, false, pAct,true);

    std::vector<std::string> vals; 
    vals.push_back("0");
    vals.push_back("1");
    vals.push_back("2");
    vals.push_back("3");
    vals.push_back("4");
    vals.push_back("5");
    vals.push_back("6");
    vals.push_back("7");
    vals.push_back("8");
    vals.push_back("9");
    vals.push_back("10");
    vals.push_back("11");
    vals.push_back("12");
    vals.push_back("13");
    vals.push_back("14");
    vals.push_back("15");
    SetAllowedValues("TTLPort", vals);

}

CITC18Shutter::~CITC18Shutter()
{
    Shutdown();
}

void CITC18Shutter::GetName(char* name) const
{
    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CITC18Shutter::Busy()
{
    MM::MMTime interval = GetCurrentMMTime() - changedTime_;

    // check whether the shutter had enough time to open or close after the command was issued
    if (lastCommand_ == g_Open) {
        if (interval > (MM::MMTime)(openingTimeMs_*1000) ) 
        return false;
        else
        return true;
    } else if (lastCommand_ == g_Close) {
        if (interval > (MM::MMTime)(closingTimeMs_) ) 
        return false;
        else
        return true;
    }
    return false;
}

int CITC18Shutter::Initialize()
{
    if (!g_DeviceITC18Available) {
        return ERR_ITC18_HUB_NOT_FOUND;
    }

    // set property list
    // -----------------

    // Name
    CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);


    // Description
    CreateProperty(MM::g_Keyword_Description, "ITC18 shutter driver", MM::String, true);   
    
    // OnOff
    CPropertyAction* pAct = new CPropertyAction (this, &CITC18Shutter::OnCommand);
    int ret = CreateProperty("Command", g_Close, MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    std::vector<std::string> commands;
    commands.push_back(g_Close);
    commands.push_back(g_Open);
    ret = SetAllowedValues("Command", commands);
    if (ret != DEVICE_OK)
        return ret;


    pAct = new CPropertyAction (this, &CITC18Shutter::OnClosingTime);
    CreateProperty("Time to close (ms)", "A", MM::Float, false, pAct, true);

    
    pAct = new CPropertyAction (this, &CITC18Shutter::OnOpeningTime);
    CreateProperty("Time to open (ms)", "A", MM::Float, false, pAct, true);

    
    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    changedTime_ = GetCurrentMMTime();
    initialized_ = true;



   return DEVICE_OK;
}

int CITC18Shutter::Shutdown()
{
    if (initialized_)
    {
        initialized_ = false;
    }

    return DEVICE_OK;
}

int CITC18Shutter::SetOpen(bool open)
{
    std::string pos;

    if (open)
        return SetProperty("Command", g_Open);
    else
        return SetProperty("Command", g_Close);
}

int CITC18Shutter::GetOpen(bool& open)
{
    char buf[MM::MaxStrLength];
    int ret = GetProperty("Command", buf);
    if (ret != DEVICE_OK)
        return ret;
    long pos = atol(buf);
    pos > 0 ? open = true : open = false;

    return DEVICE_OK;
}

int CITC18Shutter::Fire(double /*deltaT*/)
{
    return DEVICE_UNSUPPORTED_COMMAND;
}

int CITC18Shutter::ITC18RunOnce(short value)
{
    // reset short buffer to send to ITC18

    if (!g_seqThread) 
    {
        fprintf(stderr,"CITC18Shutter::ITC18RunOnce: svc() thread doesn't exist!\n");
        return 0;
    }
    
    g_seqThread->Stop();
    //g_seqThread->wait();

    for (int i = 1; i <= ONESHOTDATASIZE; i++) {
        // TTL Data for controlling shutters
        g_OneShotDataOut[(ONESHOTSEQUENCESIZE * i) - 1] = value;
    }

    g_seqThread->SetSequence(ONESHOTSEQUENCESIZE,g_OneShotInstructions);
    g_seqThread->SetDataOut(ONESHOTDATASIZE * ONESHOTSEQUENCESIZE,g_OneShotDataOut);
    g_seqThread->SetDataIn(ONESHOTDATASIZE * ONESHOTSEQUENCESIZE,g_OneShotDataIn);
    // must be loaded after sequence is loaded
    g_seqThread->SetInterval(FAST_SAMP_INTERVAL_US);
    // mode 1 = one shot
    g_seqThread->Start(1);

    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CITC18Shutter::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        // use cached state
        pProp->Set(command_.c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(command_);
        int ret;

        if (command_ == lastCommand_) return DEVICE_OK;

        if (command_ == g_Open) g_TTLOutState |= (1UL << TTLPort_);
        else g_TTLOutState &= ~(1UL << TTLPort_);
        
        ret = ITC18RunOnce(g_TTLOutState); // restore old setting
        if (ret != DEVICE_OK)
        return ret;
        
            // Last command sent to the controller
        lastCommand_ = command_;
        changedTime_ = GetCurrentMMTime();
    }

     return DEVICE_OK;
}

/*
 * Sets the TTL Port to be used.
 * Should be called before initialization
 */
int CITC18Shutter::OnTTLPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(TTLPort_);
    }
    else if (eAct == MM::AfterSet)
    {
        long port;
        pProp->Get(port);
        TTLPort_= port;  
        //fprintf(stderr, "CITC18Shutter: setting shutter port to %d\n",TTLPort_);
    }

    return DEVICE_OK;
}

/**
 * Sets and gets the time needed for the shutter to open after the serial command is sent
 */
int CITC18Shutter::OnOpeningTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(openingTimeMs_);
    }
    else if (eAct == MM::AfterSet)
    {
        // How do we check whether this value is OK?  Can pProp set a private variable? - NS
        pProp->Get(openingTimeMs_);
    }
    return DEVICE_OK;
}


/**
 * Sets and gets the time needed for the shutter to close after the serial command is sent
 */
int CITC18Shutter::OnClosingTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(closingTimeMs_);
    }
    else if (eAct == MM::AfterSet)
    {
        // How do we check whether this value is OK?  Can pProp set a private variable? - NS
        pProp->Get(closingTimeMs_);
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CITC18DACC implementation
// 

CITC18DAC::CITC18DAC() : 
     busy_(false),
     volts_(0.0),
     DACPort_(0),
     minV_(-10.24),
     maxV_(10.24),
     maxChannel_(MAX_DACHANNELS),
     gateOpen_(true),
     name_(g_DeviceNameITC18DAC)
{
    InitializeDefaultErrorMessages();
    SetErrorText(ERR_ITC18_HUB_NOT_FOUND,"Hub Device not found.  The ITC18 Hub device is needed to create this device");
    
    
    // DAC Port
    CPropertyAction *pAct = new CPropertyAction (this, &CITC18DAC::OnDACPort);
    CreateProperty("DACPort", "0", MM::Integer, false, pAct,true);

    std::vector<std::string> vals; 
    vals.push_back("0");
    vals.push_back("1");
    vals.push_back("2");
    vals.push_back("3");
    SetAllowedValues("DACPort", vals);

    pAct = new CPropertyAction(this, &CITC18DAC::OnMaxVolt);
    CreateProperty("MaxVolt", "10.24", MM::Float, false, pAct);


}

CITC18DAC::~CITC18DAC()
{
     Shutdown();
}

int CITC18DAC::Initialize()
{

    if (!g_DeviceITC18Available) {
        return ERR_ITC18_HUB_NOT_FOUND;
    }
        // Name
    CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

    // Description
    CreateProperty(MM::g_Keyword_Description, "ITC18 DAC driver", MM::String, true);


    // State
    // -----
    CPropertyAction* pAct = new CPropertyAction (this, &CITC18DAC::OnVolts);
    int nRet = CreateProperty("Volts", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK)
        return nRet;
    SetPropertyLimits("Volts", minV_, maxV_);

    nRet = UpdateStatus();
    if (nRet != DEVICE_OK)
        return nRet;

    initialized_ = true;

    return DEVICE_OK;

}

void CITC18DAC::GetName(char* name) const
{
    assert(name_.length() < CDeviceUtils::GetMaxStringLength());  
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int CITC18DAC::Shutdown()
{
    initialized_ = false;
    return DEVICE_OK;
}


int CITC18DAC::SetSignal(double volts)
{
    volts_ = volts;

    if (gateOpen_) {
        gatedVolts_ = volts_;
        return ITC18RunOnce(volts_);
    } else {
        gatedVolts_ = 0;
    }

    return DEVICE_OK;
}

int CITC18DAC::SetGateOpen(bool open)
{
    if (open) {
        gateOpen_ = true;
        gatedVolts_ = volts_;
        return ITC18RunOnce(volts_);
    } else {
        gateOpen_ = false;
        gatedVolts_ = 0;
        return ITC18RunOnce(0.0);
    }

   //return DEVICE_OK;
}

int CITC18DAC::ITC18RunOnce(double value)
{
    // reset short buffer to send to ITC18
    double answer;

    if (!g_seqThread) 
    {
        fprintf(stderr,"CITC18DAC::ITC18RunOnce: svc() thread doesn't exist!\n");
        return 0;
    }
    
    
    answer = ((value * 32767)/10.24);
    
    for (int i = 0; i < ONESHOTDATASIZE; i++) {
        // TTL Data for controlling shutters
        g_OneShotDataOut[(i * ONESHOTSEQUENCESIZE) + DACPort_] = (short)answer;
        //fprintf(stderr,"CITC18DAC::ITC18RunOnce() DACPort_ %d memory %d\n",DACPort_,(i * ONESHOTSEQUENCESIZE) + DACPort_);
    }

    if (g_seqThread->Busy() == false) 
    {
        g_seqThread->Stop();
        //g_seqThread->wait();
        //fprintf(stderr,"CITC18DAC::ITC18RunOnce() DACPort_ %d memory %d\n",DACPort_,(i * ONESHOTSEQUENCESIZE) + DACPort_);
        g_seqThread->SetSequence(ONESHOTSEQUENCESIZE,g_OneShotInstructions);
        g_seqThread->SetDataOut(ONESHOTDATASIZE * ONESHOTSEQUENCESIZE,g_OneShotDataOut);
        g_seqThread->SetDataIn(ONESHOTDATASIZE * ONESHOTSEQUENCESIZE,g_OneShotDataIn);
        // must be loaded after sequence is loaded
        g_seqThread->SetInterval(FAST_SAMP_INTERVAL_US);
        // mode 1 = one shot
        g_seqThread->Start(1);
    }  else fprintf(stderr, "CITC18ADC::ITC18RunOnce svc already running\n");
    
    return DEVICE_OK;
}

//////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CITC18DAC::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    //fprintf(stderr,"CITC18DAC::OnVolts()\n");
    if (eAct == MM::BeforeGet)
    {
        if (g_currentTime == 0) pProp->Set(volts_);
        else pProp->Set((g_currentDA[DACPort_] * 10.24)/32767);
    }
    else if (eAct == MM::AfterSet)
    {
        double volts;
        pProp->Get(volts);
        if (g_currentTime == 0) return SetSignal(volts);
    }

    return DEVICE_OK;
}

int CITC18DAC::OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    double volts;
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(maxV_);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(volts);
        if (volts > 10.24) volts = 10.24;
        if (HasProperty("Volts"))
        SetPropertyLimits("Volts", 0.0, maxV_);

    }
    return DEVICE_OK;
}

int CITC18DAC::OnDACPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set((long int)DACPort_);
    }
    else if (eAct == MM::AfterSet)
    {
        long channel;
        pProp->Get(channel);
        if (channel >= 0 && ( (unsigned) channel < maxChannel_) )
        DACPort_ = channel;
    }
    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CITC18DACC implementation
// 

CITC18ADC::CITC18ADC() : 
    busy_(false),
    volts_(0.0),
    ADCPort_(0),
    maxChannel_(8),
    gateOpen_(true),
    range_(g_ITC18_AD_RANGE_10VString),
    name_(g_DeviceNameITC18ADC)
{
    InitializeDefaultErrorMessages();
    SetErrorText(ERR_ITC18_HUB_NOT_FOUND,"Hub Device not found.  The ITC18 Hub device is needed to create this device");
    
    // ADC Port
    CPropertyAction *pAct = new CPropertyAction (this, &CITC18ADC::OnADCPort);
    CreateProperty("ADCPort", "0", MM::Integer, false, pAct,true);

    std::vector<std::string> vals; 
    vals.push_back("0");
    vals.push_back("1");
    vals.push_back("2");
    vals.push_back("3");
    vals.push_back("4");
    vals.push_back("5");
    vals.push_back("6");
    vals.push_back("7");

    SetAllowedValues("ADCPort", vals);
}

CITC18ADC::~CITC18ADC()
{
    Shutdown();
}

int CITC18ADC::Initialize()
{

    if (!g_DeviceITC18Available) {
        return ERR_ITC18_HUB_NOT_FOUND;
    }
        // Name
    CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

    // Description
    CreateProperty(MM::g_Keyword_Description, "ITC18 ADC driver", MM::String, true);


    // State
    CPropertyAction* pAct = new CPropertyAction (this, &CITC18ADC::OnVolts);
    int nRet = CreateProperty("Volts", "0.0", MM::Float, true, pAct);
    if (nRet != DEVICE_OK)
        return nRet;

    pAct = new CPropertyAction(this, &CITC18ADC::OnRange);
    CreateProperty("A/D Range", g_range.c_str(), MM::String, false, pAct);
    AddAllowedValue("A/D Range", g_ITC18_AD_RANGE_10VString);
    AddAllowedValue("A/D Range", g_ITC18_AD_RANGE_5VString);
    AddAllowedValue("A/D Range", g_ITC18_AD_RANGE_2VString);
    AddAllowedValue("A/D Range", g_ITC18_AD_RANGE_1VString);

    nRet = UpdateStatus();
    if (nRet != DEVICE_OK)
        return nRet;

    initialized_ = true;

    return DEVICE_OK;

}

void CITC18ADC::GetName(char* name) const
{
    assert(name_.length() < CDeviceUtils::GetMaxStringLength());  
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int CITC18ADC::Shutdown()
{
    initialized_ = false;
    return DEVICE_OK;
}

double CITC18ADC::GetRangeFactor() 
{
    
    double factor = 10.24;

    switch (g_ranges[ADCPort_]) {
        case ITC18_AD_RANGE_1V: 
        {
            factor = 1.024;
            break;
        }
        case ITC18_AD_RANGE_2V:
        {
            factor = 2.048;
            break;
        }
        case ITC18_AD_RANGE_5V:
        {
            factor = 5.120;
            break;
        }
        case ITC18_AD_RANGE_10V:
        {
            factor = 10.24;
            break;
        }
    }
    
    return factor;
}


int CITC18ADC::SetGateOpen(bool open)
{

    return DEVICE_OK;
}

int CITC18ADC::ITC18RunOnce(double &value)
{
    // reset short buffer to send to ITC18
    //short answer;
    
    if (!g_seqThread) 
    {
        fprintf(stderr,"CITC18ADC::ITC18RunOnce: svc() thread doesn't exist!\n");
        return 0;
    }
    
    if (g_seqThread->Busy() == false) 
    {
        g_seqThread->Stop();
        //g_seqThread->wait();
        
        g_seqThread->SetSequence(ONESHOTSEQUENCESIZE,g_OneShotInstructions);
        g_seqThread->SetDataOut(ONESHOTDATASIZE * ONESHOTSEQUENCESIZE,g_OneShotDataOut);
        g_seqThread->SetDataIn(ONESHOTDATASIZE * ONESHOTSEQUENCESIZE,g_OneShotDataIn);
        // must be loaded after sequence is loaded
        g_seqThread->SetInterval(FAST_SAMP_INTERVAL_US);
        // mode 1 = one shot
        g_seqThread->Start(1);
    }
    
    value = 0;
    
    // average all the values, and return the result
    for (int i = 0; i < ONESHOTDATASIZE; i++) {
        value += (g_OneShotDataIn[(i * ONESHOTSEQUENCESIZE) + ADCPort_] * GetRangeFactor() )/32767;
        
    }
    value /= ONESHOTDATASIZE;
    
    return DEVICE_OK;
}

int CITC18ADC::GetSignal(double &volts)
{

    if (gateOpen_) {
        ITC18RunOnce(volts);
        gatedVolts_ = volts;
    } else {
        gatedVolts_ = 0;
    }

    return DEVICE_OK;
}

//////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CITC18ADC::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
//fprintf(stderr,"CITC18DAC::OnVolts()\n");
    if (eAct == MM::BeforeGet)
    {
        double volts;
        GetSignal(volts);
        //fprintf(stderr,"CITC18ADC::OnVolts() volts %2.0g on ADC %d at %d time\n",volts,ADCPort_,g_currentTime);
        if (g_currentTime == 0) pProp->Set(CDeviceUtils::ConvertToString(volts));  
        else pProp->Set(CDeviceUtils::ConvertToString((g_currentAD[ADCPort_] * GetRangeFactor())/32767));
    }
    return DEVICE_OK;
}

int CITC18ADC::OnRange(MM::PropertyBase* pProp, MM::ActionType eAct)
{

    int nRet;

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(range_.c_str());
    }
    else {
        if (eAct == MM::AfterSet) 
        {
            std::string prop;
            pProp->Get(prop);
            if (prop == g_ITC18_AD_RANGE_10VString) {
                range_ = g_ITC18_AD_RANGE_10VString;
                g_ranges[ADCPort_] = ITC18_AD_RANGE_10V;
            }
            if (prop == g_ITC18_AD_RANGE_5VString) {
                range_ = g_ITC18_AD_RANGE_5VString;
                g_ranges[ADCPort_] = ITC18_AD_RANGE_5V;
            }
            if (prop == g_ITC18_AD_RANGE_2VString) { 
                range_ = g_ITC18_AD_RANGE_2VString;
                g_ranges[ADCPort_] = ITC18_AD_RANGE_2V;
            }
            if (prop == g_ITC18_AD_RANGE_1VString) {
                range_ = g_ITC18_AD_RANGE_1VString;
                g_ranges[ADCPort_] = ITC18_AD_RANGE_1V;
            }


            nRet = ITC18_SetRange(itc, g_ranges);
            if (nRet != 0) {
                ITC18_Release(itc);
                ITC18_GetStatusText(itc, nRet, errorMsg, ERROR_MESSAGE_LENGTH);
                return DEVICE_ERR;
            }
        }
    }
	
   return DEVICE_OK;
}

int CITC18ADC::OnADCPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set((long int)ADCPort_);
    }
    else if (eAct == MM::AfterSet)
    {
        long channel;
        pProp->Get(channel);
        if (channel >= 0 && ( (unsigned) channel < maxChannel_) )
        ADCPort_ = channel;
    }
    return DEVICE_OK;
}








///////////////////////////////////////////////////////////////////////////////
// Continuous acquisition
//
void AcqSequenceThread::Start(int mode)
{
    stop_ = false;
    mode_ = mode;
    
    //fprintf(stderr,"AcqSequenceThread::Start() Starting Service Thread\n");
    activate();
}

void AcqSequenceThread::Stop(void)
{
    //fprintf(stderr,"AcqSequenceThread::Stop() Stopping Service Thread\n");
    stop_ = true;
}
/**
 * Continuous acquisition thread service routine.
 * Starts acquisition on the ITC18
 * Maintains the buffer
 */

int AcqSequenceThread::svc(void)
{
    int status;

    dataRead_ = dataWritten_ = writePosition_ = readPosition_ = 0;
    busy_ = true;

    if (mode_ == 2)
    {
        //while (!stop_) {   
            ITC18_SmallRun(itc_, sequenceSize_, sequenceInstructions_, ticks_, externalClock_, dataSizeOut_, dataOut_, dataSizeIn_, dataIn_, externalTrigger_,outputEnabled_);
            #ifdef WIN32
            Sleep(waitTime_);
            #else 
            usleep(waitTime_ * 1000);
            #endif
        //}
    }
    if (mode_ == 1)
        {
            ITC18_SmallRun(itc_, sequenceSize_, sequenceInstructions_, ticks_, externalClock_, dataSizeOut_, dataOut_, dataSizeIn_, dataIn_, externalTrigger_,outputEnabled_);
        }
    if (mode_ == 0) 
    {
        ITC18_SetSequence(itc_, sequenceSize_, sequenceInstructions_);
        status = ITC18_SetSamplingInterval(itc, ticks_, 0);
        if (status != 0) {
            fprintf(stderr,"ITC18_SetSamplingInterval() error %d\n",status);
        }

        ITC18_InitializeAcquisition(itc_);

        status = Write(dataOut_,dataSizeOut_, dataWritten_);
        writePosition_ = dataWritten_;
        if (status != 0) {
            ITC18_GetStatusText(itc_, status, errorMsg, ERROR_MESSAGE_LENGTH);
            fprintf(stderr, "AcqSequenceThread::svc() ITC18_WriteFIFO: %s.\n", errorMsg);
            stop_ = true;
        }
        
        //fprintf(stderr,"AcqSequenceThread::svc() Starting ITC18\n");
        status = ITC18_Start(itc_, 0, 1,0,0);
        if (status != 0) {
            ITC18_GetStatusText(itc_, status, errorMsg, ERROR_MESSAGE_LENGTH);
            fprintf(stderr, "AcqSequenceThread::svc() ITC18_Start %s.\n", errorMsg);
        }

        while (!stop_) {   
            Poll();
    #ifdef WIN32
            Sleep(waitTime_);
    #else 
            usleep(waitTime_ * 1000);
    #endif
        }
    }

    ITC18_StopAndInitialize(itc_,1,1);

    busy_ = false;

    return DEVICE_OK;

}

int AcqSequenceThread::Poll()
{

    if (stop_) // check if we are actually sampling
        return 0;

    int status;

    status = Read(dataIn_+readPosition_, dataSizeIn_-readPosition_, dataRead_);
    if (status != 0) 
    {
        //fprintf(stderr,"AcqSequenceThread::Poll() Status Read Error $d\n",status);
        return status;
    }

    readPosition_ += dataRead_;

    // make sure everything has been read out before we quit!
    if (readPosition_ >= dataSizeIn_)
    {
        stop_ = true;	
        return 0;
    }

    if (writePosition_ < dataSizeOut_)
    {
        status = Write(dataOut_ + writePosition_,  dataSizeOut_- writePosition_, dataWritten_);
        fprintf(stderr,"Poll: Wrote from %d to %d and read %d\n", writePosition_,dataSizeOut_- writePosition_,dataWritten_);

        writePosition_ += dataWritten_;
        if (status != 0) 
        {
            //fprintf(stderr,"AcqSequenceThread::Poll() Status Write Error $d\n",status);
            return status;
        }
    }

	return 0;
}

int AcqSequenceThread::Write(short* data, int limit, int& written)
{
    int space_available;

    int status = ITC18_GetFIFOWriteAvailable(itc_, &space_available);

    space_available -= 1;

    int data_written = 0;

    // Write data if the amount of space available exceeds an
    // arbitrary threshold, or if it would consume the buffer.

    if (space_available >= 64 || space_available >= limit)
    {
        data_written = space_available;

        if (data_written > limit)
            data_written = limit;

        status = ITC18_WriteFIFO(itc_, data_written, data);

        if (status != 0)
            return status;
    }

    written = data_written;

    return 0;
}


int AcqSequenceThread::Read(short* data, int limit, int& read)
{
    int data_available;

    int status = ITC18_GetFIFOReadAvailable(itc_, &data_available);

    if (status != 0)
        return status;

    data_available -= 1;

    int data_read = 0;

    // Read data if the amount of data available exceeds an arbitrary
    // threshold, or if it would fill the buffer.

    int threshold = 64;	// minimum number of samples to retrive

    if (threshold > limit)
        threshold = limit;

    if (data_available >= threshold)
    {
        data_read = data_available;

        if (data_read > limit)
            data_read = limit;

        status = ITC18_ReadFIFO(itc_, data_read, data);

        if (status != 0)
            return status;
    }

    read = data_read;

    return 0;
}

int AcqSequenceThread::SetInterval(float sampling_interval)
{
    ticks_ = int((sampling_interval * 800000.0 / sequenceSize_) + 0.5);

    if (ticks_ > ITC18_MAXIMUM_TICKS) return true;
    if (ticks_ < ITC18_MINIMUM_TICKS) return true;

    return true;

}

#define _WIN32_DCOM
#include <string>
#include <sstream>
#include <iomanip>
#include <iostream>

#include "NikonTICOMInterface.h"
#include "NIkonTIErrorCodes.h"

std::string g_objectivemodellist[] = {"None","Achromat","ApoTIRF","Plan","PlanApo","PlanApoTIRF","PlanApoVC",
                                       "PlanFluor","PlanUW","SFluor","SPlanFLuor","HMC","Other"};
int g_objectivemodellistsize = 13;
std::string g_objectivemaglist[] = {"-","1","1.5","2","2.5","4","5","10","16","20","40","50","60","100","150","200"};
int g_objectivemaglistsize = 15;
std::string g_objectivetypelist[] = {"None","Dry","Oil","MImm","WI"};
int g_objectivetypelistsize = 5;
std::string g_filterblocknameslist[] = {"None","ANALY","UV-2A","UV-2B","DAPI","UV-1A","V-2A","BV-2A","CFPHQ","BV-1A","B-3A",
                                       "GFPHQ","B-2A","B-1A","B-1E","FITC","GFP-L","GFP-B","YFPHQ","G-2A","G-2B",
                                       "TRITC","G-1B","TxRed","Cy3","Cy5","Cy7"};
int g_filterblocknameslistsize = 27;
std::string g_lightpathlist[] = {"None", "Eye100","Left100","Right100","Left80","Right80","Bottom100"};
int g_lightpathlistsize = 7;


TICOMModel::TICOMModel() :
   initialized_ (false)
{
   nosepieceCOM_ = new TICOMNosepiece();
   nosepiece_ = nosepieceCOM_;
   filterBlock1COM_ = new TICOMFilterBlockCassette();
   filterBlock1_ = filterBlock1COM_;
   filterBlock2COM_ = new TICOMFilterBlockCassette();
   filterBlock2_ = filterBlock2COM_;
   zDriveCOM_ = new TICOMZDrive();
   xDriveCOM_ = new TICOMXDrive();
   yDriveCOM_ = new TICOMYDrive();
   zDrive_ = zDriveCOM_;
   xDrive_ = xDriveCOM_;
   yDrive_ = yDriveCOM_;
   pfsOffsetCOM_ = new TICOMPFSOffset();
   pfsOffset_ = pfsOffsetCOM_;
   pfsStatusCOM_ = new TICOMPFSStatus();
   pfsStatus_ = pfsStatusCOM_;
   pEpiShutterCOM_ = new TICOMEpiShutter();
   pEpiShutter_ = pEpiShutterCOM_;
   pDiaShutterCOM_ = new TICOMDiaShutter();
   pDiaShutter_ = pDiaShutterCOM_;
   pAuxShutterCOM_ = new TICOMAuxShutter();
   pAuxShutter_ = pAuxShutterCOM_;
   pLightPathCOM_ = new TICOMLightPath();
   pLightPath_ = pLightPathCOM_;

}

TICOMModel::~TICOMModel()
{
   if (initialized_)
      Shutdown();
   delete nosepiece_;
}

int TICOMModel::Initialize()
{
   // Initialize COM stuff
   ::CoInitializeEx(NULL, COINIT_MULTITHREADED);
   //::CoInitialize (NULL);
   // Get the pointer to the microscope object
   if (pMicroscope_.CreateInstance (TISCOPELib::CLSID_NikonTi)!=S_OK)
   {
      ::CoUninitialize();
      // TODO: proper error reporting
	   return 1;
   }

   // How to get hold of other scopes
   // TODO: get names of other scopes and have these as an initialization property 
   TISCOPELib::INikonTiDevices* scopes = pMicroscope_->GetDevices();
   scopes->Refresh();
   int nrScopes = scopes->GetCount();
   printf ("Found %d Microscope objects\n", nrScopes);
   //for (long i=0; i< nrScopes; i++)

   // Force to use the last (real!) scope
   pMicroscope_->GetDevice()->PutConnected(nrScopes - 1);
   //pMicroscope_->PutDevice(nrScopes -1);
   // Make the first scope our active one (TODO: pre-init property.  use pMicroscope_->SetDevice(devicenum)
   // Set microscope in non-blocking mode, note that this will kill update of Z-drive property!
   pMicroscope_->GetDevice()->Overlapped = false;  

   if (pMicroscope_->get_Nosepiece ( &(nosepieceCOM_->pNosepiece_)) == S_OK)
   {
      nosepieceCOM_->Initialize();
      nosepieceCOM_->pMicroscope_ = pMicroscope_;
   }
   if (pMicroscope_->get_FilterBlockCassette1( &(filterBlock1COM_->pFilterBlock_)) == S_OK) {
      filterBlock1COM_->Initialize();
      filterBlock1COM_->pMicroscope_ = pMicroscope_;
   }
   if (pMicroscope_->get_FilterBlockCassette2( &(filterBlock2COM_->pFilterBlock_)) == S_OK) {
      filterBlock2COM_->Initialize();
      filterBlock2COM_->pMicroscope_ = pMicroscope_;
   }
   if (pMicroscope_->get_ZDrive ( &(zDriveCOM_->pZDrive_)) == S_OK)
   {
      zDriveCOM_->Initialize();
      zDriveCOM_->pMicroscope_ = pMicroscope_;
   }
   if (pMicroscope_->get_XDrive(&(xDriveCOM_->pXDrive_))== S_OK)
   {
	   xDriveCOM_->Initialize();
	   xDriveCOM_->pMicroscope_ = pMicroscope_;
   }
   if (pMicroscope_->get_YDrive(&(yDriveCOM_->pYDrive_))== S_OK)
   {
	   yDriveCOM_->Initialize();
	   yDriveCOM_->pMicroscope_ = pMicroscope_;
   }
   TISCOPELib::IPFS *pPFS = NULL;
   if (pMicroscope_->get_PFS(&pPFS) == S_OK)
   {
      pfsOffsetCOM_->pPFS_ = pPFS;
      pfsStatusCOM_->pPFS_ = pPFS;
	   pfsOffsetCOM_->Initialize();
	   pfsStatusCOM_->Initialize();
	   pfsOffsetCOM_->pMicroscope_ = pMicroscope_;
	   pfsStatusCOM_->pMicroscope_ = pMicroscope_;
   }
   if (pMicroscope_->get_EpiShutter (&(pEpiShutterCOM_->pEpiShutter_)) == S_OK)
   {
	   pEpiShutterCOM_->Initialize();
	   pEpiShutterCOM_->pMicroscope_ = pMicroscope_;
   }
   if (pMicroscope_->get_DiaShutter (&(pDiaShutterCOM_->pDiaShutter_)) == S_OK)
   {
	   pDiaShutterCOM_->Initialize();
	   pDiaShutterCOM_->pMicroscope_ = pMicroscope_;
   }
   if (pMicroscope_->get_AuxShutter (&(pAuxShutterCOM_->pAuxShutter_)) == S_OK)
   {
	   pAuxShutterCOM_->Initialize();
	   pAuxShutterCOM_->pMicroscope_ = pMicroscope_;
   }
   if (pMicroscope_->get_LightPathDrive(&(pLightPathCOM_->pLightPath_)) == S_OK)
   {
      pLightPathCOM_->Initialize();
      pLightPathCOM_->pMicroscope_ = pMicroscope_;
   }
   initialized_ = true;
   return 0;
}

int TICOMModel::Shutdown()
{
   if (initialized_)
   {
      nosepieceCOM_->pNosepiece_->Release();
      filterBlock1COM_->pFilterBlock_->Release();
      filterBlock2COM_->pFilterBlock_->Release();
      pMicroscope_.Release();
      ::CoUninitialize ();
   }
   return 0;
}

/*************************************
 * TICOMDevice, base class for all devices
 */
int TICOMDevice::SetValue(long value)
{
   if (GetTICOMDevice() == NULL)
      return 1;
   GetTICOMDevice()->Value = value;
   return 0;
}

bool TICOMDevice::Busy()
{
      // Wait until the microscope is not busy anymore
   // TODO: Not very elegant.  Have another thread going and query the other thread instead?
   GetMicroscope()->GetDevice()->WaitForDevice(10);
   return false;
}

   // update model with current status of the device in the microscope
int TICOMDevice::UpdateIsMounted()
{
   isMounted_ = false;
   if (GetTICOMDevice() == NULL)
      return 1;
   // TODO: catch errors and report
   if (GetTICOMDevice()->Get_IsMounted() == TISCOPELib::StatusTrue)
      isMounted_ = true;

   return 0;
}

int TICOMDevice::UpdateValue()
{
   if (GetTICOMDevice() == NULL)
      return 1;
   MIPPARAMLib::IMipParameterPtr pValue = GetTICOMDevice()->Value;
   value_ = pValue->RawValue;
   // TODO: add time-out timer here
   // Apparently, the SDK return 0 (when busy?).  Wait in a loop until there is a reasonable value
   // TODO: the emulator returns 0 for the X and Y drives, probably need to have a separate Update Value for drives
   /*
   while (value_ < 1)
      value_ = GetTICOMDevice()->Value;
   */
   return 0;
}

int TICOMDevice::UpdateValueRange()
{
   if (GetTICOMDevice() == NULL)
      return 1;
   MIPPARAMLib::IMipParameterPtr pValue = GetTICOMDevice()->Value;
   lowerLimit_ = pValue->GetRangeLowerLimit();
   upperLimit_ = pValue->GetRangeHigherLimit();
   //unit_ = pValue->GetUnit();
   type_ = pValue->GetDataType();
   name_ = pValue->GetName();
   //resolution_ = GetTICOMDevice()->GetResolution();

   //printf("Unit: %s, Type: %d, Name: %s\n", unit_.c_str(), type_, name_.c_str());

   return 0;
}

HRESULT __stdcall TICOMDevice::OnValueChanged()
{
   if (m_pParam != NULL) {
      //GetTICOMDevice()->Value = m_pParam->RawValue;
      value_ = m_pParam->RawValue;
   }

   return S_OK;
}


/***********************************************
 * TICOMPositionDevice base class implementation
 */
int TICOMPositionDevice::SetPosition(long position)
{
   if (GetTICOMPositionDevice() == NULL)
      return 1;
   GetTICOMPositionDevice()->Position = position;
   return 0;
}

int TICOMPositionDevice::UpdatePosition()
{
   if (GetTICOMPositionDevice() == NULL)
      return 1;
   position_ = GetTICOMPositionDevice()->Position;
   // TODO: add time-out timer here
   // Apparently, the SDK return 0 (when busy?).  Wait in a loop until there is a reasonable value
   if (position_ < 1) {
      long startTime = GetTickCount();
      long timeout(1000);
      long now = startTime;
      while ( (position_ < 1) && ((now - startTime) < timeout) ) {
         position_ = GetTICOMPositionDevice()->Position;
         now = GetTickCount();
      }
   }

   return 0;
}

int TICOMPositionDevice::UpdatePositionRange()
{
   if (GetTICOMPositionDevice() == NULL)
      return 1;

   positionLowerLimit_ = GetTICOMPositionDevice()->GetLowerLimit();
   positionUpperLimit_ = GetTICOMPositionDevice()->GetUpperLimit();
   return 0;
}



/*************************************
 * Device Drive COM implementation
 */
int TICOMDrive::SetPosition(long position)
{
   if (GetTICOMDrive() == NULL)
      return 1;
   try {
      GetTICOMDrive()->Position = position;
   } catch (_com_error err)
   {
      return 1;
   }
   return 0;
}

int TICOMDrive::UpdatePosition()
{
   if (GetTICOMDrive() == NULL)
      return 1;
   try {
      position_ = GetTICOMDrive()->GetPosition();
      MIPPARAMLib::IMipParameterPtr pPosition =GetTICOMDrive()->Position;
      long ps = pPosition->RawValue;
      //std::string posString = pPosition->ConvertRawValueToDisplayString(position_);
      //printf("Positon: %s, position: %d, raw: %d\n", posString.c_str(), position_, ps);
   } catch (_com_error err) {
      return 1;
   }
   return 0;
}

int TICOMDrive::UpdatePositionRange()
{
   if (GetTICOMDrive() == NULL)
      return 1;

   positionLowerLimit_ = GetTICOMDrive()->GetLowerLimit();
   positionUpperLimit_ = GetTICOMDrive()->GetUpperLimit();
   return 0;
}

int TICOMDrive::MoveRelative (long relPosition)
{
   if (GetTICOMDrive() == NULL)
      return 1;
   GetTICOMDrive()->MoveRelative(relPosition);
   return 0;
}

int TICOMDrive::MoveAbsolute (long position)
{
   if (GetTICOMDrive() == NULL)
      return 1;
   GetTICOMDrive()->MoveAbsolute(position);
   return 0;
}

HRESULT __stdcall TICOMDrive::OnValueChanged()
{
   if (m_pParam != NULL) {
      //GetTICOMDrive()->Position = m_pParam->RawValue;
      //value_ = m_pParam->RawValue;
      position_ = m_pParam->RawValue;
   }

   return S_OK;
}


/*************************************
* Device TIPFSOffset COM implementation
*
*/

int TICOMPFSOffset::Initialize()
{
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   UpdateValue();
   UpdateValueRange();
   UpdatePosition();
   UpdatePositionRange();

   return 0;
}

TICOMPFSOffset::~TICOMPFSOffset()
{
}

/*************************************
* Device TIPFS COM implementation
*
*/

int TICOMPFSStatus::Initialize()
{
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   //UpdateValue();
   //UpdateValueRange();
   //UpdatePosition();
   //UpdatePositionRange();

   return 0;
}

bool TICOMPFSStatus::IsEnabled()
{
	_variant_t enb = pPFS_->GetIsEnabled();
	return (bool)enb;
	// I think that what I did above is right, but not sure. 
	// Nikon has this _variant_t object that is kind of a multi-type object
	// I'm using its overloaded operator bool to get the bool out of it. 
}

int TICOMPFSStatus::Enable()
{
	// If already enabled return success
	if (IsEnabled()) 
		return 0;
	
    pPFS_->Enable();
	//TODO: check the return message. 

	// check again to make sure it enabled now
	if (IsEnabled()) 
		return 0;

	return 1;

}

int TICOMPFSStatus::Disable()
{
	// If already enabled return success
	if (!IsEnabled()) 
		return 0;
	
	pPFS_->Disable();
	//TODO: check the return message. 

	// If already enabled return success
	if (!IsEnabled()) 
		return 0;
	
	return 1;
}


/*************************************
 * Device TIZDrive COM implementation
 */
int TICOMZDrive::Initialize()
{
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   UpdateValue();
   UpdateValueRange();
   UpdatePosition();
   UpdatePositionRange();

   //this->Advise(pZDrive_->Position);

   return 0;
}

// TIDrive and TIDevice API
int TICOMZDrive::UpdateSpeed ()
{
   if (pZDrive_ == NULL)
      return 1;
   speed_ = pZDrive_->GetSpeed();
   return 0;
}

int TICOMZDrive::SetSpeed (long speed)
{
   if (pZDrive_ == NULL)
      return 1;
   pZDrive_->Speed = speed;
   return 0;
}

int TICOMZDrive::GetTolerance(long& tolerance)
{
   if (pZDrive_ == NULL)
      return 1;
   tolerance = pZDrive_->Tolerance;
   return 0;
}

int TICOMZDrive::SetTolerance (long tolerance)
{
   if (pZDrive_ == NULL)
      return 1;
   pZDrive_->Tolerance = tolerance;
   return 0;
}

/************************************
* Device EpiShutter
*/

int TICOMEpiShutter::Initialize()
{
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   UpdateValue();
   UpdateValueRange();

   this->Advise(pEpiShutter_->Value);

   return 0;
}

/************************************
* Device DiaShutter
*/

int TICOMDiaShutter::Initialize()
{
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   UpdateValue();
   UpdateValueRange();

   this->Advise(pDiaShutter_->Value);

   return 0;
}

/************************************
* Device DiaShutter
*/

int TICOMAuxShutter::Initialize()
{
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   UpdateValue();
   UpdateValueRange();

   this->Advise(pAuxShutter_->Value);

   return 0;
}

/************************************
* Device Shutter
*/

int TICOMShutterDevice::Open()
{
	long ret;
	if(!IsOpen()) 
	{
		ret = this->GetTICOMShutterDevice()->Open();
		if (S_OK != ret) {return ERR_IN_SHUTTER;}
	} 
	return S_OK;
}

int TICOMShutterDevice::Close()
{
	long ret;
	if(IsOpen()) 
	{
		ret = this->GetTICOMShutterDevice()->Close();
		if (S_OK != ret) {return ERR_IN_SHUTTER;}
	} 
	return S_OK;
}

bool TICOMShutterDevice::IsOpen()
{
	_variant_t t = this->GetTICOMShutterDevice()->GetIsOpened();
	return (bool)t;
}


/*************************************
 * Device Nosepiece COM implementation
 */
int TICOMNosepiece::Initialize()
{   
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   UpdateValue();
   UpdateValueRange();
   UpdatePosition();
   UpdatePositionRange();
   UpdateObjectives();

   this->Advise(pNosepiece_->Value);

   return 0;
}

int TICOMNosepiece::UpdateObjectives()
{
   if (pNosepiece_ == NULL)
      return 1;
 
   TISCOPELib::IObjectivesPtr objectives = pNosepiece_->GetObjectives();

   labels_.clear();
   for (long i = PositionLowerLimit();  i<= PositionUpperLimit(); i++) {
      TICOMObjective* objective = new TICOMObjective();
      int ret = objective->UpdateData(objectives->GetItem(i));
      if (ret != 0)
         return ret;
      objectives_.push_back(objective);
      std::ostringstream os;
      if (objective->IsMounted())
      {
         const std::streamsize wid(3);
         os << objective->GetModel() << " " << std::setw(wid) << objective->GetMagnification() << "x na " ;
         os << std::fixed << std::setprecision(2) << objective->GetNA();
      }
      else // generate empty but unique labels
         for (long j = PositionLowerLimit(); j <= i; j++)
            os << " ";
      labels_.push_back(os.str().c_str());
   }
  
   return 0;
}

std::vector<TIObjective*> TICOMNosepiece::GetObjectives()
{
   std::vector<TIObjective*> objectiveList_;
   typedef std::vector<TICOMObjective*>::iterator VI;
   for (VI i =objectives_.begin(); i < objectives_.end(); i++)
      objectiveList_.push_back((TIObjective*) *i);
   return objectiveList_;
}


//////////////////////////////////
// TICOMObjective
TICOMObjective::TICOMObjective()
{
}

int TICOMObjective::UpdateData(TISCOPELib::IObjectivePtr objective)
{
   try {
      description_ = objective->GetDescription();

      if (objective->GetMagnification() < 0 || objective->GetMagnification() > g_objectivemaglistsize)
         magnification_ = g_objectivemaglist[0];
      else
         magnification_ = g_objectivemaglist[objective->GetMagnification()];

      if ((int)objective->GetObjectiveModel() < 0 || (int)objective->GetObjectiveModel() > g_objectivemodellistsize)
         model_ = g_objectivemodellist[0];
      else
         model_ = g_objectivemodellist[objective->GetObjectiveModel()];

      if ((int)objective->GetObjectiveType() < 0 || (int)objective->GetObjectiveType() > g_objectivetypelistsize)
         type_ = g_objectivetypelist[0];
      else
         type_ = g_objectivetypelist[objective->GetObjectiveType()];

      usePFS_ = objective->GetIsPFSEnabled();
      typeWD_ = objective->GetWDType();
      objective->get_NumericalAperture(&na_);
      isMounted_ = true;
   } catch(_com_error err) {
      // TODO: find better way to test if there is an objective
      isMounted_ = false;
		if (err.Error ()>(HRESULT)TISCOPELib::TISCOPE_DEVICE_ERROR_BASE)
			if (err.Error ()<(HRESULT)TISCOPELib::TISCOPE_DATABASE_ERROR_BASE)

      // TODO: better error reporting
		printf("Microscope Control Error\n");
		if (err.Error ()>(HRESULT)TISCOPELib::TISCOPE_DEVICE_ERROR_BASE)
			if (err.Error ()<(HRESULT)TISCOPELib::TISCOPE_DATABASE_ERROR_BASE)
				printf("Device Communications Error\n");
		printf("\tError Message =%s\n",err.ErrorMessage());
		printf("\tCOM Exception Code =%08lx\n",err.Error());
		printf("\tError Source =%s\n",(LPCSTR)err.Source ());
		printf("\tError Description =%s\n",(LPCSTR)err.Description ());
   }

   return 0;
}

////////////////////////////////////
// FilterBlock

int TICOMFilterBlock::UpdateData(TISCOPELib::IFilterBlockPtr pFilterBlock)
{
   try {
      //if (pFilterBlock->GetName() < 0 || pFilterBlock->GetName() > g_filterblocknameslistsize)
        // name = g_filterblocknameslist[0];
      //else 
       //  name_ = g_filterblocknameslist[pFilterBlock->GetName()];
      name_ = pFilterBlock->GetName();
   } catch (_com_error err){
      				if (err.Error ()>(HRESULT)TISCOPELib::TISCOPE_DEVICE_ERROR_BASE)
					if (err.Error ()<(HRESULT)TISCOPELib::TISCOPE_DATABASE_ERROR_BASE)

				printf("Microscope Control Error\n");
				if (err.Error ()>(HRESULT)TISCOPELib::TISCOPE_DEVICE_ERROR_BASE)
					if (err.Error ()<(HRESULT)TISCOPELib::TISCOPE_DATABASE_ERROR_BASE)
						printf("Device Communications Error\n");
				printf("\tError Message =%s\n",err.ErrorMessage());
				printf("\tCOM Exception Code =%08lx\n",err.Error());
				printf("\tError Source =%s\n",(LPCSTR)err.Source ());
				printf("\tError Description =%s\n",(LPCSTR)err.Description ());
   }
   return 0;
}

/*************************************
 * Device FliterBlockCassette COM implementation
 */

int TICOMFilterBlockCassette::Initialize()
{   
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   //UpdateValue();
   UpdateValueRange();
   UpdatePosition();
   UpdatePositionRange();
   UpdateFilterBlocks();

   //this->Advise(pFilterBlock_->Value);

   return 0;
}


int TICOMFilterBlockCassette::UpdateFilterBlocks()
{
   if (pFilterBlock_ == NULL)
      return 1;
 
   TISCOPELib::IFilterBlocksPtr filterBlocks = pFilterBlock_->GetFilterBlocks();

   labels_.clear();
   for (long i = PositionLowerLimit();  i<= PositionUpperLimit(); i++) {
      TICOMFilterBlock* filterBlock = new TICOMFilterBlock();
      int ret = filterBlock->UpdateData(filterBlocks->GetItem(i));
      if (ret != 0)
         return ret;
      filterBlocks_.push_back(filterBlock);
      // Make sure the label is unique
      std::string label = filterBlock->GetName();
      std::vector<std::string>::iterator itVectorData;
      for(itVectorData = labels_.begin(); itVectorData != labels_.end(); itVectorData++) {
         if (label == *(itVectorData)) {
            std::ostringstream os;
            os << "-" << i;
            label += os.str();
         }
      }
      labels_.push_back(label);
   }

   return 0;
}


/*************************************
 * Device LightPath COM implementation
 */

int TICOMLightPath::Initialize()
{   
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   UpdateValue();
   UpdateValueRange();
   UpdatePosition();
   UpdatePositionRange();
   UpdateLightPaths();

   //this->Advise(pFilterBlock_->Value);

   return 0;
}


int TICOMLightPath::UpdateLightPaths()
{
   if (pLightPath_ == NULL)
      return 1;
 
   labels_.clear();
   for (long i = PositionLowerLimit();  i<= PositionUpperLimit(); i++) {
      if (i < g_lightpathlistsize)
         labels_.push_back(g_lightpathlist[i]);
   }

   return 0;
}


/*************************************
 * Device TIXDrive COM implementation
 */
int TICOMXDrive::Initialize()
{
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   UpdateValue();
   UpdateValueRange();
   UpdatePosition();
   UpdatePositionRange();

   return 0;
}

 // TIDrive and TIDevice API
int TICOMXDrive::UpdateSpeed ()
{
   if (pXDrive_ == NULL)
      return 1;
   speed_ = pXDrive_->GetSpeed();
   return 0;
}

int TICOMXDrive::SetSpeed (long speed)
{
   if (pXDrive_ == NULL)
      return 1;
   pXDrive_->Speed = speed;
   return 0;
}


int TICOMXDrive::GetTolerance(long& tolerance)
{
   if (pXDrive_ == NULL)
      return 1;
   tolerance = pXDrive_->Tolerance;
   return 0;
}

int TICOMXDrive::SetTolerance (long tolerance)
{
   if (pXDrive_ == NULL)
      return 1;
   pXDrive_->Tolerance = tolerance;
   return 0;
}


/*************************************
 * Device TIYDrive COM implementation
 */
int TICOMYDrive::Initialize()
{
   UpdateIsMounted();
   if (!GetIsMounted())
      return ERR_MODULE_NOT_FOUND;
   UpdateValue();
   UpdateValueRange();
   UpdatePosition();
   UpdatePositionRange();

   return 0;
}

 // TIDrive and TIDevice API
int TICOMYDrive::UpdateSpeed ()
{
   if (pYDrive_ == NULL)
      return 1;
   speed_ = pYDrive_->GetSpeed();
   return 0;
}

int TICOMYDrive::SetSpeed (long speed)
{
   if (pYDrive_ == NULL)
      return 1;
   pYDrive_->Speed = speed;
   return 0;
}

int TICOMYDrive::GetTolerance(long& tolerance)
{
   if (pYDrive_ == NULL)
      return 1;
   tolerance = pYDrive_->Tolerance;
   return 0;
}

int TICOMYDrive::SetTolerance (long tolerance)
{
   if (pYDrive_ == NULL)
      return 1;
   pYDrive_->Tolerance = tolerance;
   return 0;
}
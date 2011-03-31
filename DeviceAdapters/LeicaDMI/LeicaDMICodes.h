// Leica Scope Families
// These need slightly different commands here and there
const int g_DMI456 = 0;
const int g_CTRMIC = 1;

// List of Device numbers (from Leica documentation)
const int g_Mirror_House = 55; // motorized with 2 positions
const int g_MA4_Mirror = 56; // flapping mirror for MA4
const int g_TL_Arm = 58; // general settings
const int g_Side_Port = 59; // Fixed prism or motorized prism with 4 positions
const int g_Bottom_Port = 60; // motorized with 2 positions
const int g_Flapping_Filter_1 = 61;
const int g_Flapping_Filter_2 = 62;
const int g_Dark_Flap_Tl = 63;
const int g_Constant_Color_Intensity_Control_Il = 64; // 12 bits
const int g_Master = 70; // DMI
const int g_ZDrive = 71; // motorized
const int g_XDrive = 72; // motorized
const int g_YDrive = 73; // motorized
const int g_Microscope_Buttons = 74;
const int g_Microscope_Display = 75;
const int g_Revolver = 76; //motorized, encoded 6 positions
const int g_Lamp = 77; // 12 bits
const int g_IL_Turret = 78; // motorized, 6 positions
const int g_Mag_Changer_Mot = 79; // motorized magnifictaion changer with 4 positions
const int g_Tube = 80; // fixed or non-coded switching tube
const int g_Flapping_Condenser = 81; // motorized with 7 positions
const int g_Condensor = 82; // motorized with 7 positions
const int g_Field_Diaphragm_TL = 83; // motorized iris diaphragm
const int g_Aperture_Diaphragm_TL = 84; // mptorized iris diaphragm in the condenser
const int g_DIC_Turret = 85; // motorized DIC disk with 4 positions
const int g_Z_Handwheel = 86; 
const int g_Field_Diaphragm_IL = 94; // motorized with maximum 12 positions
const int g_Aperture_Diaphragm_IL = 95; // motorized diaphragm disk with maximum 12 positions
const int g_Constant_Color_Intensity_Control_TL = 97; // 12 bits
const int g_TL_Polarizer = 98; // motorizedin the condenser
const int g_AFC = 48; // adaptive focus control / autofocus


//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define COM_ERR         999
#define ID_ERR          998
#define FUI_ERR         997
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004                                   
#define ERR_SET_POSITION_FAILED      10005                                   
#define ERR_INVALID_STEP_SIZE        10006                                   
#define ERR_INVALID_MODE             10008 
#define ERR_CANNOT_CHANGE_PROPERTY   10009 
#define ERR_UNEXPECTED_ANSWER        10010 
#define ERR_INVALID_TURRET           10011 
#define ERR_SCOPE_NOT_ACTIVE         10012 
#define ERR_INVALID_TURRET_POSITION  10013
#define ERR_MODULE_NOT_FOUND         10014
#define ERR_ANSWER_TIMEOUT           10015
#define ERR_PORT_NOT_OPEN            10016
#define ERR_TURRET_NOT_ENGAGED       10017
#define ERR_MAGNIFIER_NOT_ENGAGED    10018

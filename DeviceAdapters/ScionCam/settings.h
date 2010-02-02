//////////////////////////////////////////////////////////////////////////////////////
//
//
//	sfwcore	- Scion Firewire Core Library
//
//	Version	1.0
//
//	Copyright 2008-2009  Scion Corporation  	(Win 2000/XP/Vista 32/64 Platforms)
//
//
//////////////////////////////////////////////////////////////////////////////////////

//////////////////////////////////////////////////////////////////////////////////////
//
//
//	File	settings.h
//
//	definitions for configuration preference classes
//
//
//////////////////////////////////////////////////////////////////////////////////////


#if !defined(SETTINGS_H__INCLUDED_)
#define SETTINGS_H__INCLUDED_

#include "sfwlib.h"

#ifndef	DLLExport
#ifdef	_DLL
#define	DLLExport	__declspec (dllexport)
#else
#define	DLLExport	__declspec (dllimport)
#endif
#endif


// definitions

#define NO_CONFIGS			10
#define CONFIG_NAME_SIZE	(32+1)

//----------------------------------------------------------------------------
//
//	Ccam_settings	- camera settings class
//
//----------------------------------------------------------------------------
class DLLExport Ccam_settings
{
public:
	Ccam_settings();

public:
	virtual	~Ccam_settings();

public:
	// defaults
	#ifdef	QC_VERSION
	#define	DEFAULT_COLOR_GAIN		0x194		// rgb default
	#define	DEFAULT_GAIN			0x18f		// grayscale default
	#define	DEFAULT_EXPOSURE		0x2a		// exposure default
	#else
	#define	DEFAULT_COLOR_GAIN		0x194		// was 0x18f
	#define	DEFAULT_GAIN			0x119		// grayscale default
	#define	DEFAULT_EXPOSURE		0x46		// was 0x24
	#endif


	#define	DEFAULT_BL				0x27
	#define	DEFAULT_CONTRAST_MODE	0
	#define	DEFAULT_CONTRAST_VALUE	127
	#define	DEFAULT_FR_SEL			0
	#define	DEFAULT_BD_SEL			0
	#define	DEFAULT_RED_GAIN		0x1a
	#define	DEFAULT_GREEN_GAIN		0x1f
	#define	DEFAULT_BLUE_GAIN		0x23
	#define	DEFAULT_RED_BOOST		1
	#define	DEFAULT_BLUE_BOOST		1
	#define	DEFAULT_AVG_OPT			1
	#define	DEFAULT_AVG_FRAMES		2
	#define	DEFAULT_TEST_MODE		0
	#define	DEFAULT_PREVIEW_MODE	0
	#define	DEFAULT_BIN_MODE		0
	#define	DEFAULT_COLOR_GAMMA		1
	#define	DEFAULT_GAMMA			0
	#define	DEFAULT_GAMMA_VALUE		18


private:
	// camera settings

	char				config_name[CONFIG_NAME_SIZE];	
											// configuration name

	unsigned int		gain;				// gain
	double				gain_value;			// gain value
	unsigned int		bl;					// black level
	double				bl_value;			// black level value

	unsigned int		contrast_mode;		// contrast mode
	unsigned int		contrast;			// contrast value

	unsigned int		red_gain;			// wb red adjustment
	double				red_gain_value;		// red gain value
	unsigned int		green_gain;			// wb green adjustment
	double				green_gain_value;	// green gain value
	unsigned int		blue_gain;			// wb blue adjustment
	double				blue_gain_value;	// blue gain value

	unsigned int		red_boost;			// red boost
	unsigned int		blue_boost;			// blue boost

	unsigned int		gamma;				// gamma
	unsigned int		gamma_value;		// gamma value index
	double				gamma_dvalue;		// gamma value

	unsigned int		exposure;			// exposure
	unsigned int		exposure_value;		// exposure value in ms

	unsigned int		rate;				// readout speed
	unsigned int		i_rate;				// readout speed index
	unsigned int		live_rate;			// live readout speed
	unsigned int		live_i_rate;		// live readout speed index
	unsigned int		bit_depth;			// bit depth (0=8,1=10,2=12)

	unsigned int		average_opt;		// average option
	unsigned int		average_frames;		// no of frames to average

	unsigned int		test_mode;			// test mode
	unsigned int		preview;			// preview mode
	unsigned int		bin;				// bin mode

	unsigned int		invert;				// invert image

	unsigned int		format;				// image format
											// based on depth & camera

	// format options
	unsigned int		image_type;			// 0 = gray, 1 = color
	unsigned int		order;				// 0 = rgb, 1 = bgr for color image
	unsigned int		no_components;		// number of components for color image

	// native info
	unsigned int		native_format;		// native format code
	unsigned int		native_image_type;	// native image type
	unsigned int		native_order;		// 0 = rgb, 1 = bgr
	unsigned int		native_components;	// native components

// save pool for format information
private:
	bool				saved_exists;		// true if saved settings exist
	unsigned int		saved_image_type;
	unsigned int		saved_order;
	unsigned int		saved_no_components;
	unsigned int		saved_bit_depth;

// setting constraints for specific camera
protected:
	SFW_CAMERA_HANDLE	pfw;				// fw camera handle
	unsigned int		caps;				// capabilities
	unsigned int		max_gain;
	double				max_gain_value;
	unsigned int		max_bl;
	unsigned int		max_exposure;
	unsigned int		max_red_gain;
	unsigned int		max_green_gain;
	unsigned int		max_blue_gain;
	double				max_gamma_dvalue;
	unsigned int		max_gamma_value;
	unsigned int		max_contrast_value;
	unsigned int		min_gain;
	double				min_gain_value;
	unsigned int		min_bl;
	unsigned int		min_exposure;
	unsigned int		min_red_gain;
	unsigned int		min_green_gain;
	unsigned int		min_blue_gain;
	double				min_gamma_dvalue;
	unsigned int		min_gamma_value;
	unsigned int		min_contrast_value;

// accessors (proctect private nature of these parameters)
public:
	virtual	const char * const	get_name()			const {return config_name;}

	virtual	unsigned int	get_gain()				const {return gain;}
	virtual	double			get_gain_value()		const {return gain_value;}
	virtual	double			get_max_gain_value()	const {return max_gain_value;}
	virtual	double			get_min_gain_value()	const {return min_gain_value;}

	virtual	unsigned int	get_red_gain()			const {return red_gain;}
	virtual	double			get_red_gain_value()	const {return red_gain_value;}
	virtual	unsigned int	get_green_gain()		const {return green_gain;}
	virtual	double			get_green_gain_value()	const {return green_gain_value;}
	virtual	unsigned int	get_blue_gain()			const {return blue_gain;}
	virtual	double			get_blue_gain_value()	const {return blue_gain_value;}

	virtual	unsigned int	get_exposure()			const {return exposure;}
	virtual	unsigned int	get_expsoure_value()	const {return exposure_value;}

	virtual	unsigned int	get_contrast_mode()		const {return contrast_mode;}
	virtual	unsigned int	get_contrast()			const {return contrast;}
	virtual	int				get_contrast_value()	
										const {return contrast - 127;}

	virtual	unsigned int	get_gamma_mode()	const {return gamma;}
	virtual	double			get_gamma()			const {return gamma_dvalue;}
	virtual	unsigned int	get_gamma_index()	const {return gamma_value;}

	virtual	unsigned int	get_red_boost()		const {return red_boost;}			
	virtual	unsigned int	get_blue_boost()	const {return blue_boost;}

	virtual	unsigned int	get_bl()			const {return bl;}
	virtual	double			get_bl_value()		const {return bl_value;}

	virtual	unsigned int	get_depth_select()	const {return bit_depth;}

	virtual	unsigned int	get_average_opt()	const {return average_opt;}
	virtual	unsigned int	get_average_frames()const {return average_frames;}
	virtual	unsigned int	get_preview_mode()	const {return preview;}
	virtual	unsigned int	get_bin_mode()		const {return bin;}
	virtual	unsigned int	get_test_mode()		const {return test_mode;}

	virtual	unsigned int	get_rate()			const {return rate;}
	virtual	unsigned int	get_rate_index()	const {return i_rate;}
	virtual	unsigned int	get_live_rate()		const {return live_rate;}
	virtual	unsigned int	get_live_rate_index() 
										const {return live_i_rate;}

	virtual	unsigned int	get_image_type()	const {return image_type;}
	virtual	unsigned int	get_order()			const {return order;}
	virtual	unsigned int	get_no_components()	const {return no_components;}

	virtual	bool			invert_image()		const
		{
		if(invert == 1)			{return true;}
		else					{return false;}
		}


public:
	// method to initialize settings for specific camera
	virtual	int			init(SFW_CAMERA_HANDLE fwp);

	// method to load camera settings with default values for specified camera
	virtual	int			load_defaults(SFW_CAMERA_HANDLE fwp);

public:
	virtual	unsigned int	get_format();	

	virtual	bool			preview_mode_allowed();
	virtual	bool			bin_mode_allowed();
	virtual	bool			test_mode_allowed();

	// method to set parameters to native format (bit depth, order, image type)
	virtual	bool			set_native(unsigned int image_type);

	// method to save current format related settings
	virtual	bool			save_format();
	
	// method to restore format settings
	virtual	bool			restore_format();

	// method to set parameters based on specified format
	virtual	bool			set_format(unsigned int format);


	// method to test if configuration is set to capture an image with
	// component size > 8 bits
	virtual	bool			deep_image() const;

	// method to get depth of component in bits
	virtual	unsigned int	get_component_depth() const;
	virtual	bool			set_component_depth(unsigned int value);

	virtual	unsigned int	get_component_size() const;			// get component size in bytes;
	virtual	unsigned int	get_pixel_size() const;				// get pixel size in bytes


	// routines to set/update settings
	virtual	bool		set_config_name(unsigned char *name);	// config name
	virtual	bool		set_gain(unsigned int value);			// gain setting
	virtual	bool		set_gain_value(double value);			// gain value
	virtual	bool		set_bl(unsigned int value);				// black level setting
	virtual	bool		set_bl_value(double value);				// black level value
	virtual	bool		set_contrast_mode(unsigned int value);	// contrast mode
	virtual	bool		set_contrast(unsigned int value);		// contrast index
	virtual	bool		set_contrast_value(int value);			// contrast value
	virtual	bool		set_gamma_mode(unsigned value);			// gamma mode
	virtual	bool		set_gamma(double value);				// gamma value
	virtual	bool		set_gamma_index(unsigned int value);	// gamma setting

	virtual	bool		set_red_gain(unsigned int value);		// red gain index
	virtual	bool		set_blue_gain(unsigned int value);		// blue gain index
	virtual	bool		set_green_gain(unsigned int value);		// green gain index

	virtual	bool		set_red_gain_value(double value);		// red gain value
	virtual bool		set_blue_gain_value(double value);		// blue gain value
	virtual bool		set_green_gain_value(double value);		// green gain value

	virtual bool		set_red_boost(unsigned int value);		// red boost
	virtual bool		set_blue_boost(unsigned int value);		// blue boost

	virtual bool		set_exposure(unsigned int value);		// exposure index
	virtual bool		set_exposure_value(unsigned int value);	// exposure value (ms)
	virtual bool		set_rate(unsigned int value);			// capture readout speed
	virtual bool		set_live_rate(unsigned int value);		// live readout speed

	virtual bool		set_depth_select(unsigned int value);	// bit dept selection

	virtual bool		set_average_opt(unsigned int value);	// average option
	virtual bool		set_average_frames(unsigned int value);	// number of frames to average

	virtual bool		set_test_mode(unsigned int value);		// test mode
	virtual bool		set_preview_mode(unsigned int value);	// preview mode
	virtual bool		set_bin_mode(unsigned int value);		// bin mode

	virtual bool		set_invert_image(unsigned int value);	// invert image during capture if true
};


//----------------------------------------------------------------------------
//
//	Cconfig_collection	- configuration collection class
//
//----------------------------------------------------------------------------
class Cconfig_collection
{
public:
	Cconfig_collection();

public:
	virtual	~Cconfig_collection();


public:
	// camera settings

	Ccam_settings		*config;				// -> current settings
	Ccam_settings		config_tab[NO_CONFIGS];	// camera control settings

private:
	unsigned int		no_of_configs;			// no of configurations

public:
	virtual unsigned int		get_no_configs();
	virtual Ccam_settings *		get_config(unsigned int index);

	virtual bool				set_config(const Ccam_settings &config, unsigned int index);
	virtual bool				copy_config(Ccam_settings &config, unsigned int index);
};


#endif // !defined(SETTINGS_H__INCLUDED_)

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
//	File	capture.h
//
//	definitions for image capture classes
//
//
//////////////////////////////////////////////////////////////////////////////////////

#if !defined(CAPTURE_H__INCLUDED_)
#define CAPTURE_H__INCLUDED_

#include "sfwlib.h"
#include "settings.h"
#include "buffers.h"
#include "device.h"
#include "thread.h"

#include "imageinfo.h"

#ifndef	DLLExport
#ifdef	_DLL
#define	DLLExport	__declspec (dllexport)
#else
#define	DLLExport	__declspec (dllimport)
#endif
#endif

class DLLExport Cimage_binfo
{
public:
	Cimage_binfo();
	virtual ~Cimage_binfo();

public:		
	Cimage_info			ii;					// image information

	unsigned int		buffer_no;			// buffer number
	unsigned char		*bp;				// buffer pointer
	unsigned int		size;				// buffer size
	Cfw_image			*pos;				// buffer image descriptor
	Cimage				*phd;				// high definition image descriptor
};


//
// capture control class - manages capture settings and provides core frame capture functionality
//
//

class DLLExport Ccapture
{
public:
	Ccapture();
	Ccapture(Cdevice *camera);

	virtual ~Ccapture();

protected:
	Cdevice				*dp;				// -> camera device

	unsigned int		no_frames;			// count of frames (since last count reset)

	unsigned int		retries;			// number of retries for a frame
	unsigned int		retry_count;		// current try

	int					double_buffer;		// double buffer flag				
	int					buffer_select;		// current buffer selection	for capture
	unsigned int		buffer_no;			// current buffer (current image)
	unsigned int		init_buffer_no;		// initial buffer no for stream start

	Cfw_image			*pos;				// current buffer image descriptor
	Cimage				hd_image;			// high def image

	Cframe_info			frame_info;			// frame descriptor for general use

	unsigned int		image_present;		// 0 = no image yet, 1 = image
	unsigned int		deep_image_present;	// deep image present

	unsigned int		image_mode;			// 0 = gray, 1 = 24-bit color
	unsigned int		format;				// current capture format
	Ciformat			format_info;		// format info

	unsigned int		max_width;			// max width 						
	unsigned int		max_height;			// max height
	unsigned int		preview_width;		// preview width
	unsigned int		preview_height;		// preview height
	unsigned int		bin_width;			// bin image width
	unsigned int		bin_height;			// bin image height

	unsigned int		width;				// image width for capture			
	unsigned int		height;				// image height for capture	
	unsigned int		rowbytes;			// row bytes						
	unsigned int		crowbytes;			// color image row bytes			

	unsigned int		multi_frame;		// 0 = no, 1 = multi-frame capture	
	unsigned int		average_opt;		// average option					
	unsigned int		average_frames;		// no of frames to average							

	Ccam_settings		config;				// configuration for current capture

//	Cframe_info			frame;				// frame for for single op

private:	
	unsigned int		restart_stream;		// restart stream if true

public:
	virtual int			init(Cdevice *device, Ccam_settings *sp);

// access routines
public:
	virtual	Cdevice			*get_device() const;
	virtual	Ccam_settings	*get_settings();

	virtual	bool			image_available() const;
	virtual	bool			deep_image_available() const;

	// get capture dimension info based on current setup
	virtual	unsigned int	get_image_mode() const;
	virtual	unsigned int	get_bytes_per_row()	const;
	virtual	unsigned int	get_image_size() const;
	virtual	unsigned int	get_width() const;
	virtual	unsigned int	get_height() const;

	// get captured image information
	virtual	unsigned int	get_buffer_no() const;
	virtual	Cfw_image		*get_image() const;
	virtual	Cfw_image		*get_image(unsigned int buffer_no);
	virtual	Cimage			*get_deep_image();

	virtual	void 			get_image_info(Cimage_binfo *bip);

	// statistics
	virtual	void			reset_frame_count();
	virtual	unsigned int	get_frame_count() const;


// capture routines
public:	
	// capture setup
	virtual	int			setup(int multi_opt, int live_opt);
	virtual	int			setup(Ccam_settings *config, int multi_opt, int live_opt);

	// setup modification routines
	virtual	void		select_live_rate();			// switch to live capture rate
	virtual	void		select_capture_rate();		// switch to normal capture rate


	// capture routines

	// set max number of frame retries;
	virtual	void		set_no_retries(unsigned int count);

	// capture - using config depth
	virtual	int			get_frame();
	virtual	int			get_frame(Cthread_state *thread_state);
	virtual	int			get_frame(Cfw_image *image);
	virtual	int			get_frame(Cfw_image *image, Cthread_state *thread_state);

//	virtual	int			get_frame(Cimage *image);
//	virtual	int			get_frame(Cimage *image, Cthread_state *thread_state);

protected:
	// capture 8-bit
	virtual	int			get_frame8();
	virtual	int			get_frame8(Cthread_state *thread_state);
	virtual	int			get_frame8(Cfw_image *image);
	virtual	int			get_frame8(Cfw_image *image, Cthread_state *thread_state);
//	virtual	int			get_frame8(Cimage *image);
//	virtual	int			get_frame8(Cimage *image, Cthread_state *thread_state);

	// capture any depth
	virtual	int			get_deep_frame(unsigned int depth);
	virtual	int			get_deep_frame(unsigned int depth, Cthread_state *thread_state);

	virtual	int			get_deep_frame(Cfw_image *hd_image, unsigned int depth);
	virtual	int			get_deep_frame(Cfw_image *hd_image, unsigned int depth, Cthread_state *thread_state);

//	virtual	int			deep_to_displayable(unsigned int format);

public:
	// capture - buffered (any depth - uses buffer pool)
	virtual	int			start_frame();
	virtual	int			start_frame(Cthread_state *thread_state);
	virtual	int			start_frame(unsigned int start_buffer_no);
	virtual	int			start_frame(unsigned int start_buffer_no, Cthread_state *thread_state);

	virtual	int			abort_frame();

	virtual	int			complete_frame();
	virtual	int			complete_frame(Cthread_state *thread_state);
	virtual	int			complete_framex(Cthread_state *thread_state);

	virtual	int			current_frame();
	virtual	int			current_frame(Cthread_state *thread_state);
	virtual	int			current_framex(Cthread_state *thread_state);

	virtual	int			stop_frame();
	virtual	int			stop_frame(Cthread_state *thread_state);
	virtual	int			stop_framex(Cthread_state *thread_state);

public:
	// processed frame capture (multi-frame)
//	virtual	int			process_multi_frame();
//	virtual	int			process_multi_frame(Cthread_state *thread_state);

protected:
	// processed capture 8-bit
	virtual	int			process_multi_frame8();
	virtual	int			process_multi_frame8(Cthread_state *thread_state);

	// processed capture any depth
	virtual	int			process_multi_deep(unsigned int depth);
	virtual	int			process_multi_deep(unsigned int depth, Cthread_state *thread_state);

public:
//	virtual	int			clear_buffer();
//	virtual	int			clear_buffer(unsigned int buffer_no);

	virtual	int			get_raw_frame();
	virtual	int			get_raw_frame(Cthread_state *tsp);

protected:
	// accumulate 8-bit (multi-buffered)
	virtual	int			accumulate(unsigned char *bp, unsigned int buf_size);
	virtual	int			accumulate(unsigned char *bp, unsigned int buf_size, Cthread_state *tsp);

	// accumulate any depth
	virtual	int			accumulate_deep(unsigned char *bp, unsigned int buf_size,
							unsigned int depth);
	virtual	int			accumulate_deep(unsigned char *bp, unsigned int buf_size, 
							unsigned int depth, Cthread_state *tsp);

	// get frame for processed 8-bit (multi-buffered)
	virtual	int			get_multi_frame();
	virtual	int			get_multi_frame(Cthread_state *tsp);

	// get frame for processed any depth
	virtual	int			get_multi_deep();
	virtual	int			get_multi_deep(Cthread_state *tsp);


// auto processing routines (these routines capture an image with the computed values)
public:
	virtual	unsigned int	wb_process(RECT roi, unsigned int &gain,
								unsigned int &red_gain, unsigned int &green_gain,
								unsigned int &blue_gain);
	virtual	unsigned int	wb_process(RECT roi, unsigned int &gain,
								unsigned int &red_gain, unsigned int &green_gain,
								unsigned int &blue_gain, Cthread_state *tsp);

	virtual	unsigned int	agp_process(unsigned int &gain);
	virtual	unsigned int	agp_process(unsigned int &gain, Cthread_state *tsp);

	virtual	unsigned int	aep_process(unsigned int &exposure, unsigned int &gain);
	virtual	unsigned int	aep_process(unsigned int &exposure, unsigned int &gain, 
								Cthread_state *tsp);


// device auto callbacks
protected:
	static BOOL CALLBACK	wb_callback(SFW_CAMERA_HANDLE ip, UINT_PTR user_data);
	static BOOL CALLBACK	agp_callback(SFW_CAMERA_HANDLE ip, UINT_PTR user_data);
	static BOOL CALLBACK	aep_callback(SFW_CAMERA_HANDLE ip, UINT_PTR user_data);


// on the fly parameter adjustments
// these parameters do not effect camera control in any way
public:
	virtual	bool		set_test_mode(unsigned int value)
							{
							if(config.set_test_mode(value))
							{dp->set_param(SFW_TEST_PATTERN, value); return(true);}
							else	{return false;}
							}

	virtual	bool		set_gain(unsigned int value)
							{
							if(config.set_gain(value))
							{dp->set_param(SFW_GAIN, value); return(true);}
							else	{return false;}
							}

	virtual bool		set_bl(unsigned int value)
							{
							if(config.set_bl(value))
							{dp->set_param(SFW_BLACK_LEVEL, value); return(true);}
							else	{return false;}
							}

	virtual	bool		set_red_gain(unsigned int value)
							{
							if(config.set_red_gain(value))
							{dp->set_param(SFW_RED_GAIN, value); return(true);}
							else	{return false;}
							}

	virtual	bool		set_green_gain(unsigned int value)
							{
							if(config.set_green_gain(value))
							{dp->set_param(SFW_GREEN_GAIN, value); return(true);}
							else	{return false;}
							}

	virtual	bool		set_blue_gain(unsigned int value)
							{
							if(config.set_blue_gain(value))
							{dp->set_param(SFW_BLUE_GAIN, value); return(true);}
							else	{return false;}
							}

	virtual	bool		set_red_boost(unsigned int value)
							{
							if(config.set_red_boost(value))
							{dp->set_param(SFW_RED_BOOST, value); return(true);}
							else	{return false;}
							}

	virtual	bool		set_blue_boost(unsigned int value)
							{
							if(config.set_blue_boost(value))
							{dp->set_param(SFW_BLUE_BOOST, value); return(true);}
							else	{return false;}
							}
};


#endif // !defined(CAPTURE_H__INCLUDED_)

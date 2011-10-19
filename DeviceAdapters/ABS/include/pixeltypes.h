///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file		PixelTypes.h
//! 
//! \brief		Defines for Pixel Specification
//! 
//! \author		ABS GmbH Jena (HBau)
//! 
//! \date		8.11.2005 \n
//! 			 -> erstellt \n
//! 
///////////////////////////////////////////////////////////////////////////////
#ifndef _PIX_H_
#define _PIX_H_


//=============================================================================
//
//	PIXEL Pixel Types (S_IMAGE_HEADER::dwPixel_type)
//								
//=============================================================================
//
// ----------------------------------------------------------------------------
//
//! \name	Pixel type: indicate if Pixel is monochrome or RGB
//! <hr>
//! \see S_IMAGE_HEADER::dwPixel_type
//!@{

#define PIX_MONO                        0x01000000      //!< indicate a monochrome image
#define PIX_RGB                         0x02000000      //!< indicate a color image (with red green blue components)
#define PIX_BAYGB                       0x08000000      //!< bayer mask start with green blue
#define PIX_BAYBG                       0x10000000      //!< bayer mask start with blue  green
#define PIX_BAYRG                       0x20000000      //!< bayer mask start with red   green
#define PIX_BAYGR                       0x40000000      //!< bayer mask start with green red
#define PIX_CUSTOM                      0x80000000      //!< custom color type    
#define PIX_COLOR_MASK                  0xFF000000      //!< color mask    

//!@}

// ----------------------------------------------------------------------------
//! \name  Effective bits per pixel:
//! <hr>
//!@{
//! Indicate effective number of bits occupied by the pixel (including padding)\n
//! This can be used to compute amount of memory  required to store an image.

#define	PIX_OCCUPYCOMPRBIT				0x00000000
#define PIX_OCCUPY8BIT					0x00080000      //!< use  8Bit per pixel
#define PIX_OCCUPY10BIT					0x000A0000      //!< use 10Bit per pixel
#define PIX_OCCUPY12BIT					0x000C0000      //!< use 12Bit per pixel
#define PIX_OCCUPY14BIT					0x000E0000      //!< use 14Bit per pixel
#define PIX_OCCUPY16BIT					0x00100000      //!< use 16Bit per pixel
#define PIX_OCCUPY24BIT					0x00180000      //!< use 24Bit per pixel
#define PIX_OCCUPY30BIT					0x001E0000      //!< use 30Bit per pixel
#define PIX_OCCUPY32BIT					0x00200000      //!< use 32Bit per pixel
#define PIX_OCCUPY36BIT					0x00240000      //!< use 36Bit per pixel
#define PIX_OCCUPY48BIT					0x00300000      //!< use 48Bit per pixel
#define PIX_OCCUPY64BIT					0x00400000      //!< use 64Bit per pixel
#define PIX_OCCUPY96BIT					0x00600000      //!< use 96Bit per pixel


//! effective bit per pixel mask
#define PIX_EFFECTIVE_PIXEL_SIZE_MASK	0x00FF0000
//! effective bit per pixel shift
#define PIX_EFFECTIVE_PIXEL_SIZE_SHIFT	16
//! Pixel ID: lower 16-bit of the pixel type
#define PIX_ID_MASK						0x0000FFFF

//!@}


// ----------------------------------------------------------------------------
//! \name  Color channels for custom single color pixel formats
//! <hr>
//!@{
//! Indicate used color channel when capturing a single color channel\n
//! This can be used to show the image in the captured color.

#define PIX_CUSTOM_COLOR_BAY			0x00000000      //!< custom color: bayer pattern
#define PIX_CUSTOM_COLOR_GI				0x00001000      //!< custom color: green 1
#define PIX_CUSTOM_COLOR_GII			0x00002000      //!< custom color: green 2
#define PIX_CUSTOM_COLOR_R				0x00003000      //!< custom color: red
#define PIX_CUSTOM_COLOR_B				0x00004000      //!< custom color: blue
#define PIX_CUSTOM_COLOR_G				0x00005000      //!< custom color: green (1 and 2)

//! custom color channel mask
#define PIX_CUSTOM_COLOR_MASK			0x0000F000
//! custom color channel shift
#define PIX_CUSTOM_COLOR_SHIFT			12

//!@}


// ----------------------------------------------------------------------------
//! \name  Buffer formats: Mono buffer defines 
//!@{
//! <hr>
#define PIX_MONO8			(PIX_MONO | PIX_OCCUPY8BIT  | 0x0001) //!< Mono 8Bit
#define PIX_MONO8SIGEND		(PIX_MONO | PIX_OCCUPY8BIT  | 0x0002)   //!< not supported
#define PIX_MONO10			(PIX_MONO | PIX_OCCUPY16BIT | 0x0003)   //!< Mono 10Bit stored 16Bit (uses the lower bits)
#define PIX_MONO10_PACKED	(PIX_MONO | PIX_OCCUPY12BIT | 0x0004)   //!< not supported
#define PIX_MONO12			(PIX_MONO | PIX_OCCUPY16BIT | 0x0005)   //!< Mono 12Bit stored 16Bit (uses the lower bits)
#define PIX_MONO12_PACKED	(PIX_MONO | PIX_OCCUPY12BIT | 0x0006)   //!< not supported
#define PIX_MONO14			(PIX_MONO | PIX_OCCUPY16BIT | 0x0220)   //!< Mono 14Bit stored 16Bit (uses the lower bits)
#define PIX_MONO14_PACKED	(PIX_MONO | PIX_OCCUPY14BIT | 0x0221)   //!< not supported
#define PIX_MONO16			(PIX_MONO | PIX_OCCUPY16BIT | 0x0007)   //!< not supported

#define PIX_MONO10_PACKED10 (PIX_MONO | PIX_OCCUPY10BIT | 0x0204)   //!< not supported for applications
#define PIX_MONO12_PACKED12 (PIX_MONO | PIX_OCCUPY12BIT | 0x0205)   //!< not supported for applications
#define PIX_MONO14_PACKED14 (PIX_MONO | PIX_OCCUPY14BIT | 0x0222)   //!< not supported for applications

#define PIX_MONO8_BGR8_PACKED  (PIX_CUSTOM | PIX_RGB | PIX_OCCUPY24BIT | 0x0001) //!< Mono  8Bit packed as BGR8  (24Bit per pixel)
#define PIX_BAYGR8_BGR8_PACKED (PIX_CUSTOM | PIX_RGB | PIX_OCCUPY24BIT | 0x0002) //!< Bayer 8Bit packed as BGR8  (24Bit per pixel)
#define PIX_BAYRG8_BGR8_PACKED (PIX_CUSTOM | PIX_RGB | PIX_OCCUPY24BIT | 0x0003) //!< Bayer 8Bit packed as BGR8  (24Bit per pixel)
#define PIX_BAYBG8_BGR8_PACKED (PIX_CUSTOM | PIX_RGB | PIX_OCCUPY24BIT | 0x0004) //!< Bayer 8Bit packed as BGR8  (24Bit per pixel)
#define PIX_BAYGB8_BGR8_PACKED (PIX_CUSTOM | PIX_RGB | PIX_OCCUPY24BIT | 0x0005) //!< Bayer 8Bit packed as BGR8  (24Bit per pixel)

#define PIX_MONO8_BGRA8_PACKED (PIX_CUSTOM | PIX_RGB | PIX_OCCUPY32BIT | 0x0001) //!< Mono  8Bit packed as BGRA8 (32Bit per pixel)

#define PIX_MONO8_PREVIEW				(PIX_CUSTOM | PIX_MONO | PIX_OCCUPY8BIT  | 0x0133)	//!< Mono 8Bit (special interpolation)
#define PIX_MONO8_PREVIEW_BGRA8_PACKED	(PIX_CUSTOM | PIX_RGB  | PIX_OCCUPY32BIT | 0x0134)	//!< Mono 8Bit packed as BGRA8 (32Bit per pixel, special interpolation)

//!@}

// ----------------------------------------------------------------------------
//! \name  Buffer formats: Bayer buffer defines 
//!@{
//! <hr>
#define PIX_BAYGR8			(PIX_MONO | PIX_OCCUPY8BIT  | 0x0008)   //!< Bayer pattern 8Bit (first line: green - red)
#define PIX_BAYRG8			(PIX_MONO | PIX_OCCUPY8BIT  | 0x0009)   //!< Bayer pattern 8Bit (first line: red - green)
#define PIX_BAYGB8			(PIX_MONO | PIX_OCCUPY8BIT  | 0x000A)   //!< Bayer pattern 8Bit (first line: green - blue)
#define PIX_BAYBG8			(PIX_MONO | PIX_OCCUPY8BIT  | 0x000B)   //!< Bayer pattern 8Bit (first line: blue - green)
#define PIX_BAYGR10			(PIX_MONO | PIX_OCCUPY16BIT | 0x000C)   //!< Bayer pattern 10Bit stored at stored 16Bit (uses the lower bits, first line: green - red)
#define PIX_BAYRG10			(PIX_MONO | PIX_OCCUPY16BIT | 0x000D)   //!< Bayer pattern 10Bit stored at stored 16Bit (uses the lower bits, first line: red - green)
#define PIX_BAYGB10			(PIX_MONO | PIX_OCCUPY16BIT | 0x000E)   //!< Bayer pattern 10Bit stored at stored 16Bit (uses the lower bits, first line: green - blue)
#define PIX_BAYBG10			(PIX_MONO | PIX_OCCUPY16BIT | 0x000F)   //!< Bayer pattern 10Bit stored at stored 16Bit (uses the lower bits, first line: blue - green)
#define PIX_BAYGR12			(PIX_MONO | PIX_OCCUPY16BIT | 0x0010)   //!< Bayer pattern 12Bit stored at stored 16Bit (uses the lower bits, first line: green - red)
#define PIX_BAYRG12			(PIX_MONO | PIX_OCCUPY16BIT | 0x0011)   //!< Bayer pattern 12Bit stored at stored 16Bit (uses the lower bits, first line: red - green)
#define PIX_BAYGB12			(PIX_MONO | PIX_OCCUPY16BIT | 0x0012)   //!< Bayer pattern 12Bit stored at stored 16Bit (uses the lower bits, first line: green - blue)
#define PIX_BAYBG12			(PIX_MONO | PIX_OCCUPY16BIT | 0x0013)   //!< Bayer pattern 12Bit stored at stored 16Bit (uses the lower bits, first line: blue - green)
#define PIX_BAYGR14			(PIX_MONO | PIX_OCCUPY16BIT | 0x022A)   //!< Bayer pattern 14Bit stored at stored 16Bit (uses the lower bits, first line: green - red)
#define PIX_BAYRG14			(PIX_MONO | PIX_OCCUPY16BIT | 0x022B)   //!< Bayer pattern 12Bit stored at stored 16Bit (uses the lower bits, first line: red - green)
#define PIX_BAYGB14			(PIX_MONO | PIX_OCCUPY16BIT | 0x022C)   //!< Bayer pattern 12Bit stored at stored 16Bit (uses the lower bits, first line: green - blue)
#define PIX_BAYBG14			(PIX_MONO | PIX_OCCUPY16BIT | 0x022D)   //!< Bayer pattern 14Bit stored at stored 16Bit (uses the lower bits, first line: blue - green)

#define PIX_BAYGR10_PACKED10  (PIX_MONO | PIX_OCCUPY10BIT | 0x0206)   //!< not supported for applications
#define PIX_BAYRG10_PACKED10  (PIX_MONO | PIX_OCCUPY10BIT | 0x0207)   //!< not supported for applications
#define PIX_BAYGB10_PACKED10  (PIX_MONO | PIX_OCCUPY10BIT | 0x0208)   //!< not supported for applications
#define PIX_BAYBG10_PACKED10  (PIX_MONO | PIX_OCCUPY10BIT | 0x0209)   //!< not supported for applications

#define PIX_BAYGR12_PACKED12  (PIX_MONO | PIX_OCCUPY12BIT | 0x020A)   //!< not supported for applications
#define PIX_BAYRG12_PACKED12  (PIX_MONO | PIX_OCCUPY12BIT | 0x020B)   //!< not supported for applications
#define PIX_BAYGB12_PACKED12  (PIX_MONO | PIX_OCCUPY12BIT | 0x020C)   //!< not supported for applications
#define PIX_BAYBG12_PACKED12  (PIX_MONO | PIX_OCCUPY12BIT | 0x020D)   //!< not supported for applications


#define PIX_BAYGR14_PACKED14  (PIX_MONO | PIX_OCCUPY14BIT | 0x0226)   //!< not supported for applications
#define PIX_BAYRG14_PACKED14  (PIX_MONO | PIX_OCCUPY14BIT | 0x0227)   //!< not supported for applications
#define PIX_BAYGB14_PACKED14  (PIX_MONO | PIX_OCCUPY14BIT | 0x0228)   //!< not supported for applications
#define PIX_BAYBG14_PACKED14  (PIX_MONO | PIX_OCCUPY14BIT | 0x0229)   //!< not supported for applications

//!@}

// ----------------------------------------------------------------------------
//! \name  Buffer formats: Multi channel buffer defines 
//!@{
//! <hr>
#define PIX_BAYGR8_C1           (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY8BIT | 0x0010)       //!< Bayer pattern 8Bit 1 channel (first line: green - red)
#define PIX_BAYGR8_C2           (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY8BIT | 0x0020)       //!< Bayer pattern 8Bit 2 channel (first line: green - red)
#define PIX_BAYGR8_C4           (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY8BIT | 0x0040)       //!< Bayer pattern 8Bit 4 channel (first line: green - red)

#define PIX_BAYBG8_C1           (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY8BIT | 0x0010)       //!< Bayer pattern 8Bit 1 channel (first line: blue  - green)
#define PIX_BAYBG8_C2           (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY8BIT | 0x0020)       //!< Bayer pattern 8Bit 2 channel (first line: blue  - green)
#define PIX_BAYBG8_C4           (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY8BIT | 0x0040)       //!< Bayer pattern 8Bit 4 channel (first line: blue  - green)

#define PIX_BAYRG8_C1           (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY8BIT  | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: red   - green)
#define PIX_BAYRG8_C2           (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY8BIT  | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: red   - green)
#define PIX_BAYRG8_C4           (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY8BIT  | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: red   - green)

#define PIX_BAYGB8_C1           (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY8BIT  | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: green - blue)
#define PIX_BAYGB8_C2           (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY8BIT  | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: green - blue)
#define PIX_BAYGB8_C4           (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY8BIT  | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: green - blue)

#define PIX_BAYGR10_C1          (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY16BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: green - red)
#define PIX_BAYGR10_C2          (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY16BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: green - red)
#define PIX_BAYGR10_C4          (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY16BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: green - red)

#define PIX_BAYBG10_C1          (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY16BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: blue  - green)
#define PIX_BAYBG10_C2          (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY16BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: blue  - green)
#define PIX_BAYBG10_C4          (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY16BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: blue  - green)

#define PIX_BAYRG10_C1          (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY16BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: red   - green)
#define PIX_BAYRG10_C2          (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY16BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: red   - green)
#define PIX_BAYRG10_C4          (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY16BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: red   - green)

#define PIX_BAYGB10_C1          (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY16BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: green - blue)
#define PIX_BAYGB10_C2          (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY16BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: green - blue)
#define PIX_BAYGB10_C4          (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY16BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: green - blue)

#define PIX_BAYGR12_C1          (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY16BIT | 0x0012)      //!< Bayer pattern 8Bit 1 channel (first line: green - red)
#define PIX_BAYGR12_C2          (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY16BIT | 0x0022)      //!< Bayer pattern 8Bit 2 channel (first line: green - red)
#define PIX_BAYGR12_C4          (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY16BIT | 0x0042)      //!< Bayer pattern 8Bit 4 channel (first line: green - red)

#define PIX_BAYBG12_C1          (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY16BIT | 0x0012)      //!< Bayer pattern 8Bit 1 channel (first line: blue  - green)
#define PIX_BAYBG12_C2          (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY16BIT | 0x0022)      //!< Bayer pattern 8Bit 2 channel (first line: blue  - green)
#define PIX_BAYBG12_C4          (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY16BIT | 0x0042)      //!< Bayer pattern 8Bit 4 channel (first line: blue  - green)

#define PIX_BAYRG12_C1          (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY16BIT | 0x0012)      //!< Bayer pattern 8Bit 1 channel (first line: red   - green)
#define PIX_BAYRG12_C2          (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY16BIT | 0x0022)      //!< Bayer pattern 8Bit 2 channel (first line: red   - green)
#define PIX_BAYRG12_C4          (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY16BIT | 0x0042)      //!< Bayer pattern 8Bit 4 channel (first line: red   - green)

#define PIX_BAYGB12_C1          (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY16BIT | 0x0012)      //!< Bayer pattern 8Bit 1 channel (first line: green - blue)
#define PIX_BAYGB12_C2          (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY16BIT | 0x0022)      //!< Bayer pattern 8Bit 2 channel (first line: green - blue)
#define PIX_BAYGB12_C4          (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY16BIT | 0x0042)      //!< Bayer pattern 8Bit 4 channel (first line: green - blue)

#define PIX_BAYGR14_C1          (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY16BIT | 0x0014)      //!< Bayer pattern 8Bit 1 channel (first line: green - red)
#define PIX_BAYGR14_C2          (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY16BIT | 0x0024)      //!< Bayer pattern 8Bit 2 channel (first line: green - red)
#define PIX_BAYGR14_C4          (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY16BIT | 0x0044)      //!< Bayer pattern 8Bit 4 channel (first line: green - red)

#define PIX_BAYBG14_C1          (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY16BIT | 0x0014)      //!< Bayer pattern 8Bit 1 channel (first line: blue  - green)
#define PIX_BAYBG14_C2          (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY16BIT | 0x0024)      //!< Bayer pattern 8Bit 2 channel (first line: blue  - green)
#define PIX_BAYBG14_C4          (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY16BIT | 0x0044)      //!< Bayer pattern 8Bit 4 channel (first line: blue  - green)

#define PIX_BAYRG14_C1          (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY16BIT | 0x0014)      //!< Bayer pattern 8Bit 1 channel (first line: red   - green)
#define PIX_BAYRG14_C2          (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY16BIT | 0x0024)      //!< Bayer pattern 8Bit 2 channel (first line: red   - green)
#define PIX_BAYRG14_C4          (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY16BIT | 0x0044)      //!< Bayer pattern 8Bit 4 channel (first line: red   - green)

#define PIX_BAYGB14_C1          (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY16BIT | 0x0014)      //!< Bayer pattern 8Bit 1 channel (first line: green - blue)
#define PIX_BAYGB14_C2          (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY16BIT | 0x0024)      //!< Bayer pattern 8Bit 2 channel (first line: green - blue)
#define PIX_BAYGB14_C4          (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY16BIT | 0x0044)      //!< Bayer pattern 8Bit 4 channel (first line: green - blue)

#define PIX_BAYGR10_C1_PACKED   (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY10BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: green - red)
#define PIX_BAYGR10_C2_PACKED   (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY10BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: green - red)
#define PIX_BAYGR10_C4_PACKED   (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY10BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: green - red)

#define PIX_BAYBG10_C1_PACKED   (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY10BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: blue  - green)
#define PIX_BAYBG10_C2_PACKED   (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY10BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: blue  - green)
#define PIX_BAYBG10_C4_PACKED   (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY10BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: blue  - green)

#define PIX_BAYRG10_C1_PACKED   (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY10BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: red   - green)
#define PIX_BAYRG10_C2_PACKED   (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY10BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: red   - green)
#define PIX_BAYRG10_C4_PACKED   (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY10BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: red   - green)

#define PIX_BAYGB10_C1_PACKED   (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY10BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: green - blue)
#define PIX_BAYGB10_C2_PACKED   (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY10BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: green - blue)
#define PIX_BAYGB10_C4_PACKED   (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY10BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: green - blue)

#define PIX_BAYGR12_C1_PACKED   (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY12BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: green - red)
#define PIX_BAYGR12_C2_PACKED   (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY12BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: green - red)
#define PIX_BAYGR12_C4_PACKED   (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY12BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: green - red)

#define PIX_BAYBG12_C1_PACKED   (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY12BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: blue  - green)
#define PIX_BAYBG12_C2_PACKED   (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY12BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: blue  - green)
#define PIX_BAYBG12_C4_PACKED   (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY12BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: blue  - green)

#define PIX_BAYRG12_C1_PACKED   (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY12BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: red   - green)
#define PIX_BAYRG12_C2_PACKED   (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY12BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: red   - green)
#define PIX_BAYRG12_C4_PACKED   (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY12BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: red   - green)

#define PIX_BAYGB12_C1_PACKED   (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY12BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: green - blue)
#define PIX_BAYGB12_C2_PACKED   (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY12BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: green - blue)
#define PIX_BAYGB12_C4_PACKED   (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY12BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: green - blue)

#define PIX_BAYGR14_C1_PACKED   (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY14BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: green - red)
#define PIX_BAYGR14_C2_PACKED   (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY14BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: green - red)
#define PIX_BAYGR14_C4_PACKED   (PIX_CUSTOM | PIX_BAYGR | PIX_OCCUPY14BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: green - red)

#define PIX_BAYBG14_C1_PACKED   (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY14BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: blue  - green)
#define PIX_BAYBG14_C2_PACKED   (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY14BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: blue  - green)
#define PIX_BAYBG14_C4_PACKED   (PIX_CUSTOM | PIX_BAYBG | PIX_OCCUPY14BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: blue  - green)

#define PIX_BAYRG14_C1_PACKED   (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY14BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: red   - green)
#define PIX_BAYRG14_C2_PACKED   (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY14BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: red   - green)
#define PIX_BAYRG14_C4_PACKED   (PIX_CUSTOM | PIX_BAYRG | PIX_OCCUPY14BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: red   - green)

#define PIX_BAYGB14_C1_PACKED   (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY14BIT | 0x0010)      //!< Bayer pattern 8Bit 1 channel (first line: green - blue)
#define PIX_BAYGB14_C2_PACKED   (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY14BIT | 0x0020)      //!< Bayer pattern 8Bit 2 channel (first line: green - blue)
#define PIX_BAYGB14_C4_PACKED   (PIX_CUSTOM | PIX_BAYGB | PIX_OCCUPY14BIT | 0x0040)      //!< Bayer pattern 8Bit 4 channel (first line: green - blue)

#define PIX_MONO8_C1            (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY8BIT  | 0x0010)      //!< Mono 8Bit 1 channel
#define PIX_MONO8_C2            (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY8BIT  | 0x0020)      //!< Mono 8Bit 2 channel
#define PIX_MONO8_C4            (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY8BIT  | 0x0040)      //!< Mono 8Bit 4 channel

#define PIX_MONO10_C1           (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY16BIT | 0x0010)      //!< Mono 10Bit stored 16Bit 1 channel (uses the lower bits)
#define PIX_MONO10_C2           (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY16BIT | 0x0020)      //!< Mono 10Bit stored 16Bit 2 channel (uses the lower bits)
#define PIX_MONO10_C4           (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY16BIT | 0x0040)      //!< Mono 10Bit stored 16Bit 4 channel (uses the lower bits)

#define PIX_MONO12_C1           (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY16BIT | 0x0012)      //!< Mono 12Bit stored 16Bit 1 channel (uses the lower bits)
#define PIX_MONO12_C2           (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY16BIT | 0x0022)      //!< Mono 12Bit stored 16Bit 2 channel (uses the lower bits)
#define PIX_MONO12_C4           (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY16BIT | 0x0042)      //!< Mono 12Bit stored 16Bit 4 channel (uses the lower bits)

#define PIX_MONO14_C1           (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY16BIT | 0x0014)      //!< Mono 14Bit stored 16Bit 1 channel (uses the lower bits)
#define PIX_MONO14_C2           (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY16BIT | 0x0024)      //!< Mono 14Bit stored 16Bit 2 channel (uses the lower bits)
#define PIX_MONO14_C4           (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY16BIT | 0x0044)      //!< Mono 14Bit stored 16Bit 4 channel (uses the lower bits)

#define PIX_MONO10_C1_PACKED    (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY10BIT | 0x0010)      //!< Mono 10Bit stored 10Bit 1 channel 
#define PIX_MONO10_C2_PACKED    (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY10BIT | 0x0020)      //!< Mono 10Bit stored 10Bit 2 channel 
#define PIX_MONO10_C4_PACKED    (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY10BIT | 0x0040)      //!< Mono 10Bit stored 10Bit 4 channel 
                                                                                              
#define PIX_MONO12_C1_PACKED    (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY12BIT | 0x0010)      //!< Mono 12Bit stored 12Bit 1 channel 
#define PIX_MONO12_C2_PACKED    (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY12BIT | 0x0020)      //!< Mono 12Bit stored 12Bit 2 channel 
#define PIX_MONO12_C4_PACKED    (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY12BIT | 0x0040)      //!< Mono 12Bit stored 12Bit 4 channel 
                                                                                              
#define PIX_MONO14_C1_PACKED    (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY14BIT | 0x0010)      //!< Mono 14Bit stored 14Bit 1 channel 
#define PIX_MONO14_C2_PACKED    (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY14BIT | 0x0020)      //!< Mono 14Bit stored 14Bit 2 channel 
#define PIX_MONO14_C4_PACKED    (PIX_CUSTOM | PIX_MONO  | PIX_OCCUPY14BIT | 0x0040)      //!< Mono 14Bit stored 14Bit 4 channel

//!@}

// ----------------------------------------------------------------------------
//! \name  Buffer formats: RGB packed buffer defines
//!@{
//! <hr>
#define PIX_RGB8_PACKED		(PIX_RGB  | PIX_OCCUPY24BIT | 0x0014)   //!< not supported
#define PIX_BGR8_PACKED		(PIX_RGB  | PIX_OCCUPY24BIT | 0x0015)   //!< BGR 24Bit (blue green red)
#define PIX_RGBA8_PACKED	(PIX_RGB  | PIX_OCCUPY32BIT | 0x0016)   //!< not supported
#define PIX_BGRA8_PACKED	(PIX_RGB  | PIX_OCCUPY32BIT | 0x0017)   //!< BGRA 32Bit (blue green red alpha=0)
#define PIX_RGB10_PACKED	(PIX_RGB  | PIX_OCCUPY48BIT | 0x0018)   //!< not supported
#define PIX_BGR10_PACKED	(PIX_RGB  | PIX_OCCUPY48BIT | 0x0019)   //!< BGR 48Bit, 10Bit per component stored at 16Bit (uses lower bits, components: blue,green,red,alpha=0)
#define PIX_RGB12_PACKED	(PIX_RGB  | PIX_OCCUPY48BIT | 0x001A)   //!< not supported
#define PIX_BGR12_PACKED	(PIX_RGB  | PIX_OCCUPY48BIT | 0x001B)   //!< BGRA 48Bit, 12Bit per component stored at 16Bit (uses lower bits, components: blue,green,red,alpha=0)
#define PIX_RGB14_PACKED	(PIX_RGB  | PIX_OCCUPY48BIT | 0x0223)   //!< not supported
#define PIX_BGR14_PACKED	(PIX_RGB  | PIX_OCCUPY48BIT | 0x0224)   //!< BGRA 48Bit, 14Bit per component stored at 16Bit (uses lower bits, components: blue,green,red,alpha=0)

#define PIX_RGB10V1_PACKED	(PIX_RGB  | PIX_OCCUPY32BIT | 0x001C)   //!< not supported
#define PIX_BGR10V2_PACKED	(PIX_RGB  | PIX_OCCUPY32BIT | 0x001D)   //!< not supported

#define PIX_BGRA10_PACKED	(PIX_RGB  | PIX_OCCUPY64BIT | 0x001E)   //!< BGR 48Bit, 10Bit per component stored at 16Bit (uses lower bits, components: blue,green,red,alpha=0)
#define PIX_BGRA12_PACKED	(PIX_RGB  | PIX_OCCUPY64BIT | 0x001F)   //!< BGR 48Bit, 12Bit per component stored at 16Bit (uses lower bits, components: blue,green,red,alpha=0)
#define PIX_BGRA14_PACKED	(PIX_RGB  | PIX_OCCUPY64BIT | 0x022E)   //!< BGR 48Bit, 14Bit per component stored at 16Bit (uses lower bits, components: blue,green,red,alpha=0)


#define PIX_RGB10_PACKED10	(PIX_RGB  | PIX_OCCUPY30BIT | 0x0218)   //!< not supported
#define PIX_BGR10_PACKED10	(PIX_RGB  | PIX_OCCUPY30BIT | 0x0219)   //!< not supported
#define PIX_RGB12_PACKED12	(PIX_RGB  | PIX_OCCUPY36BIT | 0x021A)   //!< not supported
#define PIX_BGR12_PACKED12	(PIX_RGB  | PIX_OCCUPY36BIT | 0x021B)   //!< not supported

#define PIX_RGB16_PACKED	(PIX_RGB  | PIX_OCCUPY48BIT | 0x021E)   //!< not supported
#define PIX_BGR16_PACKED	(PIX_RGB  | PIX_OCCUPY48BIT | 0x021F)   //!< not supported


#define	PIX_RGB555_PACKED	(PIX_RGB  | PIX_OCCUPY16BIT | 0x0100)   //!< not supported
#define	PIX_BGR555_PACKED	(PIX_RGB  | PIX_OCCUPY16BIT | 0x0101)   //!< not supported
#define	PIX_RGB565_PACKED	(PIX_RGB  | PIX_OCCUPY16BIT | 0x0102)   //!< not supported
#define	PIX_BGR565_PACKED	(PIX_RGB  | PIX_OCCUPY16BIT | 0x0103)   //!< not supported

#define PIX_BGRA8_PREVIEW	(PIX_CUSTOM | PIX_RGB | PIX_OCCUPY32BIT | 0x0135)	//!< BGRA 32Bit (special interpolation)
#define PIX_BGR8_PREVIEW	(PIX_CUSTOM | PIX_RGB | PIX_OCCUPY24BIT | 0x0136)	//!< BGR 24Bit (special interpolation)

//!@}

// ----------------------------------------------------------------------------
//! \name  Buffer formats: YUV packed buffer defines
//!@{
//! <hr>
#define PIX_YUV411_PACKED	(PIX_RGB  | PIX_OCCUPY12BIT | 0x001E)   //!< not supported
#define PIX_YUV422_PACKED	(PIX_RGB  | PIX_OCCUPY16BIT | 0x001F)   //!< YUYV-422
#define PIX_YUV444_PACKED	(PIX_RGB  | PIX_OCCUPY24BIT | 0x0020)   //!< not supported
#define PIX_UYVY422_PACKED  (PIX_RGB  | PIX_OCCUPY16BIT | 0x0021)   //!< UYVY-422 (interlaced)
#define PIX_UYVY422P_PACKED (PIX_RGB  | PIX_OCCUPY16BIT | 0x0099)   //!< UYVY-422 (progressive)
#define PIX_YUV420_PACKED	(PIX_RGB  | PIX_OCCUPY12BIT | 0x0104)	//!< not supported

//!@}

// ----------------------------------------------------------------------------
//! \name  Buffer formats: RGB planar buffer defines
//!@{
//! <hr>
#define PIX_RGB8_PLANAR		(PIX_RGB  | PIX_OCCUPY24BIT | 0x0021)   //!< image stored in 3 planes (red, green, blue) each width 8Bit/Pixel
#define PIX_RGB10_PLANAR	(PIX_RGB  | PIX_OCCUPY48BIT | 0x0022)   //!< image stored in 3 planes (red, green, blue) each width 16Bit/Pixel (used 10Bit/Pixel)
#define PIX_RGB12_PLANAR	(PIX_RGB  | PIX_OCCUPY48BIT | 0x0023)   //!< image stored in 3 planes (red, green, blue) each width 16Bit/Pixel (used 12Bit/Pixel)
#define PIX_RGB14_PLANAR	(PIX_RGB  | PIX_OCCUPY48BIT | 0x0225)   //!< image stored in 3 planes (red, green, blue) each width 16Bit/Pixel (used 14Bit/Pixel)
#define PIX_RGB16_PLANAR	(PIX_RGB  | PIX_OCCUPY48BIT | 0x0024)   //!< not supported

//!@}

// ----------------------------------------------------------------------------
//! \name  Buffer formats: Single Color Channel buffer defines
//!@{
//! <hr>
#define PIX_CR8				(PIX_CUSTOM | PIX_OCCUPY8BIT  | PIX_CUSTOM_COLOR_R 		| 0x105)    //!< channel red     8Bit 
#define PIX_CG8				(PIX_CUSTOM | PIX_OCCUPY8BIT  | PIX_CUSTOM_COLOR_G 		| 0x106)    //!< channel green   8Bit 
#define PIX_CB8				(PIX_CUSTOM | PIX_OCCUPY8BIT  | PIX_CUSTOM_COLOR_B 		| 0x107)    //!< channel blue    8Bit 
#define PIX_CGI8			(PIX_CUSTOM | PIX_OCCUPY8BIT  | PIX_CUSTOM_COLOR_GI 	| 0x108)    //!< channel green 1 8Bit 
#define PIX_CGII8			(PIX_CUSTOM | PIX_OCCUPY8BIT  | PIX_CUSTOM_COLOR_GII 	| 0x109)    //!< channel green 2 8Bit 

#define PIX_CR10_PACKED10	(PIX_CUSTOM | PIX_OCCUPY10BIT | PIX_CUSTOM_COLOR_R 		| 0x10A)    //!< not supported for applications
#define PIX_CG10_PACKED10	(PIX_CUSTOM | PIX_OCCUPY10BIT | PIX_CUSTOM_COLOR_G 		| 0x10B)    //!< not supported for applications
#define PIX_CB10_PACKED10	(PIX_CUSTOM | PIX_OCCUPY10BIT | PIX_CUSTOM_COLOR_B 		| 0x10C)    //!< not supported for applications
#define PIX_CGI10_PACKED10	(PIX_CUSTOM | PIX_OCCUPY10BIT | PIX_CUSTOM_COLOR_GI 	| 0x10D)    //!< not supported for applications
#define PIX_CGII10_PACKED10	(PIX_CUSTOM | PIX_OCCUPY10BIT | PIX_CUSTOM_COLOR_GII 	| 0x10E)    //!< not supported for applications

#define PIX_CR10			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_R 		| 0x10F)    //!< channel red     10Bit 
#define PIX_CG10			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_G 		| 0x110)    //!< channel green   10Bit 
#define PIX_CB10			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_B 		| 0x111)    //!< channel blue    10Bit 
#define PIX_CGI10			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_GI 	| 0x112)    //!< channel green 1 10Bit
#define PIX_CGII10			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_GII 	| 0x113)    //!< channel green 2 10Bit

#define PIX_CR12_PACKED12	(PIX_CUSTOM | PIX_OCCUPY12BIT | PIX_CUSTOM_COLOR_R 		| 0x114)    //!< not supported for applications
#define PIX_CG12_PACKED12	(PIX_CUSTOM | PIX_OCCUPY12BIT | PIX_CUSTOM_COLOR_G	 	| 0x115)    //!< not supported for applications
#define PIX_CB12_PACKED12	(PIX_CUSTOM | PIX_OCCUPY12BIT | PIX_CUSTOM_COLOR_B 		| 0x116)    //!< not supported for applications
#define PIX_CGI12_PACKED12	(PIX_CUSTOM | PIX_OCCUPY12BIT | PIX_CUSTOM_COLOR_GI 	| 0x117)    //!< not supported for applications
#define PIX_CGII12_PACKED12	(PIX_CUSTOM | PIX_OCCUPY12BIT | PIX_CUSTOM_COLOR_GII 	| 0x118)    //!< not supported for applications

#define PIX_CR12			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_R 		| 0x119)    //!< channel red     12Bit 
#define PIX_CG12			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_G 		| 0x11A)    //!< channel green   12Bit 
#define PIX_CB12			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_B 		| 0x11B)    //!< channel blue    12Bit 
#define PIX_CGI12			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_GI 	| 0x11C)    //!< channel green 1 12Bit
#define PIX_CGII12			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_GII 	| 0x11D)    //!< channel green 2 12Bit

#define PIX_CR14_PACKED14	(PIX_CUSTOM | PIX_OCCUPY14BIT | PIX_CUSTOM_COLOR_R 		| 0x22F)    //!< not supported for applications
#define PIX_CG14_PACKED14	(PIX_CUSTOM | PIX_OCCUPY14BIT | PIX_CUSTOM_COLOR_G	 	| 0x230)    //!< not supported for applications
#define PIX_CB14_PACKED14	(PIX_CUSTOM | PIX_OCCUPY14BIT | PIX_CUSTOM_COLOR_B 		| 0x231)    //!< not supported for applications
#define PIX_CGI14_PACKED14	(PIX_CUSTOM | PIX_OCCUPY14BIT | PIX_CUSTOM_COLOR_GI 	| 0x232)    //!< not supported for applications
#define PIX_CGII14_PACKED14	(PIX_CUSTOM | PIX_OCCUPY14BIT | PIX_CUSTOM_COLOR_GII 	| 0x233)    //!< not supported for applications

#define PIX_CR14			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_R 		| 0x234)    //!< channel red     14Bit 
#define PIX_CG14			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_G 		| 0x235)    //!< channel green   14Bit 
#define PIX_CB14			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_B 		| 0x236)    //!< channel blue    14Bit 
#define PIX_CGI14			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_GI 	| 0x237)    //!< channel green 1 14Bit
#define PIX_CGII14			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_GII 	| 0x238)    //!< channel green 2 14Bit

#define PIX_CR16			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_R 		| 0x11E)    //!< not supported
#define PIX_CG16			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_G 		| 0x11F)    //!< not supported
#define PIX_CB16			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_B 		| 0x120)    //!< not supported
#define PIX_CGI16			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_GI 	| 0x121)    //!< not supported
#define PIX_CGII16			(PIX_CUSTOM | PIX_OCCUPY16BIT | PIX_CUSTOM_COLOR_GII 	| 0x122)    //!< not supported

//!@}

// ----------------------------------------------------------------------------
//! \name  Buffer formats: Compressed buffer defines
//!@{
//! <hr>
#define PIX_JPEG_COMPRESSED		(PIX_RGB | PIX_OCCUPYCOMPRBIT | 0x0130)     //!< image is a JPEG - file
#define PIX_JPEG2000_COMPRESSED	(PIX_RGB | PIX_OCCUPYCOMPRBIT | 0x0131)     //!< not supported
#define PIX_LOSSLESS_COMPRESSED	(PIX_RGB | PIX_OCCUPYCOMPRBIT | 0x0132)     //!< huffman coded prediction image
#define PIX_BMP_COLOR_PACKED    (PIX_RGB | PIX_OCCUPY24BIT    | 0x0133)     //!< image is a BMP - file with 24Bit/Pixel (color)
#define PIX_BMP_MONO_PACKED     (PIX_MONO| PIX_OCCUPY8BIT     | 0x0134)     //!< image is a BMP - file with 8Bit/Pixel (monochrome)

#define PIX_AVI_COMPRESSED      (PIX_RGB | PIX_OCCUPYCOMPRBIT | 0x0135)     //!< image is a AVI - file

//!@}

// ----------------------------------------------------------------------------
//! \name  Buffer formats: Line Camera defines
//!@{
//! <hr>
#define PIX_LINECAM12_PACKED12	(PIX_CUSTOM | PIX_OCCUPY12BIT | 0x0240)     //!< line camera data packed 12bit
#define PIX_LINECAM12	        (PIX_CUSTOM | PIX_OCCUPY16BIT | 0x0241)     //!< line camera data 16bit

//!@}


#endif // _PIX_H_

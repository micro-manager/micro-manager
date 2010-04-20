//
//  NativeGUIMac.mm
//  NativeGUIMac
//
//  Created by Arthur Edelstein on 4/16/10.
//  Copyright 2010 UCSF. All rights reserved.


#import "NativeGUI.h"
#import <Cocoa/Cocoa.h>

std::string runMDABrowser(std::string startDirectory)
{
	NSAutoreleasePool *autoreleasepool = [[NSAutoreleasePool alloc] init];
	NSString *startDir = [[NSString alloc] initWithCString:startDirectory.c_str() encoding:NSMacOSRomanStringEncoding];

	NSArray *fileTypes = [NSArray arrayWithObjects:@"tif",@"tiff",@"txt",@"xml",nil];
	NSOpenPanel *oPanel = [NSOpenPanel openPanel];
	
	int result;
	NSString *fileChosen;
	
	[oPanel setAllowsMultipleSelection:NO];
	[oPanel setCanChooseDirectories:YES];
	[oPanel setPrompt:@"Choose"];
	[oPanel orderFrontRegardless];
	
	result = [oPanel runModalForDirectory:startDir file:nil types:fileTypes];
	
	if (result == NSOKButton) {
		fileChosen = [oPanel filename];	
		char * tmp;
		tmp = (char *) [fileChosen UTF8String];
		[autoreleasepool release];
		return std::string(tmp);
	} else {
		[autoreleasepool release];
		return std::string("");
	}
	
	
}

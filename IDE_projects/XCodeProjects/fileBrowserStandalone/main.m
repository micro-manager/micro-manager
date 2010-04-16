//
//  main.m
//  fileBrowserStandalone
//
//  Created by Arthur Edelstein on 4/15/10.
//  Copyright UCSF 2010. All rights reserved.
//

#import <Cocoa/Cocoa.h>


int main(int argc, char *argv[])
{
	NSAutoreleasePool *autoreleasepool = [[NSAutoreleasePool alloc] init];

    NSArray *fileTypes = [NSArray arrayWithObjects:@"tif",@"tiff",@"txt",@"xml"];
    NSOpenPanel *oPanel = [NSOpenPanel openPanel];

	int result;
	NSString *fileChosen;
	
    [oPanel setAllowsMultipleSelection:NO];
	[oPanel setCanChooseDirectories:YES];
	[oPanel setPrompt:@"Choose"];
	[oPanel setLevel:NSScreenSaverWindowLevel];
	
	if (argc > 1)
		result = [oPanel runModalForDirectory:[NSString stringWithCString:argv[1]] file:nil types:fileTypes];
    else
		result = [oPanel runModalForDirectory:nil file:nil types:fileTypes];
	
	if (result == NSOKButton) {
		fileChosen = [oPanel filename];		
		printf("%s\n",[fileChosen cString]);
    } else {
		printf("\n");
	}
	
	[autoreleasepool release];
}

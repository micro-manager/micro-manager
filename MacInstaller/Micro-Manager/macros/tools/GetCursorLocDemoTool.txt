//  This macro tool demonstrates how to use the getCursorLoc() 
//  function in a macro tool. To use it, install the
//  macro, select the new tool, click and drag on an 
//  image. While dragging, try pressing the shift, control or option keys.
//  Pressing the right mouse button, or click-control on a Mac,
//  displays a pop-up menu. There needs to be a way to disable 
//  this feature. Pressing the alt key may not work on windows.
//  Information about tool macros is available at:
//  http://rsb.info.nih.gov/ij/developer/macro/macros.html#tools


  macro "GetCursorLoc Demo Tool - C059o11ee" {
      leftButton=16;
      rightButton=4;
      shift=1;
      ctrl=2; 
      alt=8;
      x2=-1; y2=-1; z2=-1; flags2=-1;
      getCursorLoc(x, y, z, flags);
      while (flags&leftButton!=0) {
          getCursorLoc(x, y, z, flags);
          if (x!=x2 || y!=y2 || z!=z2 || flags!=flags2) {
              s = " ";
              if (flags&leftButton!=0) s = s + "<left>";
              if (flags&rightButton!=0) s = s + "<right>";
              if (flags&shift!=0) s = s + "<shift>";
              if (flags&ctrl!=0) s = s + "<ctrl> ";
              if (flags&alt!=0) s = s + "<alt>";
              print(x+" "+y+" "+z+" "+flags + "" + s);
              logOpened = true;
              startTime = getTime();
          }
          x2=x; y2=y; z2=z; flags2=flags;
          wait(10);
      }
 }




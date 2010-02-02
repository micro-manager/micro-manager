// "RoiManagerMacros"
// This macro set provides several macros for 
// using the ROI Manager. The ROI Manager
// is opened if it is not already open.
// Add these macros to StartupMacros.txt and they
// will be automatically installed when ImageJ starts up.

  macro "Add [1]" {
      roiManager("add");
  }

  macro "Add and Name [2]" {
      requires("1.35c")
      setKeyDown("alt");
      roiManager("add");
  }

  macro "Add and Draw [3]" {
      roiManager("Add & Draw");
  }

  macro "Add, Name and Draw [4]" {
      requires("1.35c")
      setKeyDown("alt shift");
      roiManager("add");
  }

  macro "Add and Advance [5]" {
      if (nSlices==1)
          exit("This macro requires a stack");
      roiManager("add");
      run("Next Slice [>]");
  }

  macro "Fill..." {
      n = roiManager("count");
      showMessageWithCancel("Fill...", "Fill all "+n+" selectons?");
      for (i=0; i<n; i++) {
         roiManager('select', i);
         // run("Clear Outside", "slice");
         run("Fill", "slice");
      }
  }

  macro "Open All" {
      dir = getDirectory("Choose a Directory ");
      list = getFileList(dir);
      for (i=0; i<list.length; i++) {
          if (endsWith(list[i], ".roi"))
              roiManager("open", dir+list[i]);
      }
  }


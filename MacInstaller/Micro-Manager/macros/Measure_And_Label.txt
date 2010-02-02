// Measure_And_Label.txt
//
// Measures, outlines and labels the current selection.
// The selection is outlined in the current foreground
// color. (For color outlines, image must be converted
// to RGB. ) Remove the "//" at the start of the 4th line
// to always outline in yellow on RGB images. Information
// about how to install macros and assign keyboard 
// shortcuts is available at 
// "http://rsb.info.nih.gov/ij/developer/macro/macros.html".

  macro "Measure and label" {
      lineWidth = 2;
      run("Measure");
      run("Line Width...", "line="+lineWidth);
      // setForegroundColor(255,255,0); // yellow
      run("Draw");
      run("Label");
  }

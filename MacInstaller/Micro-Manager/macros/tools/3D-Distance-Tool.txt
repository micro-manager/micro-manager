// This tool macro calculates the 3D distance between
// points defined by successive mouse clicks.
//
// Double-click on the tool icon to set the mark
// width or to enable label drawing. Alt-click on
// the end point to display one distance value
// per point pair and to display the same
// label on the starting and ending points.

   var x1, y1, z1
   var markSize = 0;
   var label = false;

  macro "3D Distance Tool -C669-F3333-Fbb33" {
      getCursorLoc(x2, y2, z2, flags);
      xraw=x2; yraw=y2;
      alt = flags&8!=0;
      getPixelSize(unit, pixelWidth, pixelHeight, pixelDepth);
      x2*=pixelWidth; y2*=pixelHeight; z2*=pixelDepth;
      distance = sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)+(z2-z1)*(z2-z1));
      x1=x2; y1=y2; z1=z2;
      if (unit=="pixel") unit = "pixels";
      row = nResults;
      if (row>0 && alt) row--;
      setResult("Distance ("+unit+")", row, distance);
      updateResults();
      if (markSize>0) {
          setLineWidth(markSize);
          drawLine(xraw, yraw, xraw, yraw);
      }
      if (label && nResults>0) {
          setKeyDown("none");
          makeRectangle(xraw, yraw, 1, 1);
          run("Label");
          makeRectangle(0, 0, 0, 0);
      }
  }

  macro "3D Distance Tool Options" {
      Dialog.create("3D Tool Options");
      Dialog.addNumber("Mark Size:", markSize);
      Dialog.addCheckbox("Draw Labels", label);
      Dialog.show();
      markSize = Dialog.getNumber();
      label = Dialog.getCheckbox();
  }


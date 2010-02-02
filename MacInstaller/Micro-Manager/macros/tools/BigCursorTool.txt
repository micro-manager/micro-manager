// This tool simulates a cross hair cursor that is as large as the image.
// Change the color using Edit>Options>Colors.

  var x,  y;

  macro "Big Cursor Tool -C00cL08f8L808f" {
      getCursorLoc(x, y, z, flags);
      w = getWidth(); h = getHeight();
      px = newArray(6);
      py = newArray(6);
      x2=x; y2=y;        
      while (flags&16!=0) {
          getCursorLoc(x, y, z, flags);
          if (x!=x2 || y!=y2) {
              px[0]=0; py[0]=y;
              px[1]=w; py[1]=y;
              px[2]=x; py[2]=y;
              px[3]=x; py[3]=0;
              px[4]=x; py[4]=h;
              px[5]=x; py[5]=y;
              makeSelection("polgon", px, py);
              showStatus(x+","+y);
          }
          x2=x; y2=y;
          wait(10);
      };
  }

  macro "Display Coordinates" {
      showMessage("X Coordinate: "+x + "\nY Coordinate: "+y);
  }

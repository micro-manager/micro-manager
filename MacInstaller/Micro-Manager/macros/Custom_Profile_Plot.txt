// Custom Profile Plot
//
// Draws a line profile using getPixel() to get the pixels
// values along the line.

  getLine(x1, y1, x2, y2, width);
  if (x1==-1)
      exit("Line selection required");
  values = getLineValues(x1, y1, x2, y2);
  Plot.create("Profile Plot", "X", "Values", values);

  function getLineValues(x1, y1, x2, y2) {
      dx = x2-x1;
      dy = y2-y1;
      n = round(sqrt(dx*dx + dy*dy));
      xinc = dx/n;
      yinc = dy/n;
      n++;
      values = newArray(n);
       i = 0;
       do {
          values[i++] = getPixel(x1,y1);
          x1 += xinc;
          y1 += yinc;
      } while (i<n);
      return values;
  }

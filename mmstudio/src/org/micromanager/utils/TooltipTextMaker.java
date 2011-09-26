package org.micromanager.utils;

import javax.swing.JComponent;


public class TooltipTextMaker 
{
	private static final int numCharsPerLine = 80;
	
	// Adds html tags into string for display in tooltips
	public static String addHTMLBreaksForTooltip(String text)
	{
		int numLines = text.length() / numCharsPerLine;

		StringBuffer result = new StringBuffer("<html>");
		for (int i = 0; i < numLines; i++)
		{
			int estimatedBreakIndex = (i+1)*numCharsPerLine - 1 ;
			int actualBreakIndex = estimatedBreakIndex;
			while( text.charAt(actualBreakIndex) != ' ' )
				actualBreakIndex--;
			result.append(text.substring(i*numCharsPerLine, actualBreakIndex)+
					"<br>" + text.substring(actualBreakIndex, estimatedBreakIndex+1).trim() );
		}
		result.append(text.substring(numLines*numCharsPerLine));
		result.append("</html>");

		return result.toString();
	}

}

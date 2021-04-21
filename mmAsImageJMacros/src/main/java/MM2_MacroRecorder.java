import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.UIMonitor;

import ij.IJ;
import ij.plugin.PlugIn;
import ij.plugin.frame.Editor;
import ij.util.Tools;

public class MM2_MacroRecorder implements PlugIn {
  @Override
  public void run(String arg) {
    SwingUtilities.invokeLater(
        new Runnable() {

          @Override
          public void run() {
            MM2_Recorder mmrec = new MM2_Recorder();
          }
        });
  }

  public class MM2_Recorder {

    private final JFrame frame;
    private final JPanel mainPanel;
    private final JPanel buttonPanel = new JPanel();
    private boolean recording = false;
    private boolean originalLogMode = false;
    private boolean groupOpened = false;
    private ZonedDateTime recordingStart, lineInstant;
    private String recorded = "";

    public MM2_Recorder() {
      frame = new JFrame("MM2 Macro Recorder");
      mainPanel = new JPanel(new GridLayout(1, 1));
      final JButton recButton = new JButton("Press to record");
      buttonPanel.add(recButton);
      mainPanel.add(buttonPanel);
      frame.add(mainPanel, BorderLayout.CENTER);
      MMStudio studio = MMStudio.getInstance();

      recButton.addActionListener(
          new ActionListener() {
            @SuppressWarnings("deprecation")
            @Override
            public void actionPerformed(ActionEvent e) {
              recording = !recording;
              if (recording) {
                recButton.setText("Press to stop");
                recorded = "run('MM2 MacroExtensions');\n";
                // enable debug logging
                originalLogMode = studio.core().debugLogEnabled();
                studio.core().enableDebugLog(true);
                UIMonitor.enable(true);
                recordingStart = ZonedDateTime.now(ZoneId.systemDefault());

              } else {
                recButton.setText("Press to record");
                String fullLog = IJ.openAsString(studio.core().getPrimaryLogFile());
                String[] logLines = Tools.split(fullLog, "\n");

                OffsetDateTime odt = OffsetDateTime.now();
                ZoneOffset zoneOffset = odt.getOffset();

                for (String l : logLines) {
                  if (l.startsWith("2")) {
                    String[] items = Tools.split(l, " ");

                    lineInstant = ZonedDateTime.parse(items[0] + zoneOffset.toString());
                  }
                  if (lineInstant.isAfter(recordingStart)) {
                    translateLine(l);
                  }
                }
                Editor ed = (Editor) IJ.runPlugIn("ij.plugin.frame.Editor", "");
                ed.createMacro("RecordedMM.ijm", recorded);

                studio.core().enableDebugLog(originalLogMode);
                UIMonitor.enable(false);
              }
            }
          });
      frame.pack();
      frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      frame.setVisible(true);
    }

    public void addLine(String s) {
      recorded = recorded + s + "\n";
    }

    public void translateLine(String l) {

      if (l.contains("Did snap image from current camera")) {
        // the snap image case
        // 2019-08-22T10:16:42.545600 tid0x70000114a000 [dbg,Core] Did snap image from current
        // camera
        addLine("Ext.snap();");
      } else if (l.contains("Did set camera Camera exposure to")) {
        // the exposure case
        // 2019-08-22T10:15:45.098246 tid0x700001d77000 [dbg,Core] Did set camera Camera exposure to
        // 100.000 ms
        String[] p = Tools.split(l);
        String exp = p[p.length - 2];
        addLine("Ext.setExposure(" + exp + ");");
      } else if (l.contains("will apply preset")) {
        // the set group preset case
        // 2019-08-22T10:15:45.088301 tid0x700001d77000 [dbg,Core] Config group Channel: will apply
        // preset FITC
        String[] p = Tools.split(l, " ,:");
        addLine("Ext.setConfig('" + p[p.length - 5] + "','" + p[p.length - 1] + "');");
        groupOpened = true;
      } else if (l.contains("did apply preset")) {
        groupOpened = false;
      } else if (l.contains("Did set property")) {
        // the set device property case
        // 2019-08-22T10:15:45.099798 tid0x700001d77000 [dbg,Core:dev:Camera] Did set property
        // "Mode" to "Noise"
        String[] p = Tools.split(l, ":]\"");
        addLine(
            (groupOpened ? " // " : "")
                + "Ext.setDeviceProperty('"
                + p[4]
                + "','"
                + p[6]
                + "','"
                + p[8]
                + "');");

      } else if (l.contains("Will start relative move ")) {
        // the set device property case
        // 2019-08-22T10:16:42.394513 tid0x70000114a000 [dbg,Core] Will start relative move of Z by
        // offset 0.00000 um
        // 2019-08-22T10:16:42.394617 tid0x70000114a000 [dbg,Core] Will start relative move of XY by
        // (10.000, 15.000) um
        String dz = "0";
        String dx = "0";
        String dy = "0";

        if (l.contains("Z")) {
          String[] p = Tools.split(l, " ");
          dz = p[p.length - 2];
        } else if (l.contains("XY")) {
          String[] p = Tools.split(l, " (,)");
          dx = p[p.length - 3];
          dy = p[p.length - 2];
        }
        addLine("Ext.moveRelativeXYZ(" + dx + "," + dy + "," + dz + ");");
      } else if (l.contains("Will start absolute move ")) {
        // the set device property case
        // 2019-08-22T10:16:42.404090 tid0x70000114a000 [dbg,Core] Will start absolute move of Z to
        // position 0.00000 um
        // 2019-08-22T10:16:42.404151 tid0x70000114a000 [dbg,Core] Will start absolute move of XY to
        // position (0.000, 0.000) um
        String dz = "0";
        String dx = "0";
        String dy = "0";
        if (l.contains("Z")) {
          String[] p = Tools.split(l, " ");
          dz = p[p.length - 2];
        } else if (l.contains("XY")) {
          String[] p = Tools.split(l, " (,)");
          dx = p[p.length - 3];
          dy = p[p.length - 2];
        }
        addLine("Ext.moveAbsoluteXYZ(" + dx + "," + dy + "," + dz + ");");
      }
    }
  }
}

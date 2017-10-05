
package org.micromanager.display.inspector.internal.panels.intensity;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.miginfocom.layout.AC;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.data.NewImageEvent;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplaySettingsChangedEvent;
import org.micromanager.display.inspector.AbstractInspectorPanelController;
import org.micromanager.display.inspector.internal.panels.intensity.ImageStatsPublisher.ImageStatsChangedEvent;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.display.internal.imagestats.ImageStats;
import org.micromanager.internal.utils.CoalescentEDTRunnablePool;
import org.micromanager.internal.utils.CoalescentEDTRunnablePool.CoalescentRunnable;
import org.micromanager.internal.utils.ColorPalettes;
import org.micromanager.internal.utils.MustCallOnEDT;

/**
 *
 * @author mark
 */
public class IntensityInspectorPanelController
      extends AbstractInspectorPanelController
{
   private final JPanel panel_ = new JPanel();

   private final JPopupMenu gearMenu_ = new JPopupMenu();
   private final JMenu gearMenuPaletteSubMenu_ =
         new JMenu("Channel Color Palette");
   private final JMenuItem gearMenuColorblindFriendlyItem_ =
         new JMenuItem("Colorblind-friendly");
   private final JMenuItem gearMenuRGBCMYWItem_ =
         new JMenuItem("RGBCMYW");
   private final JMenuItem gearMenuCustomColorItem_ =
         new JMenuItem("Custom");
   private final JMenu gearMenuUpdateRateSubMenu_ =
         new JMenu("Histogram Update Rate");
   private final JMenuItem gearMenuHistogramEveryImageItem_ =
         new JMenuItem("Every Displayed Image");
   private final JMenuItem gearMenuHistogram5HzItem_ =
         new JMenuItem("5 Hz");
   private final JMenuItem gearMenuHistogram2HzItem_ =
         new JMenuItem("2 Hz");
   private final JMenuItem gearMenuHistogram1HzItem_ =
         new JMenuItem("1 Hz");
   private final JMenuItem gearMenuHistogramHalfHzItem_ =
         new JMenuItem("0.5 Hz");
   private final JMenuItem gearMenuHistogramNeverItem_ =
         new JMenuItem("Never");
   private final JCheckBoxMenuItem gearMenuLogYAxisItem_ =
         new JCheckBoxMenuItem("Logarithmic Y Axis");
   private final JCheckBoxMenuItem gearMenuUseROIItem_ =
         new JCheckBoxMenuItem("Use ROI for Histograms and Autostretch");

   private final JPanel generalControlPanel_ = new JPanel();
   private final JComboBox colorModeComboBox_ = new JComboBox();
   private final JCheckBox autostretchCheckBox_ = new JCheckBox();
   private final JSpinner percentileSpinner_ = new JSpinner();

   private final JPanel channelHistogramsPanel_ = new JPanel();

   private final List<ChannelIntensityController> channelControllers_ =
         new ArrayList<ChannelIntensityController>();

   private DataViewer viewer_;
   
   private List<Color> customPalette_;

   private final CoalescentEDTRunnablePool runnablePool_ =
         CoalescentEDTRunnablePool.create();

   public static IntensityInspectorPanelController create() {
      return new IntensityInspectorPanelController();
   }

   private IntensityInspectorPanelController() {
      setUpGearMenu();
      setUpGeneralControlPanel();
      setUpChannelHistogramsPanel(0);

      panel_.setLayout(new MigLayout(
            new LC().flowY().fill().insets("0").gridGap("0", "0")));
      panel_.add(generalControlPanel_, new CC().pushX());
      panel_.add(channelHistogramsPanel_, new CC().grow().push());
   }

   private void setUpGearMenu() {
      gearMenu_.add(gearMenuLogYAxisItem_);
      gearMenu_.add(gearMenuPaletteSubMenu_);
      gearMenuPaletteSubMenu_.add(gearMenuColorblindFriendlyItem_);
      gearMenuPaletteSubMenu_.add(gearMenuRGBCMYWItem_);
      gearMenuPaletteSubMenu_.add(gearMenuCustomColorItem_);
      gearMenu_.add(gearMenuUpdateRateSubMenu_);
      gearMenuUpdateRateSubMenu_.add(gearMenuHistogramEveryImageItem_);
      gearMenuUpdateRateSubMenu_.add(gearMenuHistogram5HzItem_);
      gearMenuUpdateRateSubMenu_.add(gearMenuHistogram2HzItem_);
      gearMenuUpdateRateSubMenu_.add(gearMenuHistogram1HzItem_);
      gearMenuUpdateRateSubMenu_.add(gearMenuHistogramHalfHzItem_);
      gearMenuUpdateRateSubMenu_.add(gearMenuHistogramNeverItem_);
      gearMenu_.add(gearMenuUseROIItem_);

      gearMenuRGBCMYWItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleColorPalette(ColorPalettes.getPrimaryColorPalette());
         }
      });
      gearMenuColorblindFriendlyItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleColorPalette(ColorPalettes.getColorblindFriendlyPalette());
         }
      });
      gearMenuCustomColorItem_.setEnabled(false);
      gearMenuCustomColorItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (customPalette_ != null) {
               handleColorPalette(customPalette_);
            }
         }      
      });
      gearMenuHistogramEveryImageItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleHistogramUpdateRate(Double.POSITIVE_INFINITY);
         }
      });
      gearMenuHistogram5HzItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleHistogramUpdateRate(5.0);
         }
      });
      gearMenuHistogram2HzItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleHistogramUpdateRate(2.0);
         }
      });
      gearMenuHistogram1HzItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleHistogramUpdateRate(1.0);
         }
      });
      gearMenuHistogramHalfHzItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleHistogramUpdateRate(0.5);
         }
      });
      gearMenuHistogramNeverItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleHistogramUpdateRate(0.0);
         }
      });
      gearMenuLogYAxisItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleHistogramLogYAxis(gearMenuLogYAxisItem_.isSelected());
         }
      });
      gearMenuUseROIItem_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleHistogramUseROI(gearMenuUseROIItem_.isSelected());
         }
      });
   }

   private void setUpGeneralControlPanel() {
      ColorModeCell cell = ColorModeCell.create();
      colorModeComboBox_.setRenderer(cell);
      colorModeComboBox_.addItem(ColorModeCell.Item.COMPOSITE);
      colorModeComboBox_.addItem(ColorModeCell.Item.COLOR);
      colorModeComboBox_.addItem(ColorModeCell.Item.GRAYSCALE);
      colorModeComboBox_.addItem(ColorModeCell.Item.HILIGHT_SAT);
      colorModeComboBox_.addItem(ColorModeCell.Item.FIRE_LUT);
      colorModeComboBox_.addItem(ColorModeCell.Item.RED_HOT_LUT);
      // Prevent "Composite" from slowly flashing
      colorModeComboBox_.getModel().setSelectedItem(null);
      colorModeComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleColorMode();
         }
      });

      autostretchCheckBox_.setText("Autostretch");
      autostretchCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleAutostretch();
         }
      });
      percentileSpinner_.setModel(
            new SpinnerNumberModel(0.0, 0.0, 49.9, 0.1));
      percentileSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            handleAutostretch();
         }
      });

      generalControlPanel_.setLayout(new MigLayout(
            new LC().fill().insets("0").gridGap("0", "0")));

      generalControlPanel_.add(new JLabel("Color Mode:"),
            new CC().gapBefore("rel").split(2));
      generalControlPanel_.add(colorModeComboBox_,
            new CC().gapAfter("push").wrap());

      generalControlPanel_.add(autostretchCheckBox_,
            new CC().split(4).gapAfter("related"));
      generalControlPanel_.add(new JLabel("(Ignore"), new CC().gapAfter("0"));
      generalControlPanel_.add(percentileSpinner_, new CC().width("72lp!").gapAfter("0"));
      generalControlPanel_.add(new JLabel(" %)"), new CC().gapAfter("push"));
   }

   private void setUpChannelHistogramsPanel(int numChannels) {
      fireInspectorPanelWillChangeHeight();

      for (ChannelIntensityController c : channelControllers_) {
         c.detach();
      }
      channelControllers_.clear();
      channelHistogramsPanel_.removeAll();

      // Create 2 columns and 2N rows (N channels and N separators)
      AC rowConstraints = new AC();
      for (int i = 0; i < numChannels; ++i) {
         // The rows for the separators need to have their height fixed, since
         // separators can have min < pref < max.
         rowConstraints = rowConstraints.size("pref:pref:pref").gap();
         rowConstraints = rowConstraints.grow().gap();
      }
      rowConstraints = rowConstraints.size("4:4:4"); // After last channel
      channelHistogramsPanel_.setLayout(new MigLayout(
            new LC().fill().insets("0").gridGap("0", "0"),
            new AC().align("left").gap().grow(),
            rowConstraints));

      for (int i = 0; i < numChannels; ++i) {
         channelHistogramsPanel_.add(new JSeparator(JSeparator.HORIZONTAL),
               new CC().span(2).growX().wrap());

         ChannelIntensityController chanController =
               ChannelIntensityController.create(viewer_, i);
         channelControllers_.add(chanController);
         channelHistogramsPanel_.add(chanController.getChannelPanel(),
               new CC().growY());
         channelHistogramsPanel_.add(chanController.getHistogramPanel(),
               new CC().grow().wrap());
      }

      fireInspectorPanelDidChangeHeight();
   }

   private void handleHistogramLogYAxis(boolean logarithmic) {
      for (ChannelIntensityController ch : channelControllers_) {
         ch.setHistogramLogYAxis(logarithmic);
      }
   }

   private void handleColorPalette(List<Color> colors) {
      DisplaySettings displaySettings = viewer_.getDisplaySettings();
      List<ChannelDisplaySettings> allChannelSettings = displaySettings.getAllChannelSettings();
      for (int i = 0; i < allChannelSettings.size(); i++) {
         displaySettings = displaySettings.
                 copyBuilderWithChannelSettings(i,
                         allChannelSettings.get(i).copyBuilder().color(colors.get(i)).build()).
                 build();
      }
      viewer_.setDisplaySettings(displaySettings);
   }

   private void handleHistogramUpdateRate(double hz) {
      // TODO Store this in prefs and subscribe to changes
      // Also, need to either hide menu items for non-DisplayWindow, or else
      // add a standard interface

      if (viewer_ instanceof DisplayController) {
         ((DisplayController) viewer_).setStatsComputeRateHz(hz);
      }
      for (ChannelIntensityController ch : channelControllers_) {
         ch.setHistogramOverlayText(hz == 0.0 ? "UPDATE DISABLED" : null);
      }
   }

   private void handleHistogramUseROI(boolean useROI) {
      DisplaySettings oldSettings, newSettings;
      do {
         oldSettings = viewer_.getDisplaySettings();
         if (oldSettings.isROIAutoscaleEnabled() == useROI) {
            return;
         }
         newSettings = oldSettings.copyBuilder().roiAutoscale(useROI).build();
      } while (!viewer_.compareAndSetDisplaySettings(oldSettings, newSettings));
   }

   @MustCallOnEDT
   private void handleColorMode() {
      ColorModeCell.Item item = (ColorModeCell.Item) colorModeComboBox_.getSelectedItem();
      DisplaySettings.ColorMode mode;
      switch (item) {
         case COMPOSITE:
            mode = DisplaySettings.ColorMode.COMPOSITE;
            break;
         case COLOR:
            mode = DisplaySettings.ColorMode.COLOR;
            break;
         case GRAYSCALE:
            mode = DisplaySettings.ColorMode.GRAYSCALE;
            break;
         case HILIGHT_SAT:
            mode = DisplaySettings.ColorMode.HIGHLIGHT_LIMITS;
            break;
         case FIRE_LUT:
            mode = DisplaySettings.ColorMode.FIRE;
            break;
         case RED_HOT_LUT:
            mode = DisplaySettings.ColorMode.RED_HOT;
            break;
         default:
            throw new AssertionError(item.name());
      }

      DisplaySettings oldSettings, newSettings;
      do {
         oldSettings = viewer_.getDisplaySettings();
         if (oldSettings.getColorMode() == mode) {
            return;
         }
         newSettings = oldSettings.copyBuilder().colorMode(mode).build();
      } while (!viewer_.compareAndSetDisplaySettings(oldSettings, newSettings));
   }

   @MustCallOnEDT
   private void handleAutostretch() {
      boolean enabled = autostretchCheckBox_.isSelected();
      double percentile = (Double) percentileSpinner_.getValue();
      DisplaySettings oldSettings, newSettings;
      boolean needToSetFixedMinMaxToAutoscaled = false;
      do {
         oldSettings = viewer_.getDisplaySettings();
         needToSetFixedMinMaxToAutoscaled = !enabled &&
               oldSettings.isAutostretchEnabled();
         newSettings = oldSettings.copyBuilder().
               autostretch(enabled).
               autoscaleIgnoredPercentile(percentile).
               build();
      } while (!viewer_.compareAndSetDisplaySettings(oldSettings, newSettings));
      if (needToSetFixedMinMaxToAutoscaled) {
         // When autostretch is turned off, rather than snapping back to
         // whatever the range was before autostretch was enabled, we want to
         // keep the current actual range. That can be accomplished by the
         // equivalent of clicking on Auto Once for each channel.
         for (ChannelIntensityController ch : channelControllers_) {
            ch.handleAutoscale();
         }
      }
   }

   @MustCallOnEDT
   private void updateImageStats(final ImagesAndStats stats) {
      runnablePool_.invokeAsLateAsPossibleWithCoalescence(new CoalescentRunnable() {
         @Override
         public Class<?> getCoalescenceClass() {
            return getClass();
         }

         @Override
         public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
            return later;
         }

         @Override
         public void run() {
            for (int i = 0; i < channelControllers_.size(); ++i) {
               if (stats == null) {
                  channelControllers_.get(i).setStats(null);
                  continue;
               }
               for (ImageStats channelStats : stats.getResult()) {
                  int channel = stats.getRequest().
                        getImage(channelStats.getIndex()).
                        getCoords().getChannel();
                  if (channel == i) {
                     channelControllers_.get(i).setStats(channelStats);
                     break;
                  }
               }
            }
         }
      });
   }

   @Override
   public String getTitle() {
      return "Histograms and Intensity Scaling";
   }

   @Override
   public JPanel getPanel() {
      return panel_;
   }

   @Override
   public JPopupMenu getGearMenu() {
      return gearMenu_;
   }

   @Override
   @MustCallOnEDT
   public void attachDataViewer(DataViewer viewer) {
      Preconditions.checkNotNull(viewer);
      if (!(viewer instanceof ImageStatsPublisher)) {
         throw new IllegalArgumentException("Programming error");
      }
      detachDataViewer();
      viewer_ = viewer;
      viewer.registerForEvents(this);
      viewer.getDataProvider().registerForEvents(this);
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            if (viewer_ == null) {
               return;
            }
            setUpChannelHistogramsPanel(
                  viewer_.getDataProvider().getAxisLength(Coords.CHANNEL));
            newDisplaySettings(viewer_.getDisplaySettings());
            updateImageStats(((ImageStatsPublisher) viewer_).getCurrentImagesAndStats());
         }
      });
   }

   @Override
   @MustCallOnEDT
   public void detachDataViewer() {
      if (viewer_ == null) {
         return;
      }
      viewer_.getDataProvider().unregisterForEvents(this);
      viewer_.unregisterForEvents(this);
      setUpChannelHistogramsPanel(0);
      viewer_ = null;
   }

   @Override
   public boolean isVerticallyResizableByUser() {
      return true;
   }

   @Subscribe
   @MustCallOnEDT
   public void onEvent(ImageStatsChangedEvent e) {
      updateImageStats(e.getImagesAndStats());
   }

   @MustCallOnEDT
   private void newDisplaySettings(DisplaySettings settings) {
      gearMenuUseROIItem_.setSelected(settings.isROIAutoscaleEnabled());

      // TODO Disable color mode and show RGB if image is RGB
      switch (settings.getColorMode()) {
         case COLOR:
            colorModeComboBox_.setSelectedItem(ColorModeCell.Item.COLOR);
            break;
         case COMPOSITE:
            colorModeComboBox_.setSelectedItem(ColorModeCell.Item.COMPOSITE);
            break;
         case HIGHLIGHT_LIMITS:
            colorModeComboBox_.setSelectedItem(ColorModeCell.Item.HILIGHT_SAT);
            break;
         case FIRE:
            colorModeComboBox_.setSelectedItem(ColorModeCell.Item.FIRE_LUT);
            break;
         case RED_HOT:
            colorModeComboBox_.setSelectedItem(ColorModeCell.Item.RED_HOT_LUT);
            break;
         case GRAYSCALE:
         default: // Use grayscale for unknown mode
            colorModeComboBox_.setSelectedItem(ColorModeCell.Item.GRAYSCALE);
            break;
      }
      ((ColorModeCell) colorModeComboBox_.getRenderer()).
            setChannelColors(settings.getAllChannelColors());
      colorModeComboBox_.repaint();

      autostretchCheckBox_.setSelected(settings.isAutostretchEnabled());

      // A spinner's change listener, unlike an action listener, gets notified
      // upon programmatic changes (which we don't want).
      ChangeListener[] saveListeners = percentileSpinner_.getChangeListeners();
      for (ChangeListener listener : saveListeners) {
         percentileSpinner_.removeChangeListener(listener);
      }
      percentileSpinner_.setValue(settings.getAutoscaleIgnoredPercentile());
      for (ChangeListener listener : saveListeners) {
         percentileSpinner_.addChangeListener(listener);
      }
      
      List<Color> allChannelColors = settings.getAllChannelColors();
      if (! (ColorPalettes.getColorblindFriendlyPalette().containsAll(allChannelColors) || 
              ColorPalettes.getPrimaryColorPalette().containsAll(allChannelColors)) ) {
         customPalette_ = allChannelColors;
         gearMenuCustomColorItem_.setEnabled(true);
      }

      for (int ch = 0; ch < channelControllers_.size(); ++ch) {
         channelControllers_.get(ch).newDisplaySettings(settings);
      }
   }

   @Subscribe
   public void onEvent(DisplaySettingsChangedEvent e) {
      final DisplaySettings settings = e.getDisplaySettings();
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            newDisplaySettings(settings);
         }
      });
   }

   @Subscribe
   public void onEvent(NewImageEvent event) {
      final int channel = event.getImage().getCoords().getChannel();
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            if (channel >= channelControllers_.size()) {
               setUpChannelHistogramsPanel(channel + 1);
            }
         }
      });
   }
}
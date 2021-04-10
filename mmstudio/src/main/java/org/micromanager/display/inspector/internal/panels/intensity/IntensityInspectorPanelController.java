
package org.micromanager.display.inspector.internal.panels.intensity;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import net.miginfocom.layout.AC;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
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
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author mark
 */
public class IntensityInspectorPanelController
      extends AbstractInspectorPanelController
{
   public static final String HISTOGRAM_UPDATE_FREQUENCY = "HistogramUpdateFrequency";
   public static final String COLOR_PALETTE = "ColorPalette";
   private static final String COLOR_BLIND_FRIENDLY = "Colorblind-friendly";
   private static final String RGBCMYW = "RGBCMYW";
   private static final String CUSTOM = "Custom";
   
   private final Studio studio_;
   private final JPanel panel_ = new JPanel();
   
   private static boolean expanded_ = true;

   private final JPopupMenu gearMenu_ = new JPopupMenu();
   private final JMenu gearMenuPaletteSubMenu_ =
         new JMenu("Channel Color Palette");
   private static class colorMenuItem {
      private final JCheckBoxMenuItem checkBox_;
      private final List<Color> colorPalette_;
      public colorMenuItem(JCheckBoxMenuItem checkBox, List<Color> colorPalette) {
         checkBox_ = checkBox;
         colorPalette_ = colorPalette;        
      }
      public JCheckBoxMenuItem getCheckBox() { return checkBox_;}
      public List<Color> getColorPalette() { return colorPalette_;}
   }
   private final Map<String, colorMenuItem> colorMenuMap_ = 
           new LinkedHashMap<>(3);
   private final JMenu gearMenuUpdateRateSubMenu_ =
         new JMenu("Histogram Update Rate");
   private final Map<String, Double> histogramMenuMap_ = 
           new LinkedHashMap<>(6);
   private final JCheckBoxMenuItem gearMenuLogYAxisItem_ =
         new JCheckBoxMenuItem("Logarithmic Y Axis");
   private final JCheckBoxMenuItem gearMenuUseROIItem_ =
         new JCheckBoxMenuItem("Use ROI for Histograms and Autostretch");

   private final JPanel generalControlPanel_ = new JPanel();
   private final JComboBox<ColorModeCell.Item> colorModeComboBox_ = new JComboBox<>();
   private final JCheckBox autostretchCheckBox_ = new JCheckBox();
   private final JSpinner percentileSpinner_ = new JSpinner();
   private final AtomicBoolean changingSpinner_ = new AtomicBoolean(false);
   private Boolean displaySettingsUpdateSuspended_ = false;
   private final JPanel channelHistogramsPanel_ = new JPanel();

   private final List<ChannelIntensityController> channelControllers_ =
         new ArrayList<>();

   private DataViewer viewer_;
   
   private List<Color> customPalette_;

   private final CoalescentEDTRunnablePool runnablePool_ =
         CoalescentEDTRunnablePool.create();

   public static IntensityInspectorPanelController create(Studio studio) {
      return new IntensityInspectorPanelController(studio);
   }

   private IntensityInspectorPanelController(Studio studio) {
      studio_ = studio;
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
      colorMenuMap_.put(COLOR_BLIND_FRIENDLY, 
              new colorMenuItem (new JCheckBoxMenuItem(COLOR_BLIND_FRIENDLY), 
                  ColorPalettes.getColorblindFriendlyPalette() ) );
      colorMenuMap_.put(RGBCMYW, 
              new colorMenuItem (new JCheckBoxMenuItem(RGBCMYW),
                  ColorPalettes.getPrimaryColorPalette()) );
      colorMenuMap_.put(CUSTOM, new colorMenuItem( 
              new JCheckBoxMenuItem(CUSTOM), customPalette_));
      // we do not have a custom palette yet, so disable the menu entry
      colorMenuMap_.get(CUSTOM).getCheckBox().setEnabled(false);
      // TODO: the viewer defaults to the colorblind friendly palette
      // get these settings from the viewer rather than default:
      colorMenuMap_.get(COLOR_BLIND_FRIENDLY).getCheckBox().setSelected(true);
      for (final String key : colorMenuMap_.keySet()) {
         colorMenuMap_.get(key).getCheckBox().addActionListener((ActionEvent e) -> {
            for (String tmpKey : colorMenuMap_.keySet()) {
               colorMenuMap_.get(tmpKey).getCheckBox().setSelected(false);
            }
            if (colorMenuMap_.get(key).getColorPalette() != null) {
               handleColorPalette(colorMenuMap_.get(key).getColorPalette());
               colorMenuMap_.get(key).getCheckBox().setSelected(true);
               // record in profile - even though we do not (yet) use this value
               studio_.profile().getSettings(
                       IntensityInspectorPanelController.class).putString(
                               COLOR_PALETTE, key);
            }
         });
         gearMenuPaletteSubMenu_.add(colorMenuMap_.get(key).getCheckBox());
      }
      
      gearMenu_.add(gearMenuUpdateRateSubMenu_);
      // Add/remove items to the histogramSubMenu here
      histogramMenuMap_.put("Every Displayed Image", Double.POSITIVE_INFINITY);
      histogramMenuMap_.put("5 Hz", 5.0);
      histogramMenuMap_.put("2 Hz", 2.0);
      histogramMenuMap_.put("1 Hz", 1.0);
      histogramMenuMap_.put("0.5 Hz", 0.5);
      histogramMenuMap_.put("Never", 0.0);
      final String defaultUpdateFrequency = studio_.profile().getSettings(
              IntensityInspectorPanelController.class).getString(HISTOGRAM_UPDATE_FREQUENCY, "Every Displayed Image");
      final List<JCheckBoxMenuItem> histogramMenuItems = new LinkedList<>();
      for (final String hKey : histogramMenuMap_.keySet()) {
         final JCheckBoxMenuItem jcmi = new JCheckBoxMenuItem(hKey);
         if (hKey.equals(defaultUpdateFrequency)) {
            jcmi.setSelected(true);
            handleHistogramUpdateRate(histogramMenuMap_.get(hKey));
         }
         histogramMenuItems.add(jcmi);
      }
      final JCheckBoxMenuItem[] hmis = histogramMenuItems.toArray(new JCheckBoxMenuItem[6]);
      for (final JCheckBoxMenuItem jbmi : histogramMenuItems) {
         jbmi.addActionListener((ActionEvent e) -> {
            for ( JCheckBoxMenuItem mi : hmis) {
               mi.setSelected(false);
            }
            handleHistogramUpdateRate(histogramMenuMap_.get(jbmi.getText()));
            jbmi.setSelected(true);
            studio_.profile().getSettings(
                    IntensityInspectorPanelController.class).putString(
                            HISTOGRAM_UPDATE_FREQUENCY, jbmi.getText());
         });
         gearMenuUpdateRateSubMenu_.add(jbmi);
      }

      gearMenu_.add(gearMenuUseROIItem_);

      gearMenuLogYAxisItem_.addActionListener((ActionEvent e) ->
              handleHistogramLogYAxis(gearMenuLogYAxisItem_.isSelected()));
      gearMenuUseROIItem_.addActionListener((ActionEvent e) ->
              handleHistogramUseROI(gearMenuUseROIItem_.isSelected()));
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
      colorModeComboBox_.addActionListener((ActionEvent e) -> handleColorMode());

      autostretchCheckBox_.setText("Autostretch");
      autostretchCheckBox_.addActionListener((ActionEvent e) -> handleAutostretch());
      percentileSpinner_.setModel(
            new SpinnerNumberModel(0.0, 0.0, 49.9, 0.1));
      percentileSpinner_.addChangeListener((ChangeEvent e) -> {
         if (!changingSpinner_.get()) {
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

         if (viewer_ != null) {
            ChannelIntensityController chanController
                    = ChannelIntensityController.create(viewer_, i);
            channelControllers_.add(chanController);
            channelHistogramsPanel_.add(chanController.getChannelPanel(),
                    new CC().growY());
            channelHistogramsPanel_.add(chanController.getHistogramPanel(),
                    new CC().grow().wrap());
         } else {
            ReportingUtils.logError("IntensityInspectorPanelController: Viewer is unexpectedly null");
         }
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
      // Need to either hide menu items for non-DisplayWindow, or else
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
      DisplaySettings.ColorMode mode = DisplaySettings.ColorMode.GRAYSCALE;
      if (item != null) {
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
      }

      DisplaySettings oldSettings, newSettings;
      do {
         if (viewer_ == null) {
            return;
         }
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
      // boolean needToSetFixedMinMaxToAutoscaled = false;
      do {
         oldSettings = viewer_.getDisplaySettings();
         //needToSetFixedMinMaxToAutoscaled = !enabled && oldSettings.isAutostretchEnabled();
         newSettings = oldSettings.copyBuilder().
               autostretch(enabled).
               autoscaleIgnoredPercentile(percentile).
               build();
      } while (!viewer_.compareAndSetDisplaySettings(oldSettings, newSettings));
      /*
      if (needToSetFixedMinMaxToAutoscaled) {
         // When autostretch is turned off, rather than snapping back to
         // whatever the range was before autostretch was enabled, we want to
         // keep the current actual range. That can be accomplished by the
         // equivalent of clicking on Auto Once for each channel.
         displaySettingsUpdateSuspended_ = true;
         for (ChannelIntensityController ch : channelControllers_) {
              ch.handleAutoscale();
         }
         displaySettingsUpdateSuspended_ = false;
      }
      */
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
                  int channel = -1;
                  // avoid index out of bounds exception
                  if (stats.getRequest().getNumberOfImages()
                          > channelStats.getIndex()) {
                     channel = stats.getRequest().
                             getImage(channelStats.getIndex()).
                             getCoords().getChannel();
                  }
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
      SwingUtilities.invokeLater(() -> {
         if (viewer_ == null) {
            return;
         }
         setUpChannelHistogramsPanel(
                 viewer_.getDataProvider().getNextIndex(Coords.CHANNEL));
         newDisplaySettings(viewer_.getDisplaySettings());
         updateImageStats(((ImageStatsPublisher) viewer_).getCurrentImagesAndStats());
         String updateRate = studio_.profile().
                 getSettings(IntensityInspectorPanelController.class).
                 getString(HISTOGRAM_UPDATE_FREQUENCY, "1 Hz");
         if (histogramMenuMap_.get(updateRate) != null) {
            handleHistogramUpdateRate(histogramMenuMap_.get(updateRate));
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
   
   
   @Override
   public void setExpanded(boolean status) {
      expanded_ = status;
   }
       
   @Override
   public boolean initiallyExpand() {
      return expanded_;
   }

   @Subscribe
   @MustCallOnEDT
   public void onEvent(ImageStatsChangedEvent e) {
      updateImageStats(e.getImagesAndStats());
   }

   @MustCallOnEDT
   private void newDisplaySettings(DisplaySettings settings) {
      if (displaySettingsUpdateSuspended_) {
         return;
      }

      // Setting the autostretch box and changeSpinner, cause their
      // action handlers to be activates, which will cause the displaysettings
      // to be changed again.  It seems that the order in which we do things
      // is important. Call the channels autoscale handlers after setting
      // the autostrechkBox and percentileSpinner
      autostretchCheckBox_.setSelected(settings.isAutostretchEnabled());

      // A spinner's change listener, unlike an action listener, gets notified
      // upon programmatic changes.  Previously, there was code here to remove
      // ChangeListeners and add them back after setting the value.  As a
      // side effect, this cause the spinner to not display its value
      // There does not seem to be a problem setting the value with the
      // ChangeListeners still attached, so do the easy thing:
      changingSpinner_.set(true);
      percentileSpinner_.setValue(settings.getAutoscaleIgnoredPercentile());
      changingSpinner_.set(false);

      if (autostretchCheckBox_.isSelected() && !settings.isAutostretchEnabled()) {
         displaySettingsUpdateSuspended_ = true;
         for (ChannelIntensityController ch : channelControllers_) {
            ch.handleAutoscale();
         }
         displaySettingsUpdateSuspended_ = false;
      }


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
     

      List<Color> allChannelColors = settings.getAllChannelColors();
      if (! (ColorPalettes.getColorblindFriendlyPalette().containsAll(allChannelColors) || 
              ColorPalettes.getPrimaryColorPalette().containsAll(allChannelColors)) ) {
         customPalette_ = allChannelColors;
         colorMenuMap_.get(CUSTOM).getCheckBox().setEnabled(true);
      }

      for (ChannelIntensityController channelIntensityController : channelControllers_) {
         channelIntensityController.newDisplaySettings(settings);
      }
   }

   @Subscribe
   public void onEvent(DisplaySettingsChangedEvent e) {
      final DisplaySettings settings = e.getDisplaySettings();
      SwingUtilities.invokeLater(() -> newDisplaySettings(settings));
   }

   @Subscribe
   public void onEvent(DataProviderHasNewImageEvent event) {
      // (NS - 2020-03-27)
      // Hack: handle only if the circular buffer is not too full.  How full is highly arbitrary!
      if ( !studio_.acquisitions().isAcquisitionRunning() || !studio_.core().isSequenceRunning() ||
              studio_.core().getRemainingImageCount() < 6 ) {
         final int channel = event.getImage().getCoords().getChannel();
         SwingUtilities.invokeLater(() -> {
            try {
               if (channel >= channelControllers_.size()) {
                  setUpChannelHistogramsPanel(channel + 1);
               }
            } catch (NullPointerException npe) {
               // it is possible that the dataprovider send a new image event
               // and immediately closed.  That will result in a null pointer
               // exception somewhere down the line.  Catch it here
               ReportingUtils.logError(npe);
            }
         });
      }
   }
}
// fix until compiles with ant build-java

package org.micromanager.plugins.uvexposure;

import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import javax.swing.JButton;

import org.micromanager.Studio;
import org.micromanager.events.LiveModeEvent;
import org.micromanager.data.internal.DefaultNewImageEvent;
import org.micromanager.display.DisplayDidShowImageEvent;

import org.micromanager.acquisition.AcquisitionStartedEvent;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.Image;
import org.micromanager.display.DataViewer;
import org.micromanager.data.DataProvider;

import java.awt.Font;
import java.awt.Color;

import javax.swing.Timer;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JFileChooser;
import java.io.FileWriter;
import java.io.IOException;

import javax.swing.*;
import java.awt.*;

public class UVExposureFrame extends JFrame {

    private Studio studio_;
    private final List<ExposureRow> exposureTable_= new ArrayList<>();
    private JTable table_;
    private DefaultTableModel tableModel_;
    private DataProvider dataProvider_;
    private Timer liveTimer_;
    private int pollIntervalMs_ = 200; 
    private long lastPollTimeMs_ = -1;
    public UVExposureFrame(Studio studio) {
        super("UV Exposure Table");
        studio_ = studio;
        studio_.events().registerForEvents(this);

        tableModel_ = new DefaultTableModel(new String[]{"X", "Y", "Filter", "Exposure (ms)"}, 0);
        
        table_ = new JTable(tableModel_);
        JScrollPane scrollPane = new JScrollPane(table_);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);

        JButton saveButton = new JButton("Save");
            saveButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Save exposure table as CSV");
                int userSelection = fileChooser.showSaveDialog(this);
                if (userSelection == JFileChooser.APPROVE_OPTION) {
                    try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile() + ".csv")) {
                        writer.append("X,Y,Filter,Exposure (ms)\n"); // CSV headers
                        
                        for (ExposureRow row : exposureTable_) {
                            writer.append(row.x + "," + row.y + "," + row.filter + "," + row.timeMs + "\n"); // CSV rows
                        }
                        
                        writer.flush();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        
        JButton clearButton = new JButton("Clear");
            clearButton.addActionListener(e -> {
                tableModel_.setRowCount(0);
                exposureTable_.clear();  
            });
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveButton);
        buttonPanel.add(clearButton);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(600, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private static class ExposureRow {
        double x;
        double y;
        String filter;
        double timeMs;

        ExposureRow(double x, double y, String filter, double timeMs){
            this.x = x;
            this.y = y;
            this.filter = filter;
            this.timeMs = timeMs;
        }
    }

    // helper function to find if row with same X and Y already exist
    private ExposureRow findMatchingRow(double x, double y, String filter) {
        for (ExposureRow row : exposureTable_) {
            if (row.filter.equals(filter) && Math.abs(row.x - x) < 1e-6 && Math.abs(row.y - y) < 1e-6) {
                return row;
            }
        }
        return null;
    }

    // helper function to round X and Y positions to nearest 100um
    private double roundToNearest100(double position) {
        return Math.round(position / 100.0) * 100.0;
    }


    // function to add exposure to exposure table
    private void addExposure(double x, double y, String filter, double timeMs) {
        ExposureRow existing = findMatchingRow(x, y, filter);

        // if row with same position and filter exists
        if (existing != null) {
            existing.timeMs += timeMs;

            int rowIndex = exposureTable_.indexOf(existing);

            SwingUtilities.invokeLater(() -> {tableModel_.setValueAt(existing.timeMs, rowIndex, 3);});

        // if row with same position and filter does not exist
        } else {
            ExposureRow newRow = new ExposureRow(x, y, filter, timeMs);

            exposureTable_.add(newRow);

            SwingUtilities.invokeLater(() -> {tableModel_.addRow(new Object[]{x, y, filter, timeMs});});
        }
    }


    @Subscribe
    public void onLiveMode(LiveModeEvent event) {
        if (!event.isOn()) {
            if (liveTimer_ != null) {
                liveTimer_.stop();
            }
            lastPollTimeMs_ = -1;
            return;
        }

        lastPollTimeMs_ = System.currentTimeMillis();

        liveTimer_ = new Timer(pollIntervalMs_, e -> pollCurrentExposure());
        liveTimer_.start();
    }

    // helper function to calculate exposure time on live mode
    private void pollCurrentExposure() {
        try {
            long now = System.currentTimeMillis();

            if (lastPollTimeMs_ < 0) {
                lastPollTimeMs_ = now;
                return;
            }

            double deltaMs = now - lastPollTimeMs_;
            lastPollTimeMs_ = now;

            double x = roundToNearest100(studio_.core().getXPosition());
            double y = roundToNearest100(studio_.core().getYPosition());
            String filter = studio_.core().getCurrentConfig("Channel");

            addExposure(x, y, filter, deltaMs);

        } catch (Exception ignored) {
        }
    }


    @Subscribe
    public void onNewAcquisition(AcquisitionStartedEvent event) {
        event.getDatastore().registerForEvents(this);
    }


    @Subscribe
    public void onNewImage(DataProviderHasNewImageEvent event) {
        if (event.getDataProvider() == null) {
            return;
        }

        Image img = event.getImage();
        if (img == null) {
            return;
        }

        double x = roundToNearest100(img.getMetadata().getXPositionUm());
        double y = roundToNearest100(img.getMetadata().getYPositionUm());
       
        int channelIndex = img.getCoords().getC();
        List<String> channelNames = event.getDataProvider().getSummaryMetadata().getChannelNameList();
        String filter = channelNames.get(channelIndex);
   
        double duration = img.getMetadata().getExposureMs();

        addExposure(x, y, filter, duration);
    }


    /*
    Attempting Snap image

    
    @Subscribe
    public void onDisplayImage(DisplayDidShowImageEvent event) {
        Image img = event.getPrimaryImage();
        if (img == null) {
            double x=1;
            double y=2;
            String filter="NADH";
            double duration = 2.3;
            addExposure(x, y, filter, duration);
            return;
        }

        double x = roundToNearest100(img.getMetadata().getXPositionUm());
        double y = roundToNearest100(img.getMetadata().getYPositionUm());

        int channelIndex = img.getCoords().getC();
        List<String> channelNames = event.getDataViewer().getDataProvider().getSummaryMetadata().getChannelNameList();
        String filter = channelNames.get(channelIndex);

        double duration = img.getMetadata().getExposureMs();

        addExposure(x, y, filter, duration);
    }

    */      

}

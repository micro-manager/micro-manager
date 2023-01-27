package org.micromanager.plugins.micromanager;

import fromScenery.LazyLoggerKt;
import fromScenery.Settings;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import kotlin.Lazy;
import kotlin.Metadata;
import kotlin.io.FilesKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

//@Metadata(
//        mv = {1, 7, 0},
//        k = 1,
//        d1 = {"\u0000d\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B+\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u0012\b\b\u0002\u0010\b\u001a\u00020\u0007¢\u0006\u0002\u0010\tJ\u0010\u0010'\u001a\u00020(2\u0006\u0010)\u001a\u00020\u0007H\u0002J\b\u0010*\u001a\u00020(H\u0002J\b\u0010+\u001a\u00020(H\u0002J\b\u0010,\u001a\u00020(H\u0002J\b\u0010-\u001a\u00020(H\u0002R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\rX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u0004¢\u0006\u0002\n\u0000R\u001b\u0010\u0013\u001a\u00020\u00148BX\u0082\u0084\u0002¢\u0006\f\n\u0004\b\u0017\u0010\u0018\u001a\u0004\b\u0015\u0010\u0016R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u001aX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\u0012X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u0012X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u001d\u001a\u00020\u001eX\u0082\u000e¢\u0006\u0002\n\u0000R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u001f\u0010 \"\u0004\b!\u0010\"R\u000e\u0010#\u001a\u00020$X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010%\u001a\u00020&X\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006."},
//        d2 = {"Lgraphics/scenery/org.micromanager.plugins.micromanager.SettingsEditor;", "", "settings", "Lgraphics/scenery/Settings;", "mainFrame", "Ljavax/swing/JFrame;", "width", "", "height", "(Lgraphics/scenery/Settings;Ljavax/swing/JFrame;II)V", "addSettingLabel", "Ljavax/swing/JLabel;", "addTextfield", "Ljavax/swing/JTextField;", "addValuefield", "lastModified", "", "loadButton", "Ljavax/swing/JButton;", "logger", "Lorg/slf4j/Logger;", "getLogger", "()Lorg/slf4j/Logger;", "logger$delegate", "Lkotlin/Lazy;", "newSettingLoaded", "", "refreshButton", "saveButton", "selectedSettingFile", "Ljava/io/File;", "getSettings", "()Lgraphics/scenery/Settings;", "setSettings", "(Lgraphics/scenery/Settings;)V", "settingsTable", "Ljavax/swing/JTable;", "tableContents", "Ljavax/swing/table/DefaultTableModel;", "changeSettingsTableAt", "", "row", "loadSettings", "refreshSettings", "saveSettings", "updateSettingsTable", "scenery"}
//)
public final class SettingsEditor {
    private final Lazy logger$delegate;
    private final JTable settingsTable;
    private DefaultTableModel tableContents;
    private final JLabel addSettingLabel;
    private final JTextField addTextfield;
    private final JTextField addValuefield;
    private final JButton saveButton;
    private final JButton loadButton;
    private File selectedSettingFile;
    private final JButton refreshButton;
    private boolean newSettingLoaded;
    private long lastModified;
    @NotNull
    private Settings settings;
    private final JFrame mainFrame;

    private final Logger getLogger() {
        Lazy var1 = this.logger$delegate;
        Object var3 = null;
        return (Logger)var1.getValue();
    }

    private final void loadSettings() {
        JFileChooser fileInspector = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Properties: ", new String[]{"properties"});
        fileInspector.setFileFilter((FileFilter)filter);
        fileInspector.setFileSelectionMode(0);
        fileInspector.setCurrentDirectory(new File((new File("")).getAbsolutePath()));
        int returnVal = fileInspector.showOpenDialog((Component)this.loadButton);
        if (returnVal == 0) {
            Settings var10000 = this.settings;
            File var10001 = fileInspector.getSelectedFile();
            Intrinsics.checkNotNullExpressionValue(var10001, "fileInspector.selectedFile");
            var10000.loadPropertiesFile(var10001);
            this.lastModified = this.selectedSettingFile.lastModified();
            this.newSettingLoaded = true;
            this.refreshSettings();
        }

    }

    private final void refreshSettings() {
        if (this.lastModified != this.selectedSettingFile.lastModified() ^ this.newSettingLoaded) {
            this.settings.loadPropertiesFile(this.selectedSettingFile);
            this.getLogger().info("loading " + this.selectedSettingFile);
            this.lastModified = this.selectedSettingFile.lastModified();
            this.updateSettingsTable();
        } else {
            this.updateSettingsTable();
        }

    }

    private final void saveSettings() {
        JFileChooser fileInspector = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Properties: ", new String[]{"properties"});
        fileInspector.setFileFilter((FileFilter)filter);
        fileInspector.setFileSelectionMode(0);
        fileInspector.setCurrentDirectory(new File((new File("")).getAbsolutePath()));
        int returnVal = fileInspector.showSaveDialog((Component)this.saveButton);
        if (returnVal == 0) {
            File path = fileInspector.getSelectedFile();
            Intrinsics.checkNotNullExpressionValue(path, "path");
            if (Intrinsics.areEqual(FilesKt.getExtension(path), "properties") ^ true) {
                path = new File(path.getAbsolutePath() + ".properties");
            }

            this.settings.saveProperties(path);
            this.selectedSettingFile = path;
        }

    }

    private final void updateSettingsTable() {
        List settingKeys = this.settings.getAllSettings();
        this.tableContents.setRowCount(0);
        this.tableContents.setColumnCount(0);
        this.tableContents.addColumn("Property");
        this.tableContents.addColumn("Value");
        Iterator var3 = settingKeys.iterator();

        while(var3.hasNext()) {
            String key = (String)var3.next();
            this.tableContents.addRow(new String[]{String.valueOf(key), String.valueOf(this.settings.get( key, (Object)null))});
        }

        this.settingsTable.getRowSorter().toggleSortOrder(0);
        this.newSettingLoaded = false;
    }

    private final void changeSettingsTableAt(int row) {
        Object setting = this.tableContents.getValueAt(row, 0);
        Object value = this.tableContents.getValueAt(row, 1);
        Object castValue = this.settings.parseType(String.valueOf(value));
        if (setting == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
        } else {
            String settingString = (String)setting;
            Object oldValue = this.settings.parseType(this.settings.getProperty(settingString) + "");
            if (Intrinsics.areEqual(castValue.getClass().getTypeName(), oldValue.getClass().getTypeName()) ^ true) {
                JOptionPane.showMessageDialog((Component)this.mainFrame, "Wrong Type! Expected " + oldValue.getClass().getTypeName() + ", inserted " + castValue.getClass().getTypeName(), "Type Error", 0);
            } else {
                this.settings.set(String.valueOf(setting), castValue);
            }
        }
    }

    @NotNull
    public final Settings getSettings() {
        return this.settings;
    }

    public final void setSettings(@NotNull Settings var1) {
        Intrinsics.checkNotNullParameter(var1, "<set-?>");
        this.settings = var1;
    }

    public SettingsEditor(@NotNull Settings settings, @NotNull JFrame mainFrame, int width, int height) {
        super();
        this.settings = settings;
        this.mainFrame = mainFrame;
        this.logger$delegate = LazyLoggerKt.LazyLogger(this, (String)null);
        this.selectedSettingFile = this.settings.getPropertiesFile();
        this.lastModified = this.selectedSettingFile.lastModified();
        this.mainFrame.setSize(new Dimension(width, height));
        this.mainFrame.setPreferredSize(new Dimension(width, height));
        this.mainFrame.setMinimumSize(new Dimension(width, height));
        this.mainFrame.setLayout((LayoutManager)(new MigLayout()));
        this.mainFrame.setVisible(true);
        this.tableContents = new DefaultTableModel();
        this.settingsTable = new JTable((TableModel)this.tableContents);
        this.settingsTable.setAutoCreateRowSorter(true);
        this.mainFrame.add((Component)(new JScrollPane((Component)this.settingsTable)), "cell 0 0 12 8");
        this.updateSettingsTable();
        this.settingsTable.putClientProperty("terminateEditOnFocusLost", true);
        this.tableContents.addTableModelListener((TableModelListener)(new TableModelListener() {
            public final void tableChanged(TableModelEvent e) {
                Intrinsics.checkNotNullExpressionValue(e, "e");
                if (e.getType() != -1 && e.getColumn() != -1) {
                    SettingsEditor.this.changeSettingsTableAt(e.getFirstRow());
                }

            }
        }));
        this.addSettingLabel = new JLabel("Add setting: ");
        this.mainFrame.add((Component)this.addSettingLabel, "cell 0 9 1 1");
        this.addTextfield = new JTextField(6);
        this.mainFrame.add((Component)this.addTextfield, "cell 1 9 1 1");
        this.addValuefield = new JTextField(6);
        this.mainFrame.add((Component)this.addValuefield, "cell 2 9 1 1");
        this.addValuefield.addKeyListener((KeyListener)(new KeyListener() {
            public void keyTyped(@NotNull KeyEvent e) {
                Intrinsics.checkNotNullParameter(e, "e");
            }

            public void keyPressed(@NotNull KeyEvent e) {
                Intrinsics.checkNotNullParameter(e, "e");
            }

            public void keyReleased(@NotNull KeyEvent e) {
                Intrinsics.checkNotNullParameter(e, "e");
                if (e.getKeyCode() == 10) {
                    Settings var10000 = SettingsEditor.this.getSettings();
                    String var10001 = SettingsEditor.this.addTextfield.getText().toString();
                    Settings var10002 = SettingsEditor.this.getSettings();
                    String var10003 = SettingsEditor.this.addValuefield.getText();
                    Intrinsics.checkNotNullExpressionValue(var10003, "addValuefield.text");
                    var10000.setIfUnset(var10001, var10002.parseType(var10003));
                    SettingsEditor.this.addTextfield.setText((String)null);
                    SettingsEditor.this.addValuefield.setText((String)null);
                    SettingsEditor.this.updateSettingsTable();
                }

            }
        }));
        this.loadButton = new JButton("Load");
        this.loadButton.setSize(50, 20);
        this.mainFrame.add((Component)this.loadButton, "cell 3 9 1 1");
        this.loadButton.addActionListener((ActionListener)(new ActionListener() {
            public final void actionPerformed(ActionEvent it) {
                SettingsEditor.this.loadSettings();
            }
        }));
        this.saveButton = new JButton("Save");
        this.saveButton.setSize(50, 20);
        this.mainFrame.add((Component)this.saveButton, "cell 4 9 1 1");
        this.saveButton.addActionListener((ActionListener)(new ActionListener() {
            public final void actionPerformed(ActionEvent it) {
                SettingsEditor.this.saveSettings();
            }
        }));
        this.refreshButton = new JButton("Refresh");
        this.refreshButton.setSize(50, 20);
        this.mainFrame.add((Component)this.refreshButton, "cell 5 9 1 1");
        this.refreshButton.addActionListener((ActionListener)(new ActionListener() {
            public final void actionPerformed(ActionEvent it) {
                SettingsEditor.this.refreshSettings();
            }
        }));
        this.mainFrame.pack();
    }

    // $FF: synthetic method
    public SettingsEditor(Settings var1, JFrame var2, int var3, int var4, int var5, DefaultConstructorMarker var6) {
        this(var1, var2, var3, var4);
        if ((var5 & 2) != 0) {
            var2 = new JFrame("org.micromanager.plugins.micromanager.SettingsEditor");
        }

        if ((var5 & 4) != 0) {
            var3 = 480;
        }

        if ((var5 & 8) != 0) {
            var4 = 500;
        }

    }
}

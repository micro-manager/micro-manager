/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal.panels.comments;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.CommentsHelper;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayDidShowImageEvent;
import org.micromanager.display.inspector.AbstractInspectorPanelController;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.io.IOException;

/** @author mark */
public final class CommentsInspectorPanelController extends AbstractInspectorPanelController
    implements DocumentListener {
  private final JPanel panel_ = new JPanel();
  private DataViewer viewer_;

  private final JTextArea summaryTextArea_;
  private final JTextArea planeTextArea_;
  private Coords editingCoords_;
  private boolean summaryCommentEdited_;
  private boolean planeCommentEdited_;
  private boolean programmaticallySettingText_ = false;
  private static boolean expanded_ = false;

  public static AbstractInspectorPanelController create() {
    CommentsInspectorPanelController instance = new CommentsInspectorPanelController();
    instance.summaryTextArea_.getDocument().addDocumentListener(instance);
    instance.planeTextArea_.getDocument().addDocumentListener(instance);
    return instance;
  }

  private CommentsInspectorPanelController() {
    panel_.setLayout(new MigLayout(new LC().insets("4").gridGap("0", "0").fill()));

    summaryTextArea_ = new JTextArea();
    configureTextArea(summaryTextArea_);
    JScrollPane summaryScrollPane =
        new JScrollPane(
            summaryTextArea_,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    JPanel summaryCommentsPanel =
        new JPanel(new MigLayout(new LC().insets("0").gridGap("0", "0").fill()));
    summaryCommentsPanel.add(new JLabel("Dataset Summary Comments:"), new CC().growX().wrap());
    summaryCommentsPanel.add(summaryScrollPane, new CC().grow().push());

    planeTextArea_ = new JTextArea();
    configureTextArea(planeTextArea_);
    JScrollPane planeScrollPane =
        new JScrollPane(
            planeTextArea_,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    JPanel planeCommentsPanel =
        new JPanel(new MigLayout(new LC().insets("0").gridGap("0", "0").fill()));
    planeCommentsPanel.add(
        new JLabel("Image Plane Comments:"), new CC().gapTop("4").growX().wrap());
    planeCommentsPanel.add(planeScrollPane, new CC().grow().push());

    JSplitPane splitPane =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, summaryCommentsPanel, planeCommentsPanel);
    splitPane.setBorder(BorderFactory.createEmptyBorder());
    splitPane.setContinuousLayout(true);
    panel_.add(splitPane, new CC().grow().push());
  }

  private static void configureTextArea(JTextArea area) {
    area.setRows(3);
    area.setColumns(40);
    area.setLineWrap(true);
    area.setTabSize(3);
    area.setWrapStyleWord(true);
  }

  @Override
  public String getTitle() {
    return "Comments";
  }

  @Override
  public void attachDataViewer(DataViewer viewer) {
    Preconditions.checkNotNull(viewer);
    Preconditions.checkState(viewer_ == null);
    viewer_ = viewer;
    viewer_.registerForEvents(this);
    boolean editable = viewer_.getDataProvider() instanceof Datastore;
    summaryTextArea_.setEnabled(editable);
    planeTextArea_.setEnabled(editable);

    Datastore store = (Datastore) viewer_.getDataProvider();
    editingCoords_ = viewer_.getDisplayPosition();
    programmaticallySettingText_ = true;
    try {
      summaryTextArea_.setText(CommentsHelper.getSummaryComment(store));
      summaryCommentEdited_ = false;
      planeTextArea_.setText(CommentsHelper.getImageComment(store, editingCoords_));
      planeCommentEdited_ = false;
    } catch (IOException e) {
      // TODO Show error
    } finally {
      programmaticallySettingText_ = false;
    }
  }

  @Override
  public void detachDataViewer() {
    if (viewer_ != null) {
      savePlaneComments();
      saveSummaryComments();
      viewer_.unregisterForEvents(this);
      viewer_ = null;
      programmaticallySettingText_ = true;
      try {
        summaryTextArea_.setText(null);
        planeTextArea_.setText(null);
      } finally {
        programmaticallySettingText_ = false;
      }
      summaryTextArea_.setEnabled(false);
      planeTextArea_.setEnabled(false);
    }
  }

  @Override
  public boolean isVerticallyResizableByUser() {
    return true;
  }

  @Override
  public JPanel getPanel() {
    return panel_;
  }

  @Override
  public void setExpanded(boolean status) {
    expanded_ = status;
  }

  @Override
  public boolean initiallyExpand() {
    return expanded_;
  }

  private void saveSummaryComments() {
    if (!summaryCommentEdited_) {
      return;
    }
    String comment = summaryTextArea_.getText();
    Datastore store = (Datastore) viewer_.getDataProvider();
    try {
      CommentsHelper.setSummaryComment(store, comment);
      summaryCommentEdited_ = false;
    } catch (IOException e) {
      // TODO XXX Show error
    }
  }

  private void savePlaneComments() {
    if (!planeCommentEdited_) {
      return;
    }
    String comment = planeTextArea_.getText();
    Datastore store = (Datastore) viewer_.getDataProvider();
    try {
      CommentsHelper.setImageComment(store, editingCoords_, comment);
      planeCommentEdited_ = false;
    } catch (IOException e) {
      // TODO XXX Show error
    }
  }

  @Subscribe
  public void onEvent(DisplayDidShowImageEvent e) {
    savePlaneComments();
    editingCoords_ = e.getPrimaryImage().getCoords();
    Datastore store = (Datastore) viewer_.getDataProvider();
    programmaticallySettingText_ = true;
    try {
      planeTextArea_.setText(CommentsHelper.getImageComment(store, editingCoords_));
    } catch (IOException ex) {
      // TODO Show error
    } finally {
      programmaticallySettingText_ = false;
    }
    planeCommentEdited_ = false;
  }

  @Override // DocumentListener
  public void insertUpdate(DocumentEvent e) {
    if (programmaticallySettingText_) {
      return;
    }
    markEdited(e.getDocument());
  }

  @Override // DocumentListener
  public void removeUpdate(DocumentEvent e) {
    if (programmaticallySettingText_) {
      return;
    }
    markEdited(e.getDocument());
  }

  @Override // DocumentListener
  public void changedUpdate(DocumentEvent e) {
    if (programmaticallySettingText_) {
      return;
    }
    markEdited(e.getDocument());
  }

  private void markEdited(Document doc) {
    if (doc == summaryTextArea_.getDocument()) {
      summaryCommentEdited_ = true;
    } else {
      planeCommentEdited_ = true;
    }
  }
}

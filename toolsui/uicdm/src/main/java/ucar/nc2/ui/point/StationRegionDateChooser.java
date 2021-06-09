/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.ui.point;

import thredds.client.catalog.DateType;
import thredds.client.catalog.TimeCoverage;
import thredds.client.catalog.TimeDuration;
import ucar.nc2.units.TimeUnit;
import ucar.ui.event.ActionSourceListener;
import ucar.ui.event.ActionValueEvent;
import ucar.ui.event.ActionValueListener;
import ucar.nc2.ui.geoloc.*;
import ucar.nc2.ui.util.Renderer;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.IndependentDialog;
import ucar.ui.widget.PopupManager;
import ucar.nc2.ui.widget.RangeDateSelector;
import ucar.unidata.geoloc.LatLonPoints;
import ucar.unidata.geoloc.LatLonRect;
import ucar.unidata.geoloc.ProjectionRect;
import ucar.unidata.geoloc.Station;
import ucar.ui.prefs.Field;
import ucar.ui.prefs.PrefPanel;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * A Swing widget for THREDDS clients to choose a station and/or a region from navigatable map.
 * <p/>
 * Typically a user listens for property change events:
 * 
 * <pre>
 *   stationRegionDateChooser.addPropertyChangeListener( new PropertyChangeListener() {
 * public void propertyChange(java.beans.PropertyChangeEvent e) {
 * if (e.getPropertyName().equals("Station")) {
 * selectedStation = (Station) e.getNewValue();
 * ...
 * }
 * else if (e.getPropertyName().equals("GeoRegion")) {
 * geoRegion = (ProjectionRect) e.getNewValue();
 * ...
 * }
 * }
 * });
 * </pre>
 *
 * @author caron
 * @version $Revision: 50 $ $Date: 2006-07-12 16:30:06Z $
 */

/*
 * implementation note:
 * do we want to remove actionSource ? we have setSelectedStation instead.
 */
public class StationRegionDateChooser extends NPController {
  private final boolean regionSelect, stationSelect, dateSelect;

  // station
  private StationRenderer stnRender;
  private Station selectedStation;

  // region
  private ProjectionRect geoBounds;
  private ProjectionRect geoSelection = new ProjectionRect();
  private boolean geoSelectionMode;
  private final Color outlineColor = Color.black;

  // date
  private RangeDateSelector dateSelector;
  private IndependentDialog dateWindow;
  private AbstractAction dateAction;

  // prefs
  private PrefPanel minmaxPP;
  private Field.Double minLonField, maxLonField, minLatField, maxLatField;

  // events
  private ActionSourceListener actionSource;

  // local caches
  private final PopupManager popupInfo = new PopupManager("Station Info");

  // debugging
  private boolean debugEvent;

  /**
   * Default Contructor, allow both region and station selection.
   */
  public StationRegionDateChooser() {
    this(true, true, true);
  }

  /**
   * Constructor
   *
   * @param regionSelect allow selecting a region
   * @param stationSelect allow selecting a station
   * @param dateSelect allow selecting a date range
   */
  public StationRegionDateChooser(boolean stationSelect, boolean regionSelect, boolean dateSelect) {

    this.regionSelect = regionSelect;
    this.stationSelect = stationSelect;
    this.dateSelect = dateSelect;

    np.setGeoSelectionMode(regionSelect && geoSelectionMode);
    // setGeoBounds( np.getMapArea());

    if (stationSelect) {
      stnRender = new StationRenderer();
      addRenderer(stnRender);

      // get Pick events from the navigated panel: mouse click
      np.addPickEventListener(e -> {
        selectedStation = stnRender.pick(e.getLocationPoint());
        if (selectedStation != null) {
          redraw();
          firePropertyChangeEvent(selectedStation, "Station");
          actionSource.fireActionValueEvent(ActionSourceListener.SELECTED, selectedStation);
        }
      });


      // get mouse motion events
      np.addMouseMotionListener(new MouseMotionAdapter() {
        public void mouseMoved(MouseEvent e) {
          Point p = e.getPoint();
          StationRenderer.StationUI sui = stnRender.isOnStation(p);
          StringBuilder sbuff = new StringBuilder();
          if (sui != null) {
            ucar.unidata.geoloc.Station s = sui.getStation();
            sbuff.append(s.getName());
            sbuff.append(" ");
            sbuff.append("\n");
            if (null != s.getDescription())
              sbuff.append(s.getDescription()).append("\n");
            sbuff.append(LatLonPoints.latToString(s.getLatitude(), 4));
            sbuff.append(" ");
            sbuff.append(LatLonPoints.lonToString(s.getLongitude(), 4));
            sbuff.append(" ");
            double alt = s.getAltitude();
            if (!Double.isNaN(alt)) {
              sbuff.append(ucar.unidata.util.Format.d(alt, 0));
              sbuff.append(" m");
            }

            popupInfo.show(sbuff.toString(), p, StationRegionDateChooser.this, s);
          } else
            popupInfo.hide();
        }
      });

      // get mouse exit events
      np.addMouseListener(new MouseAdapter() {
        public void mouseExited(MouseEvent e) {
          popupInfo.hide();
        }
      });

      // station was selected
      actionSource = new ActionSourceListener("station") {
        public void actionPerformed(ActionValueEvent e) {
          if (debugEvent)
            System.out.println(" StationdatasetChooser: actionSource event " + e);
          selectedStation = (ucar.unidata.geoloc.Station) e.getValue();
          redraw();
        }
      };
    }

    if (dateSelect) {
      TimeCoverage range = null;
      try {
        range = TimeCoverage.builder(DateType.present(), null, new TimeDuration(new TimeUnit(1, "day")), null).build();
      } catch (Exception e) {
        e.printStackTrace();
      }
      dateSelector = new RangeDateSelector(null, range, true, false, null, false, true);
      dateWindow = new IndependentDialog(null, false, "Date Selection", dateSelector);
      dateAction = new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          dateWindow.setVisible(true);
        }
      };
      BAMutil.setActionProperties(dateAction, "nj22/SelectDate", "select date range", false, 'D', -1);
    }

    makeMyUI();
  }

  protected void makeUI() {} // override superclass

  private void makeMyUI() {

    AbstractAction incrFontAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        stnRender.incrFontSize();
        redraw();
      }
    };
    BAMutil.setActionProperties(incrFontAction, "FontIncr", "increase font size", false, 'I', -1);

    AbstractAction decrFontAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        stnRender.decrFontSize();
        redraw();
      }
    };
    BAMutil.setActionProperties(decrFontAction, "FontDecr", "decrease font size", false, 'D', -1);

    JCheckBox declutCB = new JCheckBox("Declutter", true);
    declutCB.addActionListener(e -> setDeclutter(((JCheckBox) e.getSource()).isSelected()));

    AbstractAction bbAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        geoSelectionMode = !geoSelectionMode;
        np.setGeoSelectionMode(geoSelectionMode);
        redraw();
      }
    };
    BAMutil.setActionProperties(bbAction, "nj22/Geoselect", "select geo region", true, 'B', -1);
    bbAction.putValue(BAMutil.STATE, geoSelectionMode ? Boolean.TRUE : Boolean.FALSE);

    // the fields use a PrefPanel
    if (regionSelect) {
      minmaxPP = new PrefPanel(null, null);
      int nfracDig = 3;
      minLonField = minmaxPP.addDoubleField("minLon", "minLon", geoSelection.getMinX(), nfracDig, 0, 0, null);
      maxLonField = minmaxPP.addDoubleField("maxLon", "maxLon", geoSelection.getMaxX(), nfracDig, 2, 0, null);
      minLatField = minmaxPP.addDoubleField("minLat", "minLat", geoSelection.getMinY(), nfracDig, 4, 0, null);
      maxLatField = minmaxPP.addDoubleField("maxLat", "maxLat", geoSelection.getMaxY(), nfracDig, 6, 0, null);

      minmaxPP.finish(true, BorderLayout.EAST);
      minmaxPP.addActionListener(e -> {
        // "Apply" was called
        double minLon = minLonField.getDouble();
        double minLat = minLatField.getDouble();
        double maxLon = maxLonField.getDouble();
        double maxLat = maxLatField.getDouble();
        LatLonRect llbb = new LatLonRect(minLat, minLon, maxLat, maxLon);
        setGeoSelection(llbb);
        redraw();
      });


    }

    // assemble
    setLayout(new BorderLayout());

    if (stationSelect) {
      BAMutil.addActionToContainer(toolPanel, incrFontAction);
      BAMutil.addActionToContainer(toolPanel, decrFontAction);
      toolPanel.add(declutCB);
    }

    if (regionSelect)
      BAMutil.addActionToContainer(toolPanel, bbAction);
    if (dateSelect)
      BAMutil.addActionToContainer(toolPanel, dateAction);

    JPanel upperPanel = new JPanel(new BorderLayout());
    if (regionSelect)
      upperPanel.add(minmaxPP, BorderLayout.NORTH);
    upperPanel.add(toolPanel, BorderLayout.SOUTH);

    JPanel statusPanel = new JPanel(new BorderLayout());
    statusPanel.setBorder(new EtchedBorder());
    JLabel positionLabel = new JLabel("position");
    statusPanel.add(positionLabel, BorderLayout.CENTER);

    np.setPositionLabel(positionLabel);
    add(upperPanel, BorderLayout.NORTH);
    add(np, BorderLayout.CENTER);
    add(statusPanel, BorderLayout.SOUTH);
  }

  /**
   * Add an action to the toolbar.
   *
   * @param act add this action
   */
  public void addToolbarAction(AbstractAction act) {
    BAMutil.addActionToContainer(toolPanel, act);
  }

  /**
   * Fires a PropertyChangeEvent:
   * <ul>
   * <li>propertyName = "Station", getNewValue() = Station
   * <li>propertyName = "GeoRegion", getNewValue() = ProjectionRect
   * </ul>
   */
  private void firePropertyChangeEvent(Object newValue, String propertyName) {
    firePropertyChange(propertyName, null, newValue);
  }

  public void addActionValueListener(ActionValueListener l) {
    actionSource.addActionValueListener(l);
  }

  public void removeActionValueListener(ActionValueListener l) {
    actionSource.removeActionValueListener(l);
  }

  // better way to do event management
  public ActionSourceListener getActionSourceListener() {
    return actionSource;
  }

  /**
   * Set the list of Stations.
   *
   * @param stns list of Station
   */
  public void setStations(java.util.List<Station> stns) {
    stnRender.setStations(stns);
    redraw(true);
  }

  /**
   * Looks for the station with given id. If found, makes it current. Redraws.
   *
   * @param id must match stationIF.getID().
   */
  public void setSelectedStation(String id) {
    stnRender.setSelectedStation(id);
    selectedStation = stnRender.getSelectedStation();
    assert selectedStation != null;
    np.setLatLonCenterMapArea(selectedStation.getLatitude(), selectedStation.getLongitude());
    redraw();
  }

  /**
   * Get currently selected station, or null if none.
   *
   * @return selected station
   */
  public ucar.unidata.geoloc.Station getSelectedStation() {
    return selectedStation;
  }


  /**
   * Access to the navigated panel.
   *
   * @return navigated panel object
   */
  public NavigatedPanel getNavigatedPanel() {
    return np;
  }

  /**
   * Change the state of decluttering
   *
   * @param declut if true, declutter
   */
  public void setDeclutter(boolean declut) {
    stnRender.setDeclutter(declut);
    redraw();
  }

  /**
   * Get the state of the declutter flag.
   *
   * @return the state of the declutter flag.
   */
  public boolean getDeclutter() {
    return stnRender.getDeclutter();
  }


  /**
   * Redraw the graphics on the screen.
   */
  protected void redraw() {
    long tstart = System.currentTimeMillis();

    java.awt.Graphics2D gNP = np.getBufferedImageGraphics();
    if (gNP == null) // panel not drawn on screen yet
      return;

    // clear it
    gNP.setBackground(np.getBackgroundColor());
    java.awt.Rectangle r = gNP.getClipBounds();
    gNP.clearRect(r.x, r.y, r.width, r.height);

    if (regionSelect && geoSelectionMode) {
      if (geoSelection != null)
        drawBB(gNP, geoSelection, Color.cyan);
      if (geoBounds != null)
        drawBB(gNP, geoBounds, null);
      // System.out.println("GeoRegionChooser.redraw geoBounds= "+geoBounds);

      if (geoSelection != null) {
        // gNP.setColor( Color.orange);
        Navigation navigate = np.getNavigation();
        double handleSize = RubberbandRectangleHandles.handleSizePixels / navigate.getPixPerWorld();
        Rectangle2D rect = new Rectangle2D.Double(geoSelection.getX(), geoSelection.getY(), geoSelection.getWidth(),
            geoSelection.getHeight());
        RubberbandRectangleHandles.drawHandledRect(gNP, rect, handleSize);
        if (debug)
          System.out.println("GeoRegionChooser.drawHandledRect=" + handleSize + " = " + geoSelection);
      }
    }

    for (Object renderer : renderers) {
      Renderer rend = (Renderer) renderer;
      rend.draw(gNP, atI);
    }
    gNP.dispose();

    if (debug) {
      long tend = System.currentTimeMillis();
      System.out.println("StationRegionDateChooser draw time = " + (tend - tstart) / 1000.0 + " secs");
    }

    // copy buffer to the screen
    np.repaint();
  }

  private void drawBB(java.awt.Graphics2D g, ProjectionRect bb, Color fillColor) {
    Rectangle2D rect = new Rectangle2D.Double(bb.getX(), bb.getY(), bb.getWidth(), bb.getHeight());
    if (null != fillColor) {
      g.setColor(fillColor);
      g.fill(rect);
    }
    g.setColor(outlineColor);
    g.draw(rect);
  }

  private boolean debug;

  public void setGeoBounds(LatLonRect llbb) {
    np.setMapArea(llbb);
    geoBounds = np.getProjectionImpl().latLonToProjBB(llbb);
    // np.getProjectionImpl().setDefaultMapArea(geoBounds);
    setGeoSelection(geoBounds);
  }

  public void setGeoBounds(ProjectionRect bb) {
    geoBounds = bb;
    np.setMapArea(bb);
  }

  public void setGeoSelection(LatLonRect llbb) {
    np.setGeoSelection(llbb);
    setGeoSelection(np.getGeoSelection());
  }

  public void setGeoSelection(ProjectionRect bb) {
    geoSelection = bb;
    if (minLonField != null) {
      minLonField.setDouble(geoSelection.getMinX());
      minLatField.setDouble(geoSelection.getMinY());
      maxLonField.setDouble(geoSelection.getMaxX());
      maxLatField.setDouble(geoSelection.getMaxY());
    }
    np.setGeoSelection(geoSelection);
  }

  public LatLonRect getGeoSelectionLL() {
    return np.getGeoSelectionLL();
  }

  public ProjectionRect getGeoSelection() {
    return np.getGeoSelection();
  }

  public boolean getGeoSelectionMode() {
    return geoSelectionMode;
  }

  public TimeCoverage getDateRange() {
    if (!dateSelect || !dateWindow.isShowing() || !dateSelector.isEnabled())
      return null;
    return dateSelector.getDateRange();
  }

  public void setDateRange(TimeCoverage range) {
    dateSelector.setDateRange(range);
  }


  /**
   * Wrap this in a JDialog component.
   *
   * @param parent JFrame (application) or JApplet (applet) or null
   * @param title dialog window title
   * @param modal is modal
   * @return the JDialog widget
   */
  public JDialog makeDialog(RootPaneContainer parent, String title, boolean modal) {
    return new Dialog(parent, title, modal);
  }

  private class Dialog extends JDialog {

    private Dialog(RootPaneContainer parent, String title, boolean modal) {
      super(parent instanceof Frame ? (Frame) parent : null, title, modal);

      // L&F may change
      UIManager.addPropertyChangeListener(new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent e) {
          if (e.getPropertyName().equals("lookAndFeel"))
            SwingUtilities.updateComponentTreeUI(StationRegionDateChooser.Dialog.this);
        }
      });

      // add a dismiss button
      JPanel buttPanel = new JPanel();
      JButton dismissButton = new JButton("Dismiss");
      dismissButton.addActionListener(e -> setVisible(false));
      buttPanel.add(dismissButton, null);

      // add it to contentPane
      Container cp = getContentPane();
      cp.setLayout(new BorderLayout());
      cp.add(StationRegionDateChooser.this, BorderLayout.CENTER);
      cp.add(buttPanel, BorderLayout.SOUTH);
      pack();
    }
  }

}

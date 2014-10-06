// License: GPL. See LICENSE file for details.

package org.wikipedia2;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.net.URL;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.markerlayer.MarkerLayer;
import org.openstreetmap.josm.gui.layer.markerlayer.ButtonMarker;
import org.openstreetmap.josm.data.gpx.GpxConstants;
import org.openstreetmap.josm.data.gpx.GpxLink;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.tools.OpenBrowser;

/*
 * A Marker for Wikipedia articles
 */

public class WikipediaMarker extends ButtonMarker {
    
    private String title;
    private final URL url;
    
    public WikipediaMarker(LatLon ll, URL url, String title, MarkerLayer parentLayer) {
        super(ll, getIcon(url.toString()), parentLayer, 0.0, 0);
        this.title = title;
        this.url = url;
    }
    
    static private String getIcon(String uri) {
        String icon = "WPIcon.png"; //default icon
        final Matcher m = Pattern.compile(".*//(\\w+)\\.(\\w+)\\.org/.*").matcher(uri);
        if (m.matches()) {
            if (m.group(1).equals("commons")) {
                icon = "Commons.png";
            }
        }
        return icon;
    }
    
    @Override public void actionPerformed(ActionEvent ev) {
        String error = OpenBrowser.displayUrl(url.toString());
        if (error != null) {
            setErroneous(true);
            new Notification(
                    "<b>" + tr("There was an error while trying to display the URL for this marker") + "</b><br>" +
                                  tr("(URL was: ") + url.toString() + ")" + "<br>" + error)
                    .setIcon(JOptionPane.ERROR_MESSAGE)
                    .setDuration(Notification.TIME_LONG)
                    .show();
        }
    }
    
    @Override
    public WayPoint convertToWayPoint() {
        WayPoint wpt = super.convertToWayPoint();
        wpt.put(GpxConstants.GPX_NAME, title);
        //wpt.addExtension("text", title);
        GpxLink link = new GpxLink(url.toString());
        link.type = "web";
        wpt.put(GpxConstants.META_LINKS, Collections.singleton(link));
        return wpt;
    }
    
    @Override
    public void paint(Graphics g, MapView mv, boolean mousePressed, boolean showTextOrIcon) {
        super.paint(g, mv, mousePressed, showTextOrIcon);
        Point screen = mv.getPoint(getEastNorth());
        if ((title != null) && showTextOrIcon && Main.pref.getBoolean("marker.buttonlabels", true)) {
            g.drawString(title, screen.x+22, screen.y+10);
        }
    }
    
}

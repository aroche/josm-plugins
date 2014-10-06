// License: GPL. See LICENSE file for details./*
package org.wikipedia2;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.io.File;

import javax.swing.Icon;
import javax.swing.AbstractAction;
import javax.swing.Action;

import org.openstreetmap.josm.gui.layer.markerlayer.MarkerLayer;
import org.openstreetmap.josm.gui.layer.markerlayer.Marker;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.JumpToMarkerActions.JumpToMarkerLayer;
import org.openstreetmap.josm.gui.layer.JumpToMarkerActions.JumpToNextMarker;
import org.openstreetmap.josm.gui.layer.JumpToMarkerActions.JumpToPreviousMarker;
import org.openstreetmap.josm.gui.layer.CustomizeColor;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.actions.RenameLayerAction;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.gpx.GpxConstants;
import org.openstreetmap.josm.data.gpx.GpxLink;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.tools.ImageProvider;
import org.wikipedia2.WikipediaApp.WikipediaEntry;


/*
 * A Special layer for Wikipedia
 */


public class WikipediaLayer extends MarkerLayer {
    
    private static String layerName = tr("Wikipedia articles");
    
    public WikipediaLayer(List<WikipediaEntry> indata) {
        super(createGpx(indata), layerName, null, null);
    }
    
    private static GpxData createGpx(List<WikipediaEntry> entries) {
        GpxData gdata = new GpxData();
        for (WikipediaEntry article : entries) {
            gdata.waypoints.add(article.toWayPoint());
        }
        return gdata;
    }
    
    public void updateData(List<WikipediaEntry> indata) {
        GpxData gdata = createGpx(indata);
        data.clear();
        for (WayPoint wpt : gdata.waypoints) {
            Marker m = WikipediaMarker.createMarker(wpt, null, this, 0, 0);
            data.add(m);
        }
    }
    
    @Override
    public Icon getIcon() {
        return ImageProvider.get("dialogs", "wikipedia");
    }
    
    @Override public Action[] getMenuEntries() {
        Collection<Action> components = new ArrayList<>();
        components.add(LayerListDialog.getInstance().createShowHideLayerAction());
        components.add(new ShowHideMarkerText(this));
        components.add(LayerListDialog.getInstance().createDeleteLayerAction());
        components.add(SeparatorLayerAction.INSTANCE);
        components.add(new CustomizeColor(this));
        components.add(SeparatorLayerAction.INSTANCE);
        components.add(new JumpToNextMarker(this));
        components.add(new JumpToPreviousMarker(this));
        components.add(new RenameLayerAction(getAssociatedFile(), this));
        components.add(SeparatorLayerAction.INSTANCE);
        components.add(new LayerListPopup.InfoAction(this));
        return components.toArray(new Action[components.size()]);
    }
}

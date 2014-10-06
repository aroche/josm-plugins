// License: GPL. See LICENSE file for details.
package org.wikipedia2;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.search.SearchAction;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager.FireMode;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.gpx.GpxConstants;
import org.openstreetmap.josm.data.gpx.GpxLink;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.layer.markerlayer.MarkerLayer;
import org.openstreetmap.josm.gui.layer.markerlayer.Marker;
import org.openstreetmap.josm.gui.layer.markerlayer.MarkerProducers;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.LanguageInfo;
import org.openstreetmap.josm.tools.OpenBrowser;
import org.wikipedia2.WikipediaApp.WikipediaEntry;

/*
 * TODO
 * - group close markers (avoid opening many web pages at a time)
 * or open just one
 * 
 * - internal photo visualization dialog
 * - image direction ?
 * - better settings
 */

public class WikipediaToggleDialog extends ToggleDialog implements MapView.EditLayerChangeListener, DataSetListenerAdapter.Listener {

    public WikipediaToggleDialog() {
        super(tr("Wikipedia"), "wikipedia", tr("Fetch Wikipedia articles with coordinates"), null, 150);
        createLayout(list, true, Arrays.asList(
                new SideButton(new WikipediaLoadCoordinatesAction()),
                //new SideButton(new WikipediaLoadCategoryAction()),
                new SideButton(new PasteWikipediaArticlesAction()),
                new SideButton(new AddWikipediaTagAction()),
                new SideButton(new OpenWikipediaArticleAction()),
                new SideButton(new WikipediaSettingsAction(), false)));
        updateTitle();
    }
    
    static {
        Marker.markerProducers.add(0, new MarkerProducers() {
            @Override
            public Marker createMarker(WayPoint wpt, File relativePath, MarkerLayer parentLayer, double time, double offset) {
                String uri = null;
                // cheapest way to check whether "link" object exists and is a non-empty
                // collection of GpxLink objects...
                Collection<GpxLink> links = wpt.<GpxLink>getCollection(GpxConstants.META_LINKS);
                if (links != null) {
                    for (GpxLink oneLink : links ) {
                        uri = oneLink.uri;
                        break;
                    }
                }

                URL url = null;
                if (uri != null) {
                    try {
                        url = new URL(uri);
                    } catch (MalformedURLException e) {
                        // Try a relative file:// url, if the link is not in an URL-compatible form
                        if (relativePath != null) {
                            url = Utils.fileToURL(new File(relativePath.getParentFile(), uri));
                        }
                    }
                }
                
                if (uri.contains("edia.org")) {
                    System.out.println(url.toString());
                    //String title = wpt.getString("text");
                    String title = wpt.getString(GpxConstants.GPX_NAME);
                    return new WikipediaMarker(wpt.getCoor(), url, title, parentLayer);
                } else {
                    return new Marker(wpt.getCoor(), "X", null, parentLayer, 0, 0);
                }
            }
        });
    }
    
    
    
    /** A string describing the context (use-case) for determining the dialog title */
    String titleContext = null;
    /* prevents downloading from geocommons if necessary (provisoire) */
    static final Boolean retrieveFromCommons = true;
    final StringProperty wikipediaLang = new StringProperty("wikipedia.lang", LanguageInfo.getJOSMLocaleCode().substring(0, 2));
    final Set<String> articles = new HashSet<String>();
    private List<WikipediaEntry> entries = new ArrayList();
    private WikipediaLayer layer = null;
    final DefaultListModel<WikipediaEntry> model = new DefaultListModel<>();
    final JList<WikipediaEntry> list = new JList<WikipediaEntry>(model) {

        {
            setToolTipText(tr("Double click on item to search for object with article name (and center coordinate)"));
            addMouseListener(new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && getSelectedValue() != null) {
                        final WikipediaEntry entry = (WikipediaEntry) getSelectedValue();
                        if (entry.coordinate != null) {
                            BoundingXYVisitor bbox = new BoundingXYVisitor();
                            bbox.visit(entry.coordinate);
                            Main.map.mapView.recalculateCenterScale(bbox);
                        }
                        SearchAction.search(entry.name.replaceAll("\\(.*\\)", ""), SearchAction.SearchMode.replace);
                    }
                }
            });

            setCellRenderer(new DefaultListCellRenderer() {

                @Override
                public JLabel getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    final WikipediaEntry entry = (WikipediaEntry) value;
                    if (entry.isImage()) {
                        label.setIcon(ImageProvider.getIfAvailable("markers", "photo"));
                    } else if (entry.getWiwosmStatus() != null && entry.getWiwosmStatus()) {
                        label.setIcon(ImageProvider.getIfAvailable("misc", "grey_check"));
                        label.setToolTipText(/* I18n: WIWOSM server already links Wikipedia article to object/s */ tr("Available via WIWOSM server"));
                    } else if (articles.contains(entry.wikipediaArticle)) {
                        label.setIcon(ImageProvider.getIfAvailable("misc", "green_check"));
                        label.setToolTipText(/* I18n: object/s from dataset contain link to Wikipedia article */ tr("Available in local dataset"));
                    } else {
                        label.setToolTipText(tr("Not linked yet"));
                    }
                    return label;
                }
            });
        }
    };

    private void updateTitle() {
        if (titleContext == null) {
            setTitle(/* I18n: [language].Wikipedia.org */ tr("{0}.Wikipedia.org", wikipediaLang.get()));
        } else {
            setTitle(/* I18n: [language].Wikipedia.org: [context] */ tr("{0}.Wikipedia.org: {1}", wikipediaLang.get(), titleContext));
        }
    }

    class WikipediaLoadCoordinatesAction extends AbstractAction {

        public WikipediaLoadCoordinatesAction() {
            super(tr("Coordinates"), ImageProvider.get("dialogs", "refresh"));
            putValue(SHORT_DESCRIPTION, tr("Fetches all coordinates from Wikipedia in the current view"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                // determine bbox
                final LatLon min = Main.map.mapView.getLatLon(0, Main.map.mapView.getHeight());
                final LatLon max = Main.map.mapView.getLatLon(Main.map.mapView.getWidth(), 0);
                // add entries to list model
                titleContext = tr("coordinates");
                updateTitle();
                new UpdateWikipediaArticlesSwingWorker() {

                    @Override
                    List<WikipediaEntry> getEntries() {
                        List<WikipediaEntry> wpentries = WikipediaApp.getEntriesFromCoordinates(
                                wikipediaLang.get(), min, max);
                        if (retrieveFromCommons) {
                            List<WikipediaEntry> commonsentries =
                                WikipediaApp.getEntriesFromCoordinates("commons", min, max);
                            wpentries.addAll(commonsentries);
                        }
                        return wpentries;
                    }
                }.execute();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    abstract class UpdateWikipediaArticlesSwingWorker extends SwingWorker<Void, WikipediaEntry> {

        private final IntegerProperty wikipediaStatusUpdateChunkSize = new IntegerProperty("wikipedia.statusupdate.chunk-size", 20);

        abstract List<WikipediaEntry> getEntries();

        @Override
        protected Void doInBackground() throws Exception {
            final List<WikipediaEntry> entries = getEntries();
            Collections.sort(entries);
            publish(entries.toArray(new WikipediaEntry[entries.size()]));
            for (List<WikipediaEntry> chunk : WikipediaApp.partitionList(entries, wikipediaStatusUpdateChunkSize.get())) {
                WikipediaApp.updateWIWOSMStatus(wikipediaLang.get(), chunk);
            }
            return null;
        }

        @Override
        protected void process(List<WikipediaEntry> chunks) {
            entries.clear();
            model.clear();
            for (WikipediaEntry i : chunks) {
                model.addElement(i);
                entries.add(i);
            }
            updateLayer();
        }

    }

    class WikipediaLoadCategoryAction extends AbstractAction {

        public WikipediaLoadCategoryAction() {
            super(tr("Category"), ImageProvider.get("dialogs", "refresh"));
            putValue(SHORT_DESCRIPTION, tr("Fetches a list of all Wikipedia articles of a category"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final String category = JOptionPane.showInputDialog(
                    Main.parent,
                    tr("Enter the Wikipedia category"));
            if (category == null) {
                return;
            }

            titleContext = category;
            updateTitle();

            new UpdateWikipediaArticlesSwingWorker() {
                @Override
                List<WikipediaEntry> getEntries() {
                    return WikipediaApp.getEntriesFromCategory(
                            wikipediaLang.get(), category, Main.pref.getInteger("wikipedia.depth", 3));
                }
            }.execute();
        }
    }

    class PasteWikipediaArticlesAction extends AbstractAction {

        public PasteWikipediaArticlesAction() {
            super(tr("Clipboard"), ImageProvider.get("paste"));
            putValue(SHORT_DESCRIPTION, tr("Pastes Wikipedia articles from the system clipboard"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            titleContext = tr("clipboard");
            updateTitle();
            new UpdateWikipediaArticlesSwingWorker() {

                @Override
                List<WikipediaEntry> getEntries() {
                    return WikipediaApp.getEntriesFromClipboard(wikipediaLang.get());
                }
            }.execute();
        }
    }

    class OpenWikipediaArticleAction extends AbstractAction {

        public OpenWikipediaArticleAction() {
            super(tr("Open Article"), ImageProvider.getIfAvailable("browser"));
            putValue(SHORT_DESCRIPTION, tr("Opens the Wikipedia article of the selected item in a browser"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (list.getSelectedValue() != null) {
                final String url = ((WikipediaEntry) list.getSelectedValue()).getBrowserUrl();
                System.out.println("Wikipedia: opening " + url);
                OpenBrowser.displayUrl(url);
            }
        }
    }

    class WikipediaSettingsAction extends AbstractAction {

        public WikipediaSettingsAction() {
            super(tr("Language"), ImageProvider.get("dialogs/settings"));
            putValue(SHORT_DESCRIPTION, tr("Sets the default language for the Wikipedia articles"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String lang = JOptionPane.showInputDialog(
                    Main.parent,
                    tr("Enter the Wikipedia language"),
                    wikipediaLang.get());
            if (lang != null) {
                wikipediaLang.put(lang);
                updateTitle();
                updateWikipediaArticles();
            }
        }
    }
    
    // TODO : create a proper settings dialog to check if commons pictures must
    // be retrieved.

    class AddWikipediaTagAction extends AbstractAction {

        public AddWikipediaTagAction() {
            super(tr("Add Tag"), ImageProvider.get("pastetags"));
            putValue(SHORT_DESCRIPTION, tr("Adds a ''wikipedia'' tag corresponding to this article to the selected objects"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (list.getSelectedValue() != null) {
                WikipediaEntry entry = (WikipediaEntry) list.getSelectedValue();
                if (entry.isImage()) {
                    return;
                }
                Tag tag = entry.createWikipediaTag();
                if (tag != null) {
                    ChangePropertyCommand cmd = new ChangePropertyCommand(
                            Main.main.getCurrentDataSet().getSelected(),
                            tag.getKey(), tag.getValue());
                    Main.main.undoRedo.add(cmd);
                }
            }
        }
    }

    protected void updateWikipediaArticles() {
        articles.clear();
        if (Main.main != null && Main.main.getCurrentDataSet() != null) {
            for (final OsmPrimitive p : Main.main.getCurrentDataSet().allPrimitives()) {
                articles.addAll(WikipediaApp.getWikipediaArticles(wikipediaLang.get(), p));
            }
        }
    }

    private final DataSetListenerAdapter dataChangedAdapter = new DataSetListenerAdapter(this);

    @Override
    public void showNotify() {
        DatasetEventManager.getInstance().addDatasetListener(dataChangedAdapter, FireMode.IN_EDT_CONSOLIDATED);
        MapView.addEditLayerChangeListener(this);
        updateWikipediaArticles();
    }

    @Override
    public void hideNotify() {
        DatasetEventManager.getInstance().removeDatasetListener(dataChangedAdapter);
        MapView.removeEditLayerChangeListener(this);
        articles.clear();
        layer = null;
    }

    @Override
    public void editLayerChanged(OsmDataLayer oldLayer, OsmDataLayer newLayer) {
        updateWikipediaArticles();
        list.repaint();
    }

    @Override
    public void processDatasetEvent(AbstractDatasetChangedEvent event) {
        updateWikipediaArticles();
        list.repaint();
    }
    
    // layer part
    private void updateLayer() {
        // System.out.println(layer);
        if (entries.size() > 0) {
            if (layer == null) {
                layer = new WikipediaLayer(entries); 
                Main.main.addLayer(layer); 
            } else {
                layer.updateData(entries);
                if (! Main.main.map.mapView.hasLayer(layer)) {
                    Main.main.addLayer(layer);
                }
                Main.map.mapView.repaint();
            }
        }
    }
}

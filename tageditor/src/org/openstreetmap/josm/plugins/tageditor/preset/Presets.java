package org.openstreetmap.josm.plugins.tageditor.preset;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.io.MirroredInputStream;
import org.openstreetmap.josm.plugins.tageditor.preset.io.Parser;
import org.openstreetmap.josm.plugins.tageditor.preset.io.PresetIOException;

public class Presets {
    private static Logger logger = Logger.getLogger(Presets.class.getName());

    private static Presets presets = null;

    static public void initPresets() {

        presets = new Presets();
        LinkedList<String> sources = new LinkedList<String>();

        // code copied from org.openstreetmap.josm.gui.tagging.TaggingPreset
        // and slightly modified
        //
        if (Main.pref.getBoolean("taggingpreset.enable-defaults", true)) {
            sources.add("resource://data/defaultpresets.xml");
        }
        sources.addAll(Main.pref.getCollection("taggingpreset.sources",
                new LinkedList<String>()));

        File zipIconArchive = null;
        for (String source : sources) {
            try {
                MirroredInputStream s = new MirroredInputStream(source);
                InputStream zip = s.findZipEntryInputStream("xml","preset");
                if(zip != null) {
                    zipIconArchive = s.getFile();
                }
                InputStreamReader r;
                try {
                    r = new InputStreamReader(s, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    r = new InputStreamReader(s);
                }
                presets = loadPresets(r, presets, zipIconArchive);
            } catch (PresetIOException e) {
                logger
                        .log(Level.SEVERE, tr(
                                "Could not read tagging preset source: {0}",
                                source), e);
                JOptionPane.showMessageDialog(Main.parent, tr(
                        "Could not read tagging preset source: {0}", source),
                        tr("Error"), JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(Main.parent, tr(
                        "Could not read tagging preset source: {0}", source),
                        tr("Error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    static public Presets loadPresets(URL from) throws PresetIOException {
        try {
            URLConnection con = from.openConnection();
            con.connect();
            Reader reader = new InputStreamReader(con.getInputStream());
            return loadPresets(reader, null, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "exception caught while loading preset file", e);
            throw new PresetIOException(e);
        }
    }

    static public Presets loadPresets(Reader reader, Presets p, File zipIconArchive) throws PresetIOException {
        try {
            Parser parser = new Parser();
            parser.setReader(reader);
            parser.setPresets(p);
            parser.parse();
            return parser.getPresets();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "exception caught while loading presets",e);
            throw new PresetIOException(e);
        }
    }

    static public Presets getPresets() {
        if (presets == null) {
            initPresets();
        }
        return presets;
    }

    private List<Group> groups;

    public Presets() {
        groups = new ArrayList<Group>();
    }

    public void addGroup(Group group) {
        groups.add(group);
    }

    public void removeGroup(Group group) {
        groups.remove(group);
    }

    public List<Group> getGroups() {
        return groups;
    }
}

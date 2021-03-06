package org.openstreetmap.josm.plugins.canvec_helper;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.Main;

public class CanvecHelperAction extends JosmAction {
	private CanvecHelper parent_temp;
	public CanvecHelperAction(CanvecHelper parent) {
		super("CanVec Helper","layericon24",null,null,false);
		parent_temp = parent;
	}
        @Override
	public void actionPerformed(java.awt.event.ActionEvent action) {
		CanvecLayer layer;
		layer = new CanvecLayer("canvec tile helper",parent_temp);
		Main.main.addLayer(layer);
	}
}

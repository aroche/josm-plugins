/* Copyright 2014 Malcolm Herring
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * For a copy of the GNU General Public License, see <http://www.gnu.org/licenses/>.
 */

package jrender;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import jrender.Jrender.MapBB;
import render.MapContext;
import render.Renderer;
import s57.S57map;
import s57.S57map.*;

public class Tilegen {

	static class Context implements MapContext {
		
		static double minlat = 0;
		static double minlon = 0;
		static double maxlat = 0;
		static double maxlon = 0;
	  static double top = 0;
	  static double mile = 0;
	  
	  public Context (double nt, double nn, double xt, double xn) {
			minlat = nt;
			minlon = nn;
			maxlat = xt;
			maxlon = xn;
			top = (1.0 - Math.log(Math.tan(Math.toRadians(maxlat)) + 1.0 / Math.cos(Math.toRadians(maxlat))) / Math.PI) / 2.0 * 256.0 * 4096.0;
			mile = 768 / ((maxlat - minlat) * 60);
	  }
	  
		@Override
		public Point2D getPoint(Snode coord) {
			double x = (Math.toDegrees(coord.lon) - minlon) * 256.0 * 2048.0 / 180.0;
			double y = ((1.0 - Math.log(Math.tan(coord.lat) + 1.0 / Math.cos(coord.lat)) / Math.PI) / 2.0 * 256.0 * 4096.0) - top;
			return new Point2D.Double(x, y);
		}

		@Override
		public double mile(Feature feature) {
			return mile;
		}
	}
	
	static Context context;
	static S57map map;
	static int empty;
	static String dir;
	
	public static void tileMap(ArrayList<String> buf, MapBB bb, String idir) throws IOException {
		String k = "";
		String v = "";
		
		double lat = 0;
		double lon = 0;
		long id = 0;

		BufferedImage img;
		boolean inOsm = false;
		boolean inNode = false;
		boolean inWay = false;
		boolean inRel = false;
		
		context = new Context(bb.minlat, bb.minlon, bb.maxlat, bb.maxlon);
		dir = idir;

		for (String ln : buf) {
			if (inOsm) {
				if ((inNode || inWay || inRel) && (ln.contains("<tag"))) {
					k = v = "";
					String[] token = ln.split("k=");
					k = token[1].split("[\"\']")[1];
					token = token[1].split("v=");
					v = token[1].split("[\"\']")[1];
					if (!k.isEmpty() && !v.isEmpty()) {
						map.addTag(k, v);
					}
				}
				if (inNode) {
					if (ln.contains("</node")) {
						inNode = false;
						map.tagsDone(id);
					}
				} else if (ln.contains("<node")) {
					for (String token : ln.split("[ ]+")) {
						if (token.matches("^id=.+")) {
							id = Long.parseLong(token.split("[\"\']")[1]);
						} else if (token.matches("^lat=.+")) {
							lat = Double.parseDouble(token.split("[\"\']")[1]);
						} else if (token.matches("^lon=.+")) {
							lon = Double.parseDouble(token.split("[\"\']")[1]);
						}
					}
					map.addNode(id, lat, lon);
					if (ln.contains("/>")) {
						map.tagsDone(id);
					} else {
						inNode = true;
					}
				} else if (inWay) {
					if (ln.contains("<nd")) {
						long ref = 0;
						for (String token : ln.split("[ ]+")) {
							if (token.matches("^ref=.+")) {
								ref = Long.parseLong(token.split("[\"\']")[1]);
							}
						}
						map.addToEdge(ref);
					}
					if (ln.contains("</way")) {
						inWay = false;
						map.tagsDone(id);
					}
				} else if (ln.contains("<way")) {
					for (String token : ln.split("[ ]+")) {
						if (token.matches("^id=.+")) {
							id = Long.parseLong(token.split("[\"\']")[1]);
						}
					}
					map.addEdge(id);
					if (ln.contains("/>")) {
						map.tagsDone(0);
					} else {
						inWay = true;
					}
				} else if (ln.contains("</osm")) {
					inOsm = false;
					break;
				} else if (inRel) {
					if (ln.contains("<member")) {
						String type = "";
						String role = "";
						long ref = 0;
						for (String token : ln.split("[ ]+")) {
							if (token.matches("^ref=.+")) {
								ref = Long.parseLong(token.split("[\"\']")[1]);
							} else if (token.matches("^type=.+")) {
								type = (token.split("[\"\']")[1]);
							} else if (token.matches("^role=.+")) {
								role = (token.split("[\"\']")[1]);
							}
						}
						if ((role.equals("outer") || role.equals("inner")) && type.equals("way"))
							map.addToArea(ref, role.equals("outer"));
					}
					if (ln.contains("</relation")) {
						inRel = false;
						map.tagsDone(id);
					}
				} else if (ln.contains("<relation")) {
					for (String token : ln.split("[ ]+")) {
						if (token.matches("^id=.+")) {
							id = Long.parseLong(token.split("[\"\']")[1]);
						}
					}
					map.addArea(id);
					if (ln.contains("/>")) {
						map.tagsDone(id);
					} else {
						inRel = true;
					}
				}
			} else if (ln.contains("<osm")) {
				inOsm = true;
				map = new S57map();
			}
		}
		
		img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		Renderer.reRender(img.createGraphics(), 12, 1, map, context);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(img, "png", bos);
		empty = bos.size();
		tile(12, 1, 0, 0);

		for (int z = 12; z <= 18; z++) {
			DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
			Document document = domImpl.createDocument("http://www.w3.org/2000/svg", "svg", null);
			SVGGraphics2D svgGenerator = new SVGGraphics2D(document);
			svgGenerator.setSVGCanvasSize(new Dimension(256, 256));
			svgGenerator.setClip(0, 0, 256, 256);
			svgGenerator.translate(-256, -256);
			Renderer.reRender(svgGenerator, z, 1, map, context);
			svgGenerator.stream(dir + "tst_" + z + ".svg");
		}
	}
	
	static void tile(int zoom, int s, int xn, int yn) throws IOException {
		BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		g2.scale(s, s);
		g2.translate(-(256 + (xn * 256 / s)), -(256 + (yn * 256 / s)));
		Renderer.reRender(g2, zoom, 1, map, context);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(img, "png", bos);
		if (bos.size() > empty) {
			FileOutputStream fos = new FileOutputStream(dir + "tst" + zoom + "_" + xn + "_" + yn + ".png");
			bos.writeTo(fos);
			fos.close();
		}
		if ((zoom < 18) && ((zoom < 16) || (bos.size() > empty))) {
			for (int x = 0; x < 2; x++) {
				for (int y = 0; y < 2; y++) {
					tile((zoom + 1), (s * 2), (xn * 2 + x), (yn * 2 + y));
				}
			}
		}
	}
	
}

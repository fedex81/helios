/*
 * MsxXmlLoader
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/04/19 14:28
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.util;

import omegadrive.bus.mapper.MapperSelector;
import omegadrive.bus.mapper.RomMapper;
import omegadrive.input.KeyboardInput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * MSX roms db from the openMsx project
 * https://raw.githubusercontent.com/openMSX/openMSX/a7b84217394b7aac1a9d10b9f445d7fd4d250386/share/softwaredb.xml
 */
public class MsxXmlLoader {

    private static Logger LOG = LogManager.getLogger(MsxXmlLoader.class.getSimpleName());

    static DocumentBuilderFactory dbf;

    static String softwareXPath = "//software";
    static String titleXPath = "title/text()";
    static String romXPath = "dump/rom";
    static String megaromXPath = "dump/megarom";
    static String typeEl = "type";
    static String hashEl = "hash";

    static String fileName = "msx_sw_db.xml";

    static {
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(true);

        try{
            dbf.setFeature("http://xml.org/sax/features/namespaces", false);
            dbf.setFeature("http://xml.org/sax/features/validation", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception e){
            LOG.error("Unable to setup xml parser", e);
        }
    }

    public static Map<String, MapperSelector.Entry> loadData(){
        Map<String, MapperSelector.Entry> map = new HashMap<>();
        long start = System.currentTimeMillis();
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.parse(new File(fileName));

            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xPath.evaluate(softwareXPath, doc, XPathConstants.NODESET);

            for (int i = 0; i < nodes.getLength(); i++) {
                Node singleNode = nodes.item(i).cloneNode(true); //huge perf increase
                String title = xPath.evaluate(titleXPath, singleNode);
                NodeList roms = (NodeList) xPath.evaluate(romXPath, singleNode, XPathConstants.NODESET);
                addRoms(map, title, roms, false);
                NodeList megaroms = (NodeList) xPath.evaluate(megaromXPath, singleNode, XPathConstants.NODESET);
                addRoms(map, title, megaroms, true);
            }
        } catch (Exception e){
            LOG.error("Unable to parse: " + fileName, e);
        }
        LOG.info("XML loaded in ms: " + (System.currentTimeMillis() - start));
        return map;
    }

    private static void addRoms(Map<String, MapperSelector.Entry> map, String title, NodeList data, boolean megaRoms){
        for (int i = 0; i < data.getLength(); i++) {
            NodeList ch = data.item(i).getChildNodes();
            MapperSelector.Entry e = new MapperSelector.Entry();
            e.title = title;
            e.mapperName = RomMapper.NO_MAPPER_NAME;
            for (int j = 0; j < ch.getLength(); j++) {
                String text = ch.item(j).getTextContent();
                String name = ch.item(j).getNodeName();
                if(megaRoms && typeEl.equalsIgnoreCase(name)){
                    e.mapperName = text;
                }
                if(hashEl.equalsIgnoreCase(name)){
                    e.sha1 = text;
                }
            }
            MapperSelector.Entry prev = map.putIfAbsent(e.sha1, e);
            if(prev != null){
                LOG.warn("Hash collision for: {} and {}", prev, e);
            }
        }
    }

}

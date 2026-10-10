package com.spgrvl.packagetracker;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/** Reads the app's real string resources so tests check the shipped keyword lists. */
final class StringResources {
    private final Document document;

    private StringResources(Document document) {
        this.document = document;
    }

    /** @param valuesDir e.g. "values" or "values-el" */
    static StringResources load(String valuesDir) {
        // Unit tests run with the module directory as the working directory
        File file = new File("src/main/res/" + valuesDir + "/strings.xml");
        try {
            return new StringResources(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + file.getAbsolutePath(), e);
        }
    }

    String[] array(String name) {
        NodeList arrays = document.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if (name.equals(array.getAttribute("name"))) {
                NodeList items = array.getElementsByTagName("item");
                List<String> values = new ArrayList<>();
                for (int j = 0; j < items.getLength(); j++) {
                    values.add(unescape(items.item(j).getTextContent()));
                }
                return values.toArray(new String[0]);
            }
        }
        throw new IllegalArgumentException("No string-array named " + name);
    }

    /** All strings whose name starts with the given prefix, keyed by name. */
    Map<String, String> stringsWithPrefix(String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        NodeList strings = document.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element string = (Element) strings.item(i);
            if (string.getAttribute("name").startsWith(prefix)) {
                result.put(string.getAttribute("name"), unescape(string.getTextContent()));
            }
        }
        return result;
    }

    private static String unescape(String value) {
        return value.replace("\\'", "'").replace("\\\"", "\"");
    }
}

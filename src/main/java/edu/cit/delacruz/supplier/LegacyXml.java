package edu.cit.delacruz.supplier;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Builds LegacySupply's request XML and parses its response XML. XML is
 * exactly the quirk the ACL exists to hide, so nothing outside this class
 * - not even the rest of this module - ever touches a DOM node or an
 * LSError; everyone else only sees the records below.
 */
final class LegacyXml {

    private LegacyXml() {
    }

    static String authRequest(String clientId, String apiKey) {
        return "<AuthRequest><ClientId>" + clientId + "</ClientId><ApiKey>" + apiKey
                + "</ApiKey></AuthRequest>";
    }

    static String purchaseOrderRequest(String sku, int qty, String buyerRef) {
        return "<PurchaseOrder><SupplierSku>" + sku + "</SupplierSku><Qty>" + qty
                + "</Qty><BuyerRef>" + buyerRef + "</BuyerRef></PurchaseOrder>";
    }

    record AuthResult(String sessionToken) {
    }

    record OrderAck(String poNumber, int statusCode) {
    }

    record OrderStatus(String poNumber, int statusCode) {
    }

    record LsError(String code, String message) {
    }

    static AuthResult parseAuthResponse(String xml) {
        Element root = parse(xml);
        return new AuthResult(text(root, "SessionToken"));
    }

    static OrderAck parseOrderAck(String xml) {
        Element root = parse(xml);
        return new OrderAck(text(root, "PoNumber"), Integer.parseInt(text(root, "StatusCode")));
    }

    static OrderStatus parseOrderStatus(String xml) {
        Element root = parse(xml);
        return new OrderStatus(text(root, "PoNumber"), Integer.parseInt(text(root, "StatusCode")));
    }

    static LsError parseError(String xml) {
        Element root = parse(xml);
        return new LsError(text(root, "Code"), text(root, "Message"));
    }

    private static Element parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // LegacySupply is an external system: never resolve a DOCTYPE
            // or external entity in whatever it sends back (XXE hardening).
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed LegacySupply XML: " + e.getMessage(), e);
        }
    }

    private static String text(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            throw new IllegalArgumentException("Missing <" + tag + "> in LegacySupply response");
        }
        return nodes.item(0).getTextContent().trim();
    }
}

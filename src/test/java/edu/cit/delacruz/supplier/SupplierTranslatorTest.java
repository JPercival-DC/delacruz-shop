package edu.cit.delacruz.supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Plain unit tests for the no-network parts of the ACL: unit->case
 * rounding, the XML translator, and status-code mapping. No Spring
 * context - these still catch a broken rounding rule or a parser
 * regression even when the LegacySupply sandbox is down.
 */
class SupplierTranslatorTest {

    @Test
    void roundsUpToWholeCases() {
        assertEquals(2, SupplierGatewayImpl.casesFor(15, 10)); // 1.5 -> 2, per INTEGRATION.md
        assertEquals(1, SupplierGatewayImpl.casesFor(10, 10)); // exact multiple, no over-order
        assertEquals(1, SupplierGatewayImpl.casesFor(1, 6));
    }

    @Test
    void capsAtLegacySupplysQtyCeiling() {
        assertEquals(99, SupplierGatewayImpl.casesFor(100_000, 1));
    }

    @Test
    void parsesPurchaseOrderAck() {
        String xml = """
                <PurchaseOrderAck>
                 <PoNumber>PO-100231</PoNumber>
                 <StatusCode>10</StatusCode>
                 <SupplierSku>BHD-1152</SupplierSku>
                 <Qty>2</Qty>
                 <Uom>CS</Uom>
                 <BuyerRef>RO-42</BuyerRef>
                 <CreatedAt>2026-09-24T01:16:02.000Z</CreatedAt>
                </PurchaseOrderAck>
                """;

        LegacyXml.OrderAck ack = LegacyXml.parseOrderAck(xml);

        assertEquals("PO-100231", ack.poNumber());
        assertEquals(10, ack.statusCode());
    }

    @Test
    void parsesErrorDocument() {
        String xml = """
                <LSError>
                 <Code>E-QTY-11</Code>
                 <Message>Quantity invalid.</Message>
                </LSError>
                """;

        LegacyXml.LsError error = LegacyXml.parseError(xml);

        assertEquals("E-QTY-11", error.code());
    }

    @Test
    void mapsKnownStatusCodes() {
        assertEquals(SupplierOrderStatus.PLACED, SupplierJobs.mapStatus(10));
        assertEquals(SupplierOrderStatus.PREPARING, SupplierJobs.mapStatus(20));
        assertEquals(SupplierOrderStatus.SHIPPED, SupplierJobs.mapStatus(30));
        assertEquals(SupplierOrderStatus.DELIVERED, SupplierJobs.mapStatus(40));
    }

    @Test
    void mapsUnexpectedStatusCodeToNeedsReview() {
        assertEquals(SupplierOrderStatus.NEEDS_REVIEW, SupplierJobs.mapStatus(99));
    }
}

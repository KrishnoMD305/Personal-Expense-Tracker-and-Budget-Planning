package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;

/** Stage 7: JSON parsing and arithmetic of the REST exchange-rate answer. */
public class ExchangeRatesTest {
    private static final String OK = "{\"result\":\"success\",\"time_last_update_unix\":1762473000,"
            + "\"base_code\":\"USD\",\"rates\":{\"USD\":1,\"BDT\":122.4,\"EUR\":0.8,\"JPY\":150.0}}";

    @Test
    public void parsesASuccessfulAnswer() throws Exception {
        ExchangeRates r = ExchangeRates.parse(OK);
        assertEquals(1762473000000L, r.updatedMillis);
        assertTrue(r.has("EUR"));
        assertFalse(r.has("XXX"));
        assertEquals(122.4, r.homePer("USD"), 1e-9);
        assertEquals(122.4 / 0.8, r.homePer("EUR"), 1e-9);
        assertEquals(1.0, r.convertFromHome(122.4, "USD"), 1e-9);
        assertTrue(Double.isNaN(r.homePer("XXX")));
    }

    @Test
    public void rejectsBadAnswers() {
        String[] bad = {"", "   ", "not json", "{}", "{\"result\":\"error\"}",
                "{\"result\":\"success\",\"rates\":{\"USD\":1}}",           // no BDT
                "{\"result\":\"success\",\"rates\":\"x\"}"};
        for (String b : bad) {
            try {
                ExchangeRates.parse(b);
                fail("accepted: " + b);
            } catch (JSONException expected) {
                // ok
            }
        }
    }

    @Test
    public void ignoresNonPositiveRates() throws Exception {
        ExchangeRates r = ExchangeRates.parse("{\"result\":\"success\",\"rates\":{\"BDT\":120,\"ZZZ\":0,\"YYY\":-3}}");
        assertFalse(r.has("ZZZ"));
        assertFalse(r.has("YYY"));
    }

    @Test
    public void formatsTwoDecimals() {
        assertEquals("1,234.50", ExchangeRates.plain(1234.5));
        assertEquals("-", ExchangeRates.plain(Double.NaN));
    }
}

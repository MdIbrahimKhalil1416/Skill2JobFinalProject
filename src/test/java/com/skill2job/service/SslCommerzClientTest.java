package com.skill2job.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the Validation API's answers are classified: paid / not paid / could not be asked. No network. */
class SslCommerzClientTest {

    private static SslCommerzClient clientAnswering(Object answer) throws Exception {
        RestTemplate rt = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T getForObject(URI url, Class<T> type) throws RestClientException {
                if (answer instanceof RuntimeException e) {
                    throw e;
                }
                return (T) answer;
            }
        };
        SslCommerzClient c = new SslCommerzClient(rt);
        for (String f : new String[]{"storeId", "storePassword"}) {
            Field field = SslCommerzClient.class.getDeclaredField(f);
            field.setAccessible(true);
            field.set(c, "x");
        }
        Field sandbox = SslCommerzClient.class.getDeclaredField("sandbox");
        sandbox.setAccessible(true);
        sandbox.set(c, true);
        return c;
    }

    private static Map<String, Object> body(String status) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("tran_id", "SUB-1");
        m.put("amount", "2000.00");
        m.put("currency_type", "BDT");
        m.put("card_type", "VISA");
        return m;
    }

    @Test
    void validAnswerCarriesTranIdAmountAndCurrencyType() throws Exception {
        SslCommerzClient.Validation v = clientAnswering(body("VALID")).validate("V1");
        assertTrue(v.valid());
        assertFalse(v.unavailable());
        assertEquals("SUB-1", v.tranId());
        assertEquals("2000.00", v.amount());
        assertEquals("BDT", v.currency());
    }

    @Test
    void anAnswerThatIsNotValidIsAnoIsNotAnOutage() throws Exception {
        SslCommerzClient.Validation v = clientAnswering(body("INVALID_TRANSACTION")).validate("V1");
        assertFalse(v.valid());
        assertFalse(v.unavailable());
    }

    @Test
    void timeoutOrServerErrorOrEmptyBodyMeansUnavailableNotFailed() throws Exception {
        assertTrue(clientAnswering(new RestClientException("timed out")).validate("V1").unavailable());
        assertTrue(clientAnswering(null).validate("V1").unavailable());
    }

    @Test
    void blankValIdIsInvalidWithoutAskingTheGateway() throws Exception {
        SslCommerzClient.Validation v = clientAnswering(new RestClientException("must not be called")).validate(" ");
        assertFalse(v.valid());
        assertFalse(v.unavailable());
    }
}

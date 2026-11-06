package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class PinHasherTest {
    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // Published PBKDF2-HMAC-SHA256 test vectors (password "password", salt "salt").
    @Test
    public void matchesKnownVectorOneIteration() {
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
                PinHasher.toHex(PinHasher.pbkdf2(b("password"), b("salt"), 1)));
    }

    @Test
    public void matchesKnownVectorTwoIterations() {
        assertEquals("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
                PinHasher.toHex(PinHasher.pbkdf2(b("password"), b("salt"), 2)));
    }

    @Test
    public void matchesKnownVector4096Iterations() {
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
                PinHasher.toHex(PinHasher.pbkdf2(b("password"), b("salt"), 4096)));
    }

    @Test
    public void verifyAcceptsRightPinAndRejectsWrongOne() {
        byte[] salt = PinHasher.newSalt();
        String hash = PinHasher.hash("4821", salt, 1000);
        assertTrue(PinHasher.verify("4821", salt, 1000, hash));
        assertFalse(PinHasher.verify("4822", salt, 1000, hash));
        assertFalse(PinHasher.verify("4821", PinHasher.newSalt(), 1000, hash));
    }

    @Test
    public void saltsAreRandom() {
        assertNotEquals(PinHasher.toHex(PinHasher.newSalt()), PinHasher.toHex(PinHasher.newSalt()));
    }

    @Test
    public void hexRoundTripAndBadHex() {
        byte[] data = {0, 1, (byte) 0xAB, (byte) 0xFF};
        assertEquals("0001abff", PinHasher.toHex(data));
        assertEquals(4, PinHasher.fromHex("0001abff").length);
        assertEquals(0, PinHasher.fromHex("abc").length);
        assertEquals(0, PinHasher.fromHex("zz").length);
        assertEquals(0, PinHasher.fromHex(null).length);
    }

    @Test
    public void verifyWithCorruptStoredHashIsFalseNotException() {
        assertFalse(PinHasher.verify("1234", PinHasher.newSalt(), 10, "not-hex"));
    }
}

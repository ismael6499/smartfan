package com.nitsutech.smartfan.ble;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ISoundTekProtocolTest {

    private static final String TEST_ADDR = "a1b2c3d4";

    @Test
    public void testGoldenVectorsMatchOriginalProtocol() {
        assertEquals(
                "ee1bc878f61120876d53f29e3c149696f460d2cf7a1465af",
                ISoundTekProtocol.bytesToHex(ISoundTekProtocol.buildPacket(
                        TEST_ADDR, 0, 11, ISoundTekProtocol.CMD_FAN_SPEED, ISoundTekProtocol.paramFanSpeed(3), 0x92)));

        assertEquals(
                "ee1bc878f61120876e53f29e3c6cdaec8c18aab7026c791f",
                ISoundTekProtocol.bytesToHex(ISoundTekProtocol.buildPacket(
                        TEST_ADDR, 0, 8, ISoundTekProtocol.CMD_LIGHT_POWER, ISoundTekProtocol.paramBool(true), 0xea)));

        assertEquals(
                "ee1bc878f61120876a53f29e3cd3545657951508bdd36c68",
                ISoundTekProtocol.bytesToHex(ISoundTekProtocol.buildPacket(
                        TEST_ADDR, 0, 12, ISoundTekProtocol.CMD_DIMMING, ISoundTekProtocol.paramDimming(100, 50), 0x55)));

        assertEquals(
                "ee1bc878f6112087ae53f29e3cfd6c7e1dd33b2693fd92e4",
                ISoundTekProtocol.bytesToHex(ISoundTekProtocol.buildPacket(
                        TEST_ADDR, 0, 200, ISoundTekProtocol.CMD_FAN_TIMER, ISoundTekProtocol.paramFanTimerMinutes(90), 0x7b)));

        assertEquals(
                "ee1bc878f6112087af53f29e3c79e8fa9921bea21779846a",
                ISoundTekProtocol.bytesToHex(ISoundTekProtocol.buildPacket(
                        TEST_ADDR, 0, 201, ISoundTekProtocol.CMD_FAN_TIMER, ISoundTekProtocol.paramFanTimerMinutes(300), 0xff)));

        assertEquals(
                "ee1bc878f61120876353f29e3c860517a554405de886babf",
                ISoundTekProtocol.bytesToHex(ISoundTekProtocol.buildPacket(
                        TEST_ADDR, 0, 5, ISoundTekProtocol.CMD_RGB, ISoundTekProtocol.paramRgb("#10c3a6"), 0x00)));
    }

    @Test
    public void testAllCommandsRoundTripAndCrcValidation() {
        int[] cmds = new int[] {
                ISoundTekProtocol.CMD_PAIR,
                ISoundTekProtocol.CMD_MASTER_POWER,
                ISoundTekProtocol.CMD_LIGHT_POWER,
                ISoundTekProtocol.CMD_FAN_POWER,
                ISoundTekProtocol.CMD_DIMMING,
                ISoundTekProtocol.CMD_METEOR,
                ISoundTekProtocol.CMD_AUTO_COLOR,
                ISoundTekProtocol.CMD_RGB,
                ISoundTekProtocol.CMD_FAN_SPEED,
                ISoundTekProtocol.CMD_FAN_SHAKE,
                ISoundTekProtocol.CMD_FAN_REVERSAL,
                ISoundTekProtocol.CMD_FAN_MODE,
                ISoundTekProtocol.CMD_AUX_LIGHT,
                ISoundTekProtocol.CMD_LIGHT_TIMER,
                ISoundTekProtocol.CMD_FAN_TIMER
        };
        byte[][] params = new byte[][] {
                ISoundTekProtocol.paramPair(true),
                ISoundTekProtocol.paramBool(false),
                ISoundTekProtocol.paramBool(true),
                ISoundTekProtocol.paramBool(true),
                ISoundTekProtocol.paramDimming(45, 80),
                ISoundTekProtocol.paramBool(true),
                ISoundTekProtocol.paramAutoColor(true),
                ISoundTekProtocol.paramRgb("33aa77"),
                ISoundTekProtocol.paramFanSpeed(6),
                ISoundTekProtocol.paramBool(true),
                ISoundTekProtocol.paramBool(true),
                ISoundTekProtocol.paramFanMode(2),
                ISoundTekProtocol.paramBool(true),
                ISoundTekProtocol.paramLightTimerMinutes(45),
                ISoundTekProtocol.paramFanTimerMinutes(300)
        };

        for (int i = 0; i < cmds.length; i++) {
            byte[] pkt = ISoundTekProtocol.buildPacket(TEST_ADDR, 0, i + 1, cmds[i], params[i], (i * 17) & 0xFF);
            ISoundTekProtocol.DecodedPacket dec = ISoundTekProtocol.decodePacket(pkt);
            assertNotNull(dec);
            assertTrue("CRC must be valid for cmd " + cmds[i], dec.crcValid);
            assertEquals(TEST_ADDR, dec.advAddrHex);
            assertEquals(i + 1, dec.seq);
            assertEquals(cmds[i], dec.cmd);
            assertArrayEquals(params[i], dec.param6);

            byte[] corrupted = pkt.clone();
            corrupted[23] ^= 0x01;
            ISoundTekProtocol.DecodedPacket bad = ISoundTekProtocol.decodePacket(corrupted);
            assertNotNull(bad);
            assertFalse("Corrupted CRC must be detected", bad.crcValid);
        }
    }

    @Test
    public void testAdvAddrValidation() {
        assertTrue(ISoundTekProtocol.isValidAdvAddr("0123abcd"));
        assertTrue(ISoundTekProtocol.isValidAdvAddr("DEADBEEF"));
        assertFalse(ISoundTekProtocol.isValidAdvAddr("123"));
        assertFalse(ISoundTekProtocol.isValidAdvAddr("zzzz1111"));
        assertTrue(ISoundTekProtocol.isValidAdvAddr(ISoundTekProtocol.generateRandomAdvAddr()));
    }
}

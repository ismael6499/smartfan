package com.nitsutech.smartfan.ble;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

/**
 * Exact implementation of the iSoundTek / IntelliBeacon BLE raw advertising protocol
 * (Manufacturer ID 0x5349 / 21321, 24-byte manufacturer payload).
 */
public final class ISoundTekProtocol {

    public static final int MANUFACTURER_ID = 0x5349; // 21321

    public static final int CMD_PAIR = 0x01;
    public static final int CMD_MASTER_POWER = 0x10;
    public static final int CMD_LIGHT_POWER = 0x11;
    public static final int CMD_FAN_POWER = 0x12;
    public static final int CMD_DIMMING = 0x20;
    public static final int CMD_METEOR = 0x22;
    public static final int CMD_AUTO_COLOR = 0x23;
    public static final int CMD_RGB = 0x24;
    public static final int CMD_FAN_SPEED = 0x25;
    public static final int CMD_FAN_SHAKE = 0x26;
    public static final int CMD_FAN_REVERSAL = 0x27;
    public static final int CMD_FAN_MODE = 0x28;
    public static final int CMD_AUX_LIGHT = 0x30;
    public static final int CMD_LIGHT_TIMER = 0x35;
    public static final int CMD_FAN_TIMER = 0x36;

    private static final byte[] HEADER = new byte[] {
            (byte) 0xEE, (byte) 0x1B, (byte) 0xC8, (byte) 0x78, (byte) 0xF6, (byte) 0x11
    };

    private static final int[] ENCODE_TABLE = new int[] {
            33, 7, 102, 242, 64, 93, 232, 134,
            33, 7, 102, 242, 64, 93, 232, 134
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private ISoundTekProtocol() {
    }

    public static final class DecodedPacket {
        public final int seq;
        public final String advAddrHex;
        public final int group;
        public final int cmd;
        public final byte[] param6;
        public final int randByte;
        public final boolean crcValid;

        public DecodedPacket(int seq, String advAddrHex, int group, int cmd, byte[] param6, int randByte, boolean crcValid) {
            this.seq = seq;
            this.advAddrHex = advAddrHex;
            this.group = group;
            this.cmd = cmd;
            this.param6 = param6;
            this.randByte = randByte;
            this.crcValid = crcValid;
        }
    }

    public static String generateRandomAdvAddr() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    public static boolean isValidAdvAddr(String hex) {
        if (hex == null || hex.length() != 8) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            char c = hex.charAt(i);
            boolean isHex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }

    public static byte[] buildPacket(String advAddrHex, int group, int seq, int cmd, byte[] param6) {
        int randByte = RANDOM.nextInt(256);
        return buildPacket(advAddrHex, group, seq, cmd, param6, randByte);
    }

    public static byte[] buildPacket(String advAddrHex, int group, int seq, int cmd, byte[] param6, int randByte) {
        if (!isValidAdvAddr(advAddrHex)) {
            throw new IllegalArgumentException("advAddrHex must be 8 hex chars");
        }
        if (param6 == null || param6.length != 6) {
            throw new IllegalArgumentException("param6 must be exactly 6 bytes");
        }

        byte[] advAddr = hexToBytes(advAddrHex);
        int rand = randByte & 0xFF;

        byte[] plain16 = new byte[16];
        plain16[0] = 0x01;
        plain16[1] = (byte) 0x80;
        plain16[2] = (byte) (seq & 0xFF);
        plain16[3] = advAddr[0];
        plain16[4] = advAddr[1];
        plain16[5] = advAddr[2];
        plain16[6] = advAddr[3];
        plain16[7] = (byte) (group & 0xFF);
        plain16[8] = (byte) (cmd & 0xFF);
        System.arraycopy(param6, 0, plain16, 9, 6);
        plain16[15] = (byte) rand;

        byte[] crc = crc16Ccitt(plain16);

        byte[] encrypted16 = new byte[16];
        for (int i = 0; i < 7; i++) {
            encrypted16[i] = (byte) ((plain16[i] & 0xFF) ^ ENCODE_TABLE[i]);
        }
        for (int i = 7; i < 15; i++) {
            int randXor = (plain16[i] & 0xFF) ^ rand;
            encrypted16[i] = (byte) (randXor ^ ENCODE_TABLE[i]);
        }
        encrypted16[15] = (byte) (rand ^ ENCODE_TABLE[15]);

        byte[] packet = new byte[24];
        System.arraycopy(HEADER, 0, packet, 0, HEADER.length);
        System.arraycopy(encrypted16, 0, packet, 6, 16);
        packet[22] = crc[0];
        packet[23] = crc[1];
        return packet;
    }

    public static DecodedPacket decodePacket(byte[] packet24) {
        if (packet24 == null || packet24.length != 24) {
            return null;
        }
        for (int i = 0; i < HEADER.length; i++) {
            if (packet24[i] != HEADER[i]) {
                return null;
            }
        }
        byte[] tableDec = new byte[16];
        for (int i = 0; i < 16; i++) {
            tableDec[i] = (byte) ((packet24[6 + i] & 0xFF) ^ ENCODE_TABLE[i]);
        }
        if ((tableDec[0] & 0xFF) != 0x01 || (tableDec[1] & 0xFF) != 0x80) {
            return null;
        }
        int seq = tableDec[2] & 0xFF;
        String advAddrHex = bytesToHex(Arrays.copyOfRange(tableDec, 3, 7));
        int rand = tableDec[15] & 0xFF;

        byte[] plain16 = new byte[16];
        System.arraycopy(tableDec, 0, plain16, 0, 7);
        for (int i = 7; i < 15; i++) {
            plain16[i] = (byte) ((tableDec[i] & 0xFF) ^ rand);
        }
        plain16[15] = (byte) rand;

        byte[] expectedCrc = crc16Ccitt(plain16);
        boolean crcValid = expectedCrc[0] == packet24[22] && expectedCrc[1] == packet24[23];
        int group = plain16[7] & 0xFF;
        int cmd = plain16[8] & 0xFF;
        byte[] param6 = Arrays.copyOfRange(plain16, 9, 15);
        return new DecodedPacket(seq, advAddrHex, group, cmd, param6, rand, crcValid);
    }

    public static byte[] crc16Ccitt(byte[] data16) {
        int crc = 0x5555;
        for (int i = 0; i < 16; i++) {
            int revByte = reverse8(data16[i] & 0xFF);
            crc ^= (revByte << 8);
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }
        int rev = reverse16(crc);
        return new byte[] {
                (byte) (rev & 0xFF),
                (byte) ((rev >> 8) & 0xFF)
        };
    }

    private static int reverse8(int value) {
        int res = 0;
        for (int i = 0; i < 8; i++) {
            if ((value & (1 << i)) != 0) {
                res |= (1 << (7 - i));
            }
        }
        return res;
    }

    private static int reverse16(int value) {
        int res = 0;
        for (int i = 0; i < 16; i++) {
            if ((value & (1 << i)) != 0) {
                res |= (1 << (15 - i));
            }
        }
        return res;
    }

    // --- Command parameter builders (all exact hex formats from IntelliBeacon) ---

    public static byte[] paramPair(boolean pair) {
        return new byte[] { (byte) (pair ? 0x01 : 0x00), 0, 0, 0, 0, 0 };
    }

    public static byte[] paramBool(boolean on) {
        return new byte[] { (byte) (on ? 0x01 : 0x00), 0, 0, 0, 0, 0 };
    }

    public static byte[] paramByte0(int value) {
        return new byte[] { (byte) (value & 0xFF), 0, 0, 0, 0, 0 };
    }

    public static byte[] paramDimming(int brightness, int colorTemp) {
        int b = clamp(brightness, 10, 100);
        int ct = clamp(colorTemp, 0, 100);
        return new byte[] { 0x04, (byte) b, (byte) ct, 0, 0, 0 };
    }

    public static byte[] paramFanSpeed(int speed) {
        int s = clamp(speed, 1, 6);
        return new byte[] { (byte) s, 0, 0, 0, 0, 0 };
    }

    public static byte[] paramFanMode(int mode) {
        int m = clamp(mode, 0, 2);
        return new byte[] { (byte) m, 0, 0, 0, 0, 0 };
    }

    public static byte[] paramFanTimerMinutes(int totalMinutes) {
        int mins = clamp(totalMinutes, 0, 1439);
        return new byte[] {
                0x02,
                0x00,
                (byte) (mins & 0xFF),
                (byte) ((mins >> 8) & 0xFF),
                0x00,
                0x00
        };
    }

    public static byte[] paramLightTimerMinutes(int minutes) {
        int mins = clamp(minutes, 0, 60);
        return new byte[] { 0x02, 0x00, (byte) mins, 0x00, 0x00, 0x00 };
    }

    public static byte[] paramAutoColor(boolean on) {
        return new byte[] { (byte) (on ? 0x01 : 0x00), 0x02, 0x01, 0x64, 0x00, 0x00 };
    }

    public static byte[] paramRgb(String rgbHex) {
        String clean = rgbHex == null ? "ff0000" : rgbHex.replace("#", "").trim();
        if (clean.length() != 6) {
            clean = "ff0000";
        }
        byte[] rgb = hexToBytes(clean);
        return new byte[] { rgb[0], rgb[1], rgb[2], 0x00, 0x00, 0x00 };
    }

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }
}

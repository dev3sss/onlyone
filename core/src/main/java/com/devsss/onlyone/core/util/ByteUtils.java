package com.devsss.onlyone.core.util;

public class ByteUtils {

    public static byte[] join(byte[] a, byte[] b) {
        if (b == null) {
            return a;
        }
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    public static byte[] join(byte[] a, byte[] b, byte[] c) {
        byte[] out = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        System.arraycopy(c, 0, out, a.length + b.length, c.length);
        return out;
    }

    public static byte[] join(byte[] a, byte[] b, byte[] c, byte[] d) {
        byte[] out = new byte[a.length + b.length + c.length + d.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        System.arraycopy(c, 0, out, a.length + b.length, c.length);
        System.arraycopy(d, 0, out, a.length + b.length + c.length, d.length);
        return out;
    }


    public static int getInt8(byte[] b) {
        return b[0] & 0xFF;
    }

    public static int getInt16(byte[] b) {
        return (b[1] & 0xFF) |
                ((b[0] & 0xFF) << 8);
    }

    public static int getInt24(byte[] b) {
        return (b[2] & 0xFF) |
                ((b[1] & 0xFF) << 8) |
                ((b[0] & 0xFF) << 16);
    }

    public static int getInt32(byte[] b) {
        return (b[3] & 0xFF) |
                ((b[2] & 0xFF) << 8) |
                ((b[1] & 0xFF) << 16) |
                ((b[0] & 0xFF) << 24);
    }

    public static byte[] getBytes8(int a) {
        byte[] b = new byte[1];
        b[0] = (byte) (a & 0xFF);
        return b;
    }

    public static byte[] getBytes16(int a) {
        byte[] b = new byte[2];
        b[0] = (byte) ((a >> 8) & 0xFF);
        b[1] = (byte) (a & 0xFF);
        return b;
    }

    public static byte[] getBytes24(int a) {
        byte[] b = new byte[3];
        b[0] = (byte) ((a >> 16) & 0xFF);
        b[1] = (byte) ((a >> 8) & 0xFF);
        b[2] = (byte) (a & 0xFF);
        return b;
    }

    public static byte[] getBytes32(int a) {
        byte[] b = new byte[4];
        b[0] = (byte) ((a >> 24) & 0xFF);
        b[1] = (byte) ((a >> 16) & 0xFF);
        b[2] = (byte) ((a >> 8) & 0xFF);
        b[3] = (byte) (a & 0xFF);
        return b;
    }

}

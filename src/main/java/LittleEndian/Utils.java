package LittleEndian;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Conversion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.CharBuffer;

public class Utils {
    public static byte[] getBytes(String path) throws IOException {
        File file = new File(path);
        FileInputStream fileInputStream = new FileInputStream(file);
        return IOUtils.toByteArray(fileInputStream);
    }

    public static int getInt(byte[] bytes, int start, int length) {
        return Conversion.byteArrayToInt(bytes, start, 0, 0, length);
    }

    public static int getInt(byte byteInput) {
        Byte ByteInput = byteInput;
        return ByteInput.intValue();
    }

    public static float getFloat(byte[] bytes, int start, int length) {
        return Float.intBitsToFloat(Conversion.byteArrayToInt(bytes, start, 0, 0, length));
    }

    public static double getDouble(byte[] bytes, int start, int length) {
        return Double.longBitsToDouble(Conversion.byteArrayToLong(bytes, start, 0, 0, length));
    }

    public static String getString(byte[] bytes, int start, int length) {
        CharBuffer charBuffer = CharBuffer.allocate(length);
        String stringOutput;
        for (int i = start; i < start + length; i++) {
            charBuffer.append((char) bytes[i]);
        }
        stringOutput = charBuffer.toString();
        return stringOutput;
    }

    public static boolean[] getFlagBits(byte[] bytes) {
        boolean[] flagBits = new boolean[8];
        return Conversion.byteToBinary(bytes[0], 0, flagBits, 0, 8);
    }


}
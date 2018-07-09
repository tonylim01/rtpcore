package common;

public class StringUtil {
    private static final String STR_OK = "OK";
    private static final String STR_FAIL = "FAIL";

    public static String getOkFail(boolean result) {
        return (result ? STR_OK : STR_FAIL);
    }

    public static boolean isNumeric(String str) {
        return (str != null && str.matches("-?\\d+")) ? true : false;
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for(final byte b: a)
            sb.append(String.format("%02x ", b&0xff));
        return sb.toString();
    }
}

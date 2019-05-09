package com.tag4yu.uhfdemo;

import android.util.Log;

public class Util {
    public static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();
    public static final String TAG = "Dung.LV Util";

    public static String OneByte2Hex(byte b)
    {
        String ret = "";
        ret = "" + HEX_CHARS[b >> 4 & 0x0F] + HEX_CHARS[b & 0x0F];
        return ret;
    }

    public static String ByteArrayToHex(byte[] buffer, int len)
    {
        String ret = "";
        for (int i = 0; i < buffer.length; i++)
        {
            ret = ret + OneByte2Hex(buffer[i]);
        }

        return ret;
    }

    public static boolean CheckSum(String data) {
        boolean ret = false;
        int allsum = 0;
        Log.d(TAG,"CheckSum data= " + data);
        String[] _str = data.split(" ");

        if (_str.length >= 2) {
            for (int i = 1; i < _str.length - 2; i++) {
                allsum = allsum + ToInt(_str[i]);
            }
            String cSum = Integer.toHexString(allsum % 256).toUpperCase();
            if (cSum.length() == 1)
                cSum = "0" + cSum;

            if (cSum.equals(_str[_str.length - 2])) {
                ret = true;
            }
        } else {
            ret = false;
        }
        return ret;
    }

    public static int ToInt(String hex) {
        int ret = Integer.parseInt(hex,16);
//        if ((hex.charAt(0)-'A') >= 0) {
//            ret += (hex.charAt(0)-'A'+10)*16;
//        }else {
//            ret += (hex.charAt(0)-'0')*16;
//        }
//        if ((hex.charAt(1)-'A')>=0) {
//            ss+=hex.charAt(1)-'A'+10;
//        }else {
//            ss+=hex.charAt(1)-'0';
//        }
        return ret;
    }

    public static int ToInt(byte b){
        return (int) b & 0xFF;
    }
}

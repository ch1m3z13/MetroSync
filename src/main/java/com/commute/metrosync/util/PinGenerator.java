package com.commute.metrosync.util;

import java.security.SecureRandom;

public class PinGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateOtp(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public static String generateSafetyPin() {
        return generateOtp(4);
    }
}
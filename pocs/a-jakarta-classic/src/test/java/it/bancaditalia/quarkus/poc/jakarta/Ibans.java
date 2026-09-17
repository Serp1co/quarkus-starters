package it.bancaditalia.quarkus.poc.jakarta;

import java.util.concurrent.atomic.AtomicLong;

/** Syntactically valid Italian IBANs (mod-97 check digits), unique within one JVM. */
public final class Ibans {

    private static final AtomicLong COUNTER = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    private Ibans() {
    }

    public static String next() {
        String bban = "X0100003200" + String.format("%012d", COUNTER.incrementAndGet());
        return "IT" + checkDigits("IT", bban) + bban;
    }

    static String checkDigits(String country, String bban) {
        String rearranged = bban + country + "00";
        int remainder = 0;
        for (int i = 0; i < rearranged.length(); i++) {
            int digit = Character.digit(rearranged.charAt(i), 36);
            remainder = digit < 10 ? (remainder * 10 + digit) % 97 : (remainder * 100 + digit) % 97;
        }
        return String.format("%02d", 98 - remainder);
    }
}

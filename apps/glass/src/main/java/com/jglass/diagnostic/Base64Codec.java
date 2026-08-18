package com.jglass.diagnostic;

final class Base64Codec {
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private Base64Codec() {
    }

    static byte[] decode(String encoded, int maximumBytes) throws IllegalArgumentException {
        if (encoded == null || encoded.length() == 0 || encoded.length() % 4 != 0
                || encoded.length() > ((maximumBytes + 2) / 3) * 4) {
            throw new IllegalArgumentException("Invalid Base64 length");
        }
        int padding = encoded.endsWith("==") ? 2 : encoded.endsWith("=") ? 1 : 0;
        int outputLength = encoded.length() / 4 * 3 - padding;
        if (outputLength < 3 || outputLength > maximumBytes) {
            throw new IllegalArgumentException("Decoded audio is outside its size limit");
        }
        byte[] output = new byte[outputLength];
        int outputIndex = 0;
        for (int index = 0; index < encoded.length(); index += 4) {
            int a = value(encoded.charAt(index));
            int b = value(encoded.charAt(index + 1));
            int c = encoded.charAt(index + 2) == '=' ? 0 : value(encoded.charAt(index + 2));
            int d = encoded.charAt(index + 3) == '=' ? 0 : value(encoded.charAt(index + 3));
            boolean finalGroup = index + 4 == encoded.length();
            if (a < 0 || b < 0 || c < 0 || d < 0
                    || (!finalGroup && (encoded.charAt(index + 2) == '=' || encoded.charAt(index + 3) == '='))
                    || (encoded.charAt(index + 2) == '=' && encoded.charAt(index + 3) != '=')) {
                throw new IllegalArgumentException("Invalid Base64 data");
            }
            int bits = a << 18 | b << 12 | c << 6 | d;
            if (outputIndex < outputLength) output[outputIndex++] = (byte) (bits >> 16);
            if (outputIndex < outputLength) output[outputIndex++] = (byte) (bits >> 8);
            if (outputIndex < outputLength) output[outputIndex++] = (byte) bits;
        }
        return output;
    }

    private static int value(char character) {
        return ALPHABET.indexOf(character);
    }
}

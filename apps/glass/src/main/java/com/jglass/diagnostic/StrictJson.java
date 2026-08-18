package com.jglass.diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON reader used by the API-19 startup protocol. */
final class StrictJson {
    static final class ParseException extends Exception {
        ParseException(String message) {
            super(message);
        }
    }

    private final String source;
    private int position;

    private StrictJson(String source) {
        this.source = source;
    }

    static Object parse(String source) throws ParseException {
        StrictJson reader = new StrictJson(source);
        Object value = reader.readValue();
        reader.skipWhitespace();
        if (reader.position != source.length()) {
            throw reader.error("Trailing content");
        }
        return value;
    }

    private Object readValue() throws ParseException {
        skipWhitespace();
        if (position >= source.length()) {
            throw error("Unexpected end of JSON");
        }
        char next = source.charAt(position);
        if (next == '{') return readObject();
        if (next == '[') return readArray();
        if (next == '"') return readString();
        if (next == 't') return readLiteral("true", Boolean.TRUE);
        if (next == 'f') return readLiteral("false", Boolean.FALSE);
        if (next == 'n') return readLiteral("null", null);
        if (next == '-' || (next >= '0' && next <= '9')) return readNumber();
        throw error("Unexpected value");
    }

    private Map<String, Object> readObject() throws ParseException {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (consume('}')) return result;
        while (true) {
            skipWhitespace();
            if (position >= source.length() || source.charAt(position) != '"') {
                throw error("Object key must be a string");
            }
            String key = readString();
            if (result.containsKey(key)) throw error("Duplicate object key");
            skipWhitespace();
            expect(':');
            result.put(key, readValue());
            skipWhitespace();
            if (consume('}')) return result;
            expect(',');
        }
    }

    private List<Object> readArray() throws ParseException {
        expect('[');
        List<Object> result = new ArrayList<Object>();
        skipWhitespace();
        if (consume(']')) return result;
        while (true) {
            result.add(readValue());
            skipWhitespace();
            if (consume(']')) return result;
            expect(',');
        }
    }

    private String readString() throws ParseException {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (position < source.length()) {
            char character = source.charAt(position++);
            if (character == '"') return result.toString();
            if (character < 0x20) throw error("Control character in string");
            if (character != '\\') {
                result.append(character);
                continue;
            }
            if (position >= source.length()) throw error("Incomplete string escape");
            char escape = source.charAt(position++);
            switch (escape) {
                case '"': result.append('"'); break;
                case '\\': result.append('\\'); break;
                case '/': result.append('/'); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'u': result.append(readUnicodeEscape()); break;
                default: throw error("Unsupported string escape");
            }
        }
        throw error("Unterminated string");
    }

    private char readUnicodeEscape() throws ParseException {
        if (position + 4 > source.length()) throw error("Incomplete Unicode escape");
        int value = 0;
        for (int count = 0; count < 4; count++) {
            int digit = Character.digit(source.charAt(position++), 16);
            if (digit < 0) throw error("Invalid Unicode escape");
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private Object readLiteral(String literal, Object value) throws ParseException {
        if (!source.regionMatches(position, literal, 0, literal.length())) {
            throw error("Invalid literal");
        }
        position += literal.length();
        return value;
    }

    private Number readNumber() throws ParseException {
        int start = position;
        consume('-');
        if (consume('0')) {
            if (position < source.length() && Character.isDigit(source.charAt(position))) {
                throw error("Leading zero in number");
            }
        } else {
            readDigits();
        }
        if (consume('.')) readDigits();
        if (position < source.length() && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
            position++;
            if (position < source.length() && (source.charAt(position) == '+' || source.charAt(position) == '-')) position++;
            readDigits();
        }
        try {
            return Double.valueOf(source.substring(start, position));
        } catch (NumberFormatException invalid) {
            throw error("Invalid number");
        }
    }

    private void readDigits() throws ParseException {
        int start = position;
        while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
        if (position == start) throw error("Number requires digits");
    }

    private boolean consume(char expected) {
        if (position < source.length() && source.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char expected) throws ParseException {
        if (!consume(expected)) throw error("Expected '" + expected + "'");
    }

    private void skipWhitespace() {
        while (position < source.length()) {
            char character = source.charAt(position);
            if (character != ' ' && character != '\n' && character != '\r' && character != '\t') return;
            position++;
        }
    }

    private ParseException error(String message) {
        return new ParseException(message + " at character " + position);
    }
}

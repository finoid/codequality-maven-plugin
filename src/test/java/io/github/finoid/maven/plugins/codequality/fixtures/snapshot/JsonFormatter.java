package io.github.finoid.maven.plugins.codequality.fixtures.snapshot;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Inspired by com.jayway.jsonpath.internal.JsonFormatter.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("MissingSwitchDefault")
public class JsonFormatter {
    private static final String INDENT = "  ";
    private static final String NEW_LINE = "\n";

    private static final int MODE_SINGLE = 100;
    private static final int MODE_DOUBLE = 101;
    private static final int MODE_ESCAPE_SINGLE = 102;
    private static final int MODE_ESCAPE_DOUBLE = 103;
    private static final int MODE_BETWEEN = 104;

    private static void appendIndent(StringBuilder sb, int count) {
        for (; count > 0; --count) {
            sb.append(INDENT);
        }
    }

    public static String prettyPrint(String input) {
        input = input.replaceAll("[\\r\\n]", "");

        StringBuilder output = new StringBuilder(input.length() * 2);
        int mode = MODE_BETWEEN;
        int depth = 0;

        for (int i = 0; i < input.length(); ++i) {
            char ch = input.charAt(i);

            switch (mode) {
                case MODE_BETWEEN:
                    switch (ch) {
                        case '{':
                        case '[':
                            output.append(ch);
                            output.append(NEW_LINE);
                            appendIndent(output, ++depth);
                            break;
                        case '}':
                        case ']':
                            output.append(NEW_LINE);
                            appendIndent(output, --depth);
                            output.append(ch);
                            break;
                        case ',':
                            output.append(ch);
                            output.append(NEW_LINE);
                            appendIndent(output, depth);
                            break;
                        case ':':
                            output.append(" : ");
                            break;
                        case '\'':
                            output.append(ch);
                            mode = MODE_SINGLE;
                            break;
                        case '"':
                            output.append(ch);
                            mode = MODE_DOUBLE;
                            break;
                        case ' ':
                            break;
                        default:
                            output.append(ch);
                            break;
                    }
                    break;
                case MODE_ESCAPE_SINGLE:
                    output.append(ch);
                    mode = MODE_SINGLE;
                    break;
                case MODE_ESCAPE_DOUBLE:
                    output.append(ch);
                    mode = MODE_DOUBLE;
                    break;
                case MODE_SINGLE:
                    output.append(ch);
                    switch (ch) {
                        case '\'':
                            mode = MODE_BETWEEN;
                            break;
                        case '\\':
                            mode = MODE_ESCAPE_SINGLE;
                            break;
                    }
                    break;
                case MODE_DOUBLE:
                    output.append(ch);
                    switch (ch) {
                        case '"':
                            mode = MODE_BETWEEN;
                            break;
                        case '\\':
                            mode = MODE_ESCAPE_DOUBLE;
                            break;
                    }
                    break;
            }
        }
        return output.toString();
    }
}

package io.github.itech_mm.apiro.spring.webmvc;

import java.util.Comparator;

final class CodePoints {
    static final Comparator<String> ORDER = CodePoints::compare;

    private CodePoints() {
    }

    static int compare(String left, String right) {
        int i = 0;
        while (i < left.length() && i < right.length()) {
            int a = left.codePointAt(i);
            int b = right.codePointAt(i);
            if (a != b) {
                return a < b ? -1 : 1;
            }
            i += Character.charCount(a);
        }
        return left.length() - right.length();
    }
}

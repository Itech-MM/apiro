package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.model.RequestSettings;
import java.math.BigInteger;
import java.util.regex.Pattern;

public final class RequestSettingsParser {

    private static final Pattern WHOLE = Pattern.compile("-?\\d+");

    private static final Pattern BAD_HOST = Pattern.compile(".*[\\s/].*");

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private static final BigInteger PORT_MAX = BigInteger.valueOf(65535);

    private RequestSettingsParser() {
    }

    public record Result(RequestSettings settings, String problem) {

        public boolean valid() {
            return problem.isEmpty();
        }
    }

    public static Result parse(String connect, String socket, Boolean follow, String maxRedirects,
                               String host, String port) {
        String connectText = trim(connect);
        String socketText = trim(socket);
        String redirectText = trim(maxRedirects);
        String hostText = trim(host);
        String portText = trim(port);
        String problem = firstProblem(
                nonNegative("Connect timeout", connectText),
                nonNegative("Socket timeout", socketText),
                nonNegative("Max redirects", redirectText),
                hostProblem(hostText),
                portProblem(portText),
                missingPort(hostText, portText));
        if (!problem.isEmpty()) {
            return new Result(null, problem);
        }
        boolean proxy = !hostText.isEmpty();
        RequestSettings settings = new RequestSettings(
                integer(connectText), integer(socketText), follow, integer(redirectText),
                proxy ? hostText : null, proxy ? integer(portText) : null);
        return new Result(settings, "");
    }

    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private static Integer integer(String text) {
        return text.isEmpty() ? null : Integer.valueOf(text);
    }

    private static String firstProblem(String... problems) {
        for (String problem : problems) {
            if (!problem.isEmpty()) {
                return problem;
            }
        }
        return "";
    }

    private static String nonNegative(String label, String text) {
        if (text.isEmpty()) {
            return "";
        }
        if (!WHOLE.matcher(text).matches()) {
            return label + " must be a whole number.";
        }
        BigInteger number = new BigInteger(text);
        if (number.signum() < 0) {
            return label + " must not be negative.";
        }
        return number.compareTo(INT_MAX) > 0 ? label + " is too large." : "";
    }

    private static String hostProblem(String host) {
        return BAD_HOST.matcher(host).matches()
                ? "Proxy host must be a host name or address, without spaces or slashes." : "";
    }

    private static String portProblem(String port) {
        if (port.isEmpty()) {
            return "";
        }
        if (!WHOLE.matcher(port).matches()) {
            return "Proxy port must be a whole number.";
        }
        BigInteger number = new BigInteger(port);
        boolean inRange = number.compareTo(BigInteger.ONE) >= 0 && number.compareTo(PORT_MAX) <= 0;
        return inRange ? "" : "Proxy port must be between 1 and 65535.";
    }

    private static String missingPort(String host, String port) {
        return !host.isEmpty() && port.isEmpty() ? "Proxy port is required when a proxy host is set." : "";
    }
}
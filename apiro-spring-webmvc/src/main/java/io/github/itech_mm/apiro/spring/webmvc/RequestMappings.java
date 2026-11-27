package io.github.itech_mm.apiro.spring.webmvc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.MediaTypeExpression;
import org.springframework.web.servlet.mvc.condition.NameValueExpression;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

public final class RequestMappings {
    private RequestMappings() {
    }

    public static List<MappingDescription> describe(Map<RequestMappingInfo, HandlerMethod> mappings) {
        List<MappingDescription> result = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.entrySet()) {
            result.add(describe(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static MappingDescription describe(RequestMappingInfo info, HandlerMethod handler) {
        MappingDescription description = new MappingDescription(handler);
        description.getPatterns().addAll(info.getPatternValues());
        for (RequestMethod method : info.getMethodsCondition().getMethods()) {
            description.getMethods().add(method.name());
        }
        media("consumes", info.getConsumesCondition().getExpressions(), description.getConsumes(),
                description.getUnsupported());
        media("produces", info.getProducesCondition().getExpressions(), description.getProduces(),
                description.getUnsupported());
        for (NameValueExpression<String> expression : info.getParamsCondition().getExpressions()) {
            description.getParams().add(text(expression));
        }
        for (NameValueExpression<String> expression : info.getHeadersCondition().getExpressions()) {
            description.getHeaders().add(text(expression));
        }
        Object custom = info.getCustomCondition();
        if (custom != null) {
            description.getUnsupported().add(custom.getClass().getSimpleName());
        }
        Object version = versionCondition(info);
        if (version != null) {
            description.getUnsupported().add(version.getClass().getSimpleName());
        }
        return description;
    }

    private static void media(String name, Set<MediaTypeExpression> expressions, Set<String> types,
                              Set<String> unsupported) {
        for (MediaTypeExpression expression : expressions) {
            if (expression.isNegated()) {
                unsupported.add(name + "=!" + expression.getMediaType());
            } else {
                types.add(expression.getMediaType().toString());
            }
        }
    }

    private static String text(NameValueExpression<String> expression) {
        if (expression.getValue() == null) {
            return (expression.isNegated() ? "!" : "") + expression.getName();
        }
        return expression.getName() + (expression.isNegated() ? "!=" : "=") + expression.getValue();
    }

    private static Object versionCondition(RequestMappingInfo info) {
        try {
            Method getter = RequestMappingInfo.class.getMethod("getVersionCondition");
            Object condition = getter.invoke(info);
            if (condition == null) {
                return null;
            }
            Object empty = condition.getClass().getMethod("isEmpty").invoke(condition);
            return Boolean.TRUE.equals(empty) ? null : condition;
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read the version condition of " + info, error);
        }
    }
}

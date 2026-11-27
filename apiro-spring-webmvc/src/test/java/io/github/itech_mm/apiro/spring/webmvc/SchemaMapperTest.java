package io.github.itech_mm.apiro.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.itech_mm.apiro.spec.Schema;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

class SchemaMapperTest {
    static class Base<T> {
        public T id;
    }

    static class Node extends Base<Long> {
        @JsonProperty("child_nodes")
        private List<Node> children;
        private String secret;
        private String cache;

        public List<Node> getChildren() {
            return children;
        }

        @JsonIgnore
        public String getSecret() {
            return secret;
        }
    }

    @Test
    void followsInheritanceCyclesAndJacksonNames() {
        SchemaMapper mapper = new SchemaMapper(new LinkedHashSet<>());

        Schema root = mapper.map(ResolvableType.forClass(Node.class));

        Schema node = mapper.schemas().get(root.getRef());
        List<String> names = new ArrayList<>(node.getProperties().keySet());
        assertEquals("[id, child_nodes]", names.toString());
        assertEquals("int64", node.getProperties().get("id").getFormat());
        assertEquals(root.getRef(), node.getProperties().get("child_nodes").getItems().getRef());
    }
}

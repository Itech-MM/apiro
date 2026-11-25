package io.github.itech_mm.apiro.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectItemTest {

    @Test
    void requestHasDefaults() {
        RequestItem request = new RequestItem(null, null, null, null, null, null, null, null, null);
        assertFalse(request.id().isBlank());
        assertEquals(HttpMethod.GET, request.method());
        assertEquals("", request.url());
        assertEquals(BodyType.NONE, request.body().type());
        assertNull(request.auth());
        assertNull(request.settings());
    }

    @Test
    void folderFindsNestedItemById() {
        RequestItem deep = RequestItem.of("Get order", HttpMethod.GET, "{{baseUrl}}/orders/1");
        Folder inner = Folder.of("Orders", List.of(deep));
        Folder root = Folder.of("Shop", List.of(inner));
        assertEquals(deep, root.find(deep.id()).orElseThrow());
        assertEquals(inner, root.find(inner.id()).orElseThrow());
        assertTrue(root.find("missing").isEmpty());
    }

    @Test
    void folderInheritsByDefault() {
        Folder folder = Folder.of("Users", List.of());
        assertNull(folder.auth());
        assertNull(folder.settings());
        assertTrue(folder.headers().isEmpty());
        assertTrue(folder.variables().isEmpty());
    }
}
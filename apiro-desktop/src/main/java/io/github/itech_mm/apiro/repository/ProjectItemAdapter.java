package io.github.itech_mm.apiro.repository;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import java.lang.reflect.Type;
import java.util.Map;

final class ProjectItemAdapter implements JsonSerializer<ProjectItem>, JsonDeserializer<ProjectItem> {

    private static final String TYPE = "type";
    private static final String FOLDER = "folder";
    private static final String REQUEST = "request";
    private static final String SCENARIO = "scenario";

    @Override
    public JsonElement serialize(ProjectItem item, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject body = context.serialize(item, item.getClass()).getAsJsonObject();
        JsonObject result = new JsonObject();
        result.addProperty(TYPE, typeOf(item));
        for (Map.Entry<String, JsonElement> entry : body.entrySet()) {
            result.add(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @Override
    public ProjectItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject object = json.getAsJsonObject();
        JsonElement kind = object.get(TYPE);
        if (kind == null || kind.isJsonNull()) {
            throw new JsonParseException("Project item is missing \"type\"");
        }
        return switch (kind.getAsString()) {
            case FOLDER -> context.deserialize(object, Folder.class);
            case REQUEST -> context.deserialize(object, RequestItem.class);
            case SCENARIO -> context.deserialize(object, ScenarioItem.class);
            default -> throw new JsonParseException("Unknown project item type: " + kind.getAsString());
        };
    }

    private static String typeOf(ProjectItem item) {
        if (item instanceof Folder) {
            return FOLDER;
        }
        if (item instanceof ScenarioItem) {
            return SCENARIO;
        }
        return REQUEST;
    }
}
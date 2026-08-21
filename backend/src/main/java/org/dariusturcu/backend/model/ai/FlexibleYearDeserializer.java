package org.dariusturcu.backend.model.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlexibleYearDeserializer extends ValueDeserializer<Integer> {
    private static final Pattern YEAR_PATTERN = Pattern.compile("-?\\d{1,4}");

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        String raw = p.getString().trim();
        Matcher matcher = YEAR_PATTERN.matcher(raw);
        // No plausible year found, null instead of silently defaulting to a wrong one.
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }
}

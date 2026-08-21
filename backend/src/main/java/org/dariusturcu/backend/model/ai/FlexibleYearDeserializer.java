package org.dariusturcu.backend.model.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlexibleYearDeserializer extends JsonDeserializer<Integer> {
    private static final Pattern YEAR_PATTERN = Pattern.compile("-?\\d{1,4}");

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String raw = p.getText().trim();
        Matcher matcher = YEAR_PATTERN.matcher(raw);
        // No plausible year found, null instead of silently defaulting to a wrong one.
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }
}
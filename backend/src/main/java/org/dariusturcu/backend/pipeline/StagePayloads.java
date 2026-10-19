package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.ai.AiServiceResolveResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class StagePayloads {
    private static final String IDENTIFIED = "IDENTIFIED";
    private static final String SUCCESS = "SUCCESS";
    private static final String LOCK_SOURCE = "musicbrainz+discogs+wikidata-lock";
    private final ObjectMapper mapper;

    public StagePayloads(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<PipelineStage, JsonNode> results(List<PipelineJob> jobs) {
        Map<PipelineStage, JsonNode> results = new EnumMap<>(PipelineStage.class);
        jobs.stream().filter(job -> job.resultJson() != null)
                .forEach(job -> results.put(job.stage(), mapper.readTree(job.resultJson())));
        return results;
    }

    public JsonNode request(PipelineRun run, PipelineStage stage, List<PipelineJob> jobs) {
        ObjectNode request = mapper.createObjectNode().put("youtubeId", run.youtubeId());
        if (stage == PipelineStage.YOUTUBE_METADATA) {
            return request;
        }
        Map<PipelineStage, JsonNode> results = results(jobs);
        JsonNode identification = results.get(PipelineStage.YOUTUBE_METADATA);
        request.set("title", identification.required("title"));
        request.set("artists", identification.required("artists"));
        if (stage == PipelineStage.LLM_RECONCILIATION) {
            request.set("featuredArtists", identification.required("featuredArtists"));
            request.set("color", identification.required("color"));
            request.set("video", identification.required("video"));
            request.set("musicbrainzCandidates", results.get(PipelineStage.MUSICBRAINZ).required("candidates"));
            request.set("discogsCandidates", results.get(PipelineStage.DISCOGS).required("candidates"));
            request.set("wikidataCandidates", results.get(PipelineStage.WIKIDATA).required("candidates"));
            request.set("wikipediaCandidates", results.get(PipelineStage.WIKIPEDIA).required("candidates"));
            request.set("sitelinksCount", results.get(PipelineStage.WIKIDATA).path("sitelinksCount"));
        }
        return request;
    }

    public boolean youtubeFinished(JsonNode result) {
        return !IDENTIFIED.equals(result.required("outcome").asText());
    }

    public boolean rejected(JsonNode result) {
        return "REJECTED".equals(result.path("outcome").asText());
    }

    public SongMetadataResponse finalMetadata(List<PipelineJob> jobs) {
        Map<PipelineStage, JsonNode> results = results(jobs);
        JsonNode identification = results.get(PipelineStage.YOUTUBE_METADATA);
        if (youtubeFinished(identification)) {
            return metadata(identification.required("result"));
        }
        JsonNode reconciled = results.get(PipelineStage.LLM_RECONCILIATION);
        if (reconciled != null) {
            return metadata(reconciled);
        }
        Integer year = mapper.treeToValue(results.get(PipelineStage.MUSICBRAINZ).required("earliestYear"), Integer.class);
        return new SongMetadataResponse(identification.required("title").asText(),
                strings(identification.required("artists")), strings(identification.required("featuredArtists")),
                year, identification.required("color").asText(), "high", LOCK_SOURCE,
                "All three structured sources (MusicBrainz, Discogs, Wikidata) agree on " + year
                        + ". Locked without Wikipedia extraction or year reconciliation.",
                "VERIFIED", nullableInteger(results.get(PipelineStage.WIKIDATA).path("sitelinksCount")),
                nullableInteger(identification.required("video").path("durationSeconds")), null);
    }

    private Integer nullableInteger(JsonNode value) {
        return value.isNumber() ? value.asInt() : null;
    }

    private List<String> strings(JsonNode values) {
        return mapper.readerForListOf(String.class).readValue(values);
    }

    private SongMetadataResponse metadata(JsonNode result) {
        AiServiceResolveResponse response = mapper.treeToValue(result, AiServiceResolveResponse.class);
        if (!SUCCESS.equals(response.status()) || response.content() == null) {
            throw new IllegalStateException("AI stage returned no final metadata");
        }
        var content = response.content();
        return new SongMetadataResponse(content.title(), content.mainArtists(), content.featuredArtists(),
                content.releaseYear(), content.color(), content.confidence(), content.source(), content.reasoning(),
                content.verificationStatus(), content.sitelinksCount(), content.durationSeconds(), content.canonicalSongId());
    }
}

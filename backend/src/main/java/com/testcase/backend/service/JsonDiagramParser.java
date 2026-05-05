package com.testcase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testcase.backend.entity.Actor;
import com.testcase.backend.entity.Relationship;
import com.testcase.backend.entity.UseCase;
import com.testcase.backend.entity.UseCaseDiagram;
import com.testcase.backend.enums.ActorType;
import com.testcase.backend.enums.RelationType;
import com.testcase.backend.enums.UseCasePriority;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsonDiagramParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void parse(InputStream inputStream, UseCaseDiagram diagram) throws Exception {
        JsonNode root = objectMapper.readTree(inputStream);

        // Parse actors
        JsonNode actorsNode = root.path("actors");
        if (actorsNode.isArray()) {
            for (JsonNode a : actorsNode) {
                Actor actor = new Actor();
                actor.setXmiId(a.path("xmiId").asText("A" + System.nanoTime()));
                actor.setName(a.path("name").asText("Unknown Actor"));
                actor.setDescription(a.path("description").asText(null));

                String typeStr = a.path("actorType").asText("PRIMARY");
                try {
                    actor.setActorType(ActorType.valueOf(typeStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    actor.setActorType(ActorType.PRIMARY);
                }

                diagram.addActor(actor);
            }
        }

        // Parse useCases
        JsonNode useCasesNode = root.path("useCases");
        if (useCasesNode.isArray()) {
            for (JsonNode uc : useCasesNode) {
                UseCase useCase = new UseCase();
                useCase.setXmiId(uc.path("xmiId").asText("UC" + System.nanoTime()));
                useCase.setName(uc.path("name").asText("Unknown UseCase"));
                useCase.setDescription(uc.path("description").asText(null));

                // preconditions
                List<String> pre = new ArrayList<>();
                JsonNode preNode = uc.path("preconditions");
                if (preNode.isArray()) {
                    for (JsonNode p : preNode)
                        pre.add(p.asText());
                }
                useCase.setPreconditions(pre);

                // postconditions
                List<String> post = new ArrayList<>();
                JsonNode postNode = uc.path("postconditions");
                if (postNode.isArray()) {
                    for (JsonNode p : postNode)
                        post.add(p.asText());
                }
                useCase.setPostconditions(post);

                // priority
                String priorityStr = uc.path("priority").asText("MEDIUM");
                try {
                    useCase.setPriority(UseCasePriority.valueOf(priorityStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    useCase.setPriority(UseCasePriority.MEDIUM);
                }

                diagram.addUseCase(useCase);
            }
        }

        // Parse relationships
        JsonNode relsNode = root.path("relationships");
        if (relsNode.isArray()) {
            for (JsonNode r : relsNode) {
                Relationship rel = new Relationship();
                rel.setSourceXmiId(r.path("sourceXmiId").asText());
                rel.setTargetXmiId(r.path("targetXmiId").asText());

                String typeStr = r.path("type").asText("ASSOCIATION");
                try {
                    rel.setType(RelationType.valueOf(typeStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    rel.setType(RelationType.ASSOCIATION);
                }

                String label = r.path("label").asText(null);
                if (label != null && !label.isBlank())
                    rel.setLabel(label);

                diagram.addRelationship(rel);
            }
        }
    }
}
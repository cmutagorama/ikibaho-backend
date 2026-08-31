package com.charlie.ikibaho.project;

import java.util.Optional;
import java.util.UUID;

public interface IssueContextResolver {
    Optional<IssueContext> resolve(UUID issueId);
}

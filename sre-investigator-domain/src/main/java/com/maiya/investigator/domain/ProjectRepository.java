package com.maiya.investigator.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    InvestigatorProject save(InvestigatorProject project);

    List<InvestigatorProject> findAll();

    Optional<InvestigatorProject> findById(UUID id);

    void delete(UUID id);
}

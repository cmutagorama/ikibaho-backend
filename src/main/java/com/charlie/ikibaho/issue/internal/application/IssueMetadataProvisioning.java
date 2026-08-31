package com.charlie.ikibaho.issue.internal.application;

import com.charlie.ikibaho.issue.internal.domain.IssueType;
import com.charlie.ikibaho.issue.internal.domain.Status;
import com.charlie.ikibaho.issue.internal.domain.StatusCategory;
import com.charlie.ikibaho.issue.internal.persistence.IssueTypeRepository;
import com.charlie.ikibaho.issue.internal.persistence.StatusRepository;
import com.charlie.ikibaho.workflow.WorkflowProvisioning;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IssueMetadataProvisioning {
    private final StatusRepository statuses;
    private final IssueTypeRepository issueTypes;
    private final WorkflowProvisioning workflowProvisioning;

    IssueMetadataProvisioning(StatusRepository statuses, IssueTypeRepository issueTypes, WorkflowProvisioning workflowProvisioning) {
        this.statuses = statuses;
        this.issueTypes = issueTypes;
        this.workflowProvisioning = workflowProvisioning;
    }

    /**
     * Idempotent. Called before the first issue is created in an org, because
     * the project module cannot write to issue-owned tables.
     */
    @Transactional
    public void ensureDefaults(UUID organizationId) {
        if (statuses.existsByOrganizationId(organizationId)) {
            return;
        }
        statuses.save(new Status(organizationId, "To Do", StatusCategory.TODO, 1));
        statuses.save(new Status(organizationId, "In Progress", StatusCategory.IN_PROGRESS, 2));
        statuses.save(new Status(organizationId, "Done", StatusCategory.DONE, 3));

        List<WorkflowProvisioning.StatusRef> refs = statuses
                .findByOrganizationIdOrderByPositionAsc(organizationId).stream()
                .map(s -> new WorkflowProvisioning.StatusRef(s.getId(), s.getName(), s.getPosition()))
                .toList();
        workflowProvisioning.ensureDefaultWorkflow(organizationId, refs);

        issueTypes.save(new IssueType(organizationId, "Epic", IssueType.LEVEL_EPIC));
        issueTypes.save(new IssueType(organizationId, "Story", IssueType.LEVEL_STANDARD));
        issueTypes.save(new IssueType(organizationId, "Task", IssueType.LEVEL_STANDARD));
        issueTypes.save(new IssueType(organizationId, "Bug", IssueType.LEVEL_STANDARD));
        issueTypes.save(new IssueType(organizationId, "Sub-task", IssueType.LEVEL_SUBTASK));
    }
}

package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.ProjectMemberRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectMemberResponse;
import com.pablomarotta.smart_task_manager.service.ProjectMembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
@Slf4j
public class ProjectMembershipController {

    private final ProjectMembershipService membershipService;

    @GetMapping
    public List<ProjectMemberResponse> listMembers(
            @PathVariable Long projectId,
            Principal principal
    ) {
        log.info("Listing members for project: {}", projectId);
        return membershipService.listMembers(projectId, principal.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse addMember(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectMemberRequest request,
            Principal principal
    ) {
        log.info("Adding member to project: {}", projectId);
        return membershipService.addMember(projectId, request, principal.getName());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            Principal principal
    ) {
        log.info("Removing member {} from project: {}", userId, projectId);
        membershipService.removeMember(projectId, userId, principal.getName());
    }
}

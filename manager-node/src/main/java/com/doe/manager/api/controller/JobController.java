package com.doe.manager.api.controller;

import com.doe.core.model.JobStatus;
import com.doe.manager.api.dto.JobRequest;
import com.doe.manager.api.dto.JobResponse;
import com.doe.manager.api.service.JobService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse submitJob(@RequestBody JobRequest request) {
        return jobService.submitJob(request);
    }

    @GetMapping
    public Page<JobResponse> listJobs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) JobStatus status) {
        return jobService.listJobs(page, size, status);
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable("id") UUID id) {
        return jobService.getJob(id);
    }
}

package com.doe.manager.api.controller;

import com.doe.manager.api.service.JobService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logs/jobs")
public class JobLogController {

    private final JobService jobService;

    public JobLogController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getJobLogs(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(jobService.getJobLogs(id));
    }
}

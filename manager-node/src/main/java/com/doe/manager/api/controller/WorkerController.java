package com.doe.manager.api.controller;

import com.doe.manager.api.dto.WorkerResponse;
import com.doe.manager.api.service.WorkerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workers")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @GetMapping
    public List<WorkerResponse> listWorkers() {
        return workerService.listWorkers();
    }

    @GetMapping("/{workerId}")
    public WorkerResponse getWorker(@PathVariable("workerId") UUID workerId) {
        return workerService.getWorker(workerId);
    }
}

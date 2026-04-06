package com.doe.manager.api.service;

import com.doe.manager.api.dto.WorkerResponse;
import com.doe.manager.persistence.entity.WorkerEntity;
import com.doe.manager.persistence.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkerService {

    private final WorkerRepository workerRepository;

    public WorkerService(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }
    
    // we dont need pagination for workers since we expect a small number of them, but we can easily add it later if needed
    @Transactional(readOnly = true)
    public List<WorkerResponse> listWorkers() {
        return workerRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private WorkerResponse mapToResponse(WorkerEntity entity) {
        return new WorkerResponse(
                entity.getId(),
                entity.getHostname(),
                entity.getIpAddress(),
                entity.getStatus(),
                entity.getLastHeartbeat()
        );
    }
}

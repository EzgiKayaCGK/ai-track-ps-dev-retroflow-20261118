package com.stackcraft.retroflow.service;

import com.stackcraft.retroflow.dto.RetrospectiveResponse;
import com.stackcraft.retroflow.exception.ServiceNotImplementedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrospectiveService {

    public RetrospectiveResponse createRetrospective(Long teamId, String title, String requester) {
        throw new ServiceNotImplementedException("Retrospective creation is not implemented");
    }

    public List<RetrospectiveResponse> listRetrospectives(Long teamId) {
        throw new ServiceNotImplementedException("Retrospective listing is not implemented");
    }

    public RetrospectiveResponse closeRetrospective(Long id, String requester) {
        throw new ServiceNotImplementedException("Retrospective closure is not implemented");
    }
}

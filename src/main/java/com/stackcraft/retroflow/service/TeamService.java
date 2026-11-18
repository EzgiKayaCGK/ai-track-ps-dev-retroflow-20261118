package com.stackcraft.retroflow.service;

import com.stackcraft.retroflow.dto.TeamResponse;
import com.stackcraft.retroflow.exception.ServiceNotImplementedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeamService {

    public TeamResponse createTeam(String name, List<String> members) {
        throw new ServiceNotImplementedException("Team creation is not implemented");
    }

    public TeamResponse getTeam(Long id) {
        throw new ServiceNotImplementedException("Team retrieval is not implemented");
    }
}

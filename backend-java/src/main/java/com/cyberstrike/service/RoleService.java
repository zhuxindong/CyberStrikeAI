package com.cyberstrike.service;

import com.cyberstrike.dto.RoleDTO;
import com.cyberstrike.entity.RoleEntity;
import com.cyberstrike.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public List<RoleDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public RoleDTO getRole(String name) {
        return roleRepository.findByName(name)
                .map(this::convertToDTO)
                .orElse(null);
    }

    public RoleDTO getRoleById(Integer id) {
        return roleRepository.findById(id)
                .map(this::convertToDTO)
                .orElse(null);
    }

    @Transactional
    public RoleDTO createRole(RoleDTO request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("角色已存在: " + request.getName());
        }

        RoleEntity entity = RoleEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .userPrompt(request.getUserPrompt())
                .icon(request.getIcon() != null ? request.getIcon() : "📁")
                .tools(request.getTools())
                .enabled(true)
                .build();

        RoleEntity saved = roleRepository.save(entity);
        log.info("创建角色: {}", saved.getName());

        return convertToDTO(saved);
    }

    @Transactional
    public RoleDTO updateRole(String name, RoleDTO request) {
        RoleEntity entity = roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + name));

        String newName = request.getName();
        if (newName != null && !newName.equals(name)) {
            if (roleRepository.existsByName(newName)) {
                throw new IllegalArgumentException("角色名称已存在: " + newName);
            }
            entity.setName(newName);
        }

        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getUserPrompt() != null) {
            entity.setUserPrompt(request.getUserPrompt());
        }
        if (request.getIcon() != null) {
            entity.setIcon(request.getIcon());
        }
        if (request.getTools() != null) {
            entity.setTools(request.getTools());
        }
        entity.setEnabled(request.isEnabled());

        RoleEntity saved = roleRepository.save(entity);
        log.info("更新角色: {} -> {}", name, saved.getName());

        return convertToDTO(saved);
    }

    @Transactional
    public void deleteRole(String name) {
        RoleEntity entity = roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + name));

        roleRepository.delete(entity);
        log.info("删除角色: {}", name);
    }

    private RoleDTO convertToDTO(RoleEntity entity) {
        return RoleDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .userPrompt(entity.getUserPrompt())
                .icon(entity.getIcon())
                .tools(entity.getTools())
                .enabled(entity.isEnabled())
                .build();
    }
}
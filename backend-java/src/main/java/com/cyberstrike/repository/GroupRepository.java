package com.cyberstrike.repository;

import com.cyberstrike.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, String> {

    List<Group> findAllByOrderByPinnedDescCreatedAtDesc();
}

package com.cyberstrike.repository;

import com.cyberstrike.entity.ChatCompletionMessageDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatCompletionMessageRepository extends JpaRepository<ChatCompletionMessageDO, Long> {

    List<ChatCompletionMessageDO> findByConversationIdOrderByCreateTimeAscIdAsc(String conversationId);

}

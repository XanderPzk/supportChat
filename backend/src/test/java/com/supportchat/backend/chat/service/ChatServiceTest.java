package com.supportchat.backend.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.supportchat.backend.chat.api.MessagePayload;
import com.supportchat.backend.chat.api.SendPayload;
import com.supportchat.backend.chat.model.SenderRole;
import com.supportchat.backend.chat.repo.MessageRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageRepository messageRepository;

    @Test
    void duplicateClientMsgIdShouldNotCreateSecondMessage() {
        String conversationId = "conv-dedup";
        String clientMsgId = UUID.randomUUID().toString();

        AppendResult first = chatService.appendMessage(
                conversationId,
                new SendPayload(clientMsgId, "Hola", SenderRole.CUSTOMER, "Cliente")
        );
        AppendResult second = chatService.appendMessage(
                conversationId,
                new SendPayload(clientMsgId, "Hola", SenderRole.CUSTOMER, "Cliente")
        );

        assertThat(first.message().getId()).isEqualTo(second.message().getId());
        assertThat(first.message().getSeq()).isEqualTo(second.message().getSeq());
        assertThat(messageRepository.findByConversationIdOrderBySeqAsc(conversationId)).hasSize(1);
    }

    @Test
    void concurrentMessagesShouldGetContiguousSequence() throws Exception {
        String conversationId = "conv-concurrent";
        int count = 8;

        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Callable<AppendResult>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                tasks.add(() -> chatService.appendMessage(
                        conversationId,
                        new SendPayload(UUID.randomUUID().toString(), "m-" + index, SenderRole.AGENT, "Agent")
                ));
            }

            List<Future<AppendResult>> futures = pool.invokeAll(tasks);
            List<Long> seqs = new ArrayList<>();
            for (Future<AppendResult> future : futures) {
                seqs.add(future.get().message().getSeq());
            }

            Set<Long> unique = seqs.stream().collect(Collectors.toSet());
            assertThat(unique).hasSize(count);
            assertThat(unique).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void syncShouldReturnOnlyMessagesAfterLastSeq() {
        String conversationId = "conv-sync";
        chatService.appendMessage(
                conversationId,
                new SendPayload(UUID.randomUUID().toString(), "uno", SenderRole.CUSTOMER, "Cliente")
        );
        chatService.appendMessage(
                conversationId,
                new SendPayload(UUID.randomUUID().toString(), "dos", SenderRole.AGENT, "Agente")
        );
        chatService.appendMessage(
                conversationId,
                new SendPayload(UUID.randomUUID().toString(), "tres", SenderRole.CUSTOMER, "Cliente")
        );

        List<MessagePayload> replay = chatService.listMessages(conversationId, 1L);
        assertThat(replay).hasSize(2);
        assertThat(replay.get(0).seq()).isEqualTo(2L);
        assertThat(replay.get(1).seq()).isEqualTo(3L);
    }
}

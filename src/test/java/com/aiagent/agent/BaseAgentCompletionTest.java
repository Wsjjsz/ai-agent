package com.aiagent.agent;

import com.aiagent.agent.model.AgentState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAgentCompletionTest {

    @Test
    void runStreamInvokesCompletionHandlerWithSerializedTrace() throws Exception {
        CompletingAgent agent = new CompletingAgent();
        agent.setName("test-agent");
        agent.setSystemPrompt("test");
        agent.setMaxSteps(3);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> savedContent = new AtomicReference<>();

        agent.runStream("hello", content -> {
            savedContent.set(content);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(savedContent.get().contains("Step 1: 思考完成：ok"));
        assertTrue(savedContent.get().contains("[FINAL_RESULT]"));
    }

    private static class CompletingAgent extends BaseAgent {

        @Override
        public String step() {
            setState(AgentState.FINISHED);
            return "思考完成：ok";
        }
    }
}

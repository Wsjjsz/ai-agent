package com.aiagent.agent;

import jakarta.annotation.Resource;
import com.aiagent.agent.model.AgentState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Disabled("Requires live LLM/tool integrations; keep out of default unit-test CI.")
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class YuManusTest {

    @Resource
    private YuManus yuManus;

    @Test
    public void runStream(CapturedOutput output) throws InterruptedException {
        int originalMaxSteps = yuManus.getMaxSteps();
        yuManus.setMaxSteps(1);
        String userPrompt = """
                我关注上海本地消费板块，请帮我检索近期相关新闻，
                并结合公开市场信息，制定一份详细的投资观察计划，
               markdown形式""";
        try {
            SseEmitter answer = yuManus.runStream(userPrompt);
            Assertions.assertNotNull(answer);

            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline) {
                AgentState state = yuManus.getState();
                if (state == AgentState.FINISHED || state == AgentState.ERROR) {
                    break;
                }
                Thread.sleep(200);
            }

            Assertions.assertNotEquals(AgentState.RUNNING, yuManus.getState(), "Agent should reach terminal state within timeout");
            Assertions.assertNotEquals(AgentState.ERROR, yuManus.getState(), "Agent should not enter ERROR state");
            Assertions.assertTrue(yuManus.getCurrentStep() > 0, "Agent should execute at least one step");
            Assertions.assertFalse(output.getOut().contains("思考过程遇到了问题"), "Agent logs should not contain thinking errors");
        } finally {
            yuManus.setMaxSteps(originalMaxSteps);
        }
    }
}

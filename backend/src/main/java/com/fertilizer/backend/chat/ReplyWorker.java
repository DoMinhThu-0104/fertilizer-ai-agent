package com.fertilizer.backend.chat;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mỗi giây lấy các tin khách đang chờ trong database và trả lời, chạy trên luồng nền.
 * Lỗi thì thử lại giãn dần; hết lượt thì đánh dấu FAILED. Không log nội dung tin nhắn.
 */
@Component
@ConditionalOnProperty(name = "reply.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ReplyWorker {

    private static final Logger log = LoggerFactory.getLogger(ReplyWorker.class);

    private final ReplyQueue queue;
    private final ReplyService replies;
    private final Executor executor;
    private final int batchSize;
    private final int maxAttempts;
    private final int maxAgeHours;

    public ReplyWorker(
            ReplyQueue queue,
            ReplyService replies,
            @Qualifier("applicationTaskExecutor") Executor executor,
            @Value("${reply.batch-size:10}") int batchSize,
            @Value("${reply.max-attempts:5}") int maxAttempts,
            @Value("${reply.max-age-hours:20}") int maxAgeHours) {
        this.queue = queue;
        this.replies = replies;
        this.executor = executor;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.maxAgeHours = maxAgeHours;
    }

    @Scheduled(fixedDelayString = "${reply.poll-ms:1000}")
    public void poll() {
        try {
            int swept = queue.sweep(maxAttempts, maxAgeHours);
            if (swept > 0) {
                log.warn("Hàng đợi trả lời: bỏ {} tin (quá hạn hoặc hết lượt thử)", swept);
            }
            List<ReplyQueue.ReplyJob> jobs = queue.claim(batchSize);
            for (ReplyQueue.ReplyJob job : jobs) {
                try {
                    executor.execute(() -> run(job));
                } catch (RejectedExecutionException e) {
                    queue.markRetry(job.id(), job.attempts(), "qua tai");
                }
            }
        } catch (RuntimeException e) {
            // Lỗi database tạm thời: lần quét sau thử lại, không làm dừng bộ lập lịch.
            log.warn("Hàng đợi trả lời: lỗi khi quét: {}", e.getClass().getSimpleName());
        }
    }

    void run(ReplyQueue.ReplyJob job) {
        try {
            replies.reply(job.event());
            queue.markDone(job.id());
        } catch (RuntimeException e) {
            String error = e instanceof MessengerException ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Trả lời tin {} thất bại (lần {}): {}", job.id(), job.attempts(), error);
            if (job.attempts() >= maxAttempts) {
                queue.markFailed(job.id(), error);
            } else {
                queue.markRetry(job.id(), job.attempts(), error);
            }
        }
    }
}

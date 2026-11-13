package com.fertilizer.backend.chat;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;

import com.fertilizer.backend.chat.ReplyQueue.ReplyJob;

class ReplyWorkerTest {

    private final ReplyQueue queue = mock(ReplyQueue.class);
    private final ReplyService replies = mock(ReplyService.class);
    private final Executor direct = Runnable::run;
    private final ReplyWorker worker = new ReplyWorker(queue, replies, direct, 10, 3, 20);

    private final MessengerEvent event = new MessengerEvent("P1", "U1", "mid.in1", "Xin chào", Instant.now());

    @Test
    void pollProcessesEveryClaimedJob() {
        when(queue.claim(10)).thenReturn(List.of(new ReplyJob(1, 1, event), new ReplyJob(2, 1, event)));

        worker.poll();

        verify(queue).sweep(3, 20);
        verify(replies, times(2)).reply(event);
        verify(queue).markDone(1);
        verify(queue).markDone(2);
    }

    @Test
    void failureWithAttemptsLeftSchedulesARetry() {
        doThrow(new MessengerException("Send API trả lỗi HTTP 400")).when(replies).reply(event);

        worker.run(new ReplyJob(5, 1, event));

        verify(queue).markRetry(5, 1, "Send API trả lỗi HTTP 400");
        verify(queue, never()).markDone(anyLong());
    }

    @Test
    void lastAttemptMarksTheJobFailed() {
        doThrow(new MessengerException("Send API trả lỗi HTTP 400")).when(replies).reply(event);

        worker.run(new ReplyJob(6, 3, event));

        verify(queue).markFailed(6, "Send API trả lỗi HTTP 400");
    }

    @Test
    void unexpectedErrorStoresOnlyTheClassName() {
        doThrow(new IllegalStateException("chua noi dung cua khach")).when(replies).reply(event);

        worker.run(new ReplyJob(7, 1, event));

        verify(queue).markRetry(7, 1, "IllegalStateException");
    }

    @Test
    void fullExecutorReleasesTheJobForLater() {
        Executor full = r -> { throw new RejectedExecutionException("full"); };
        ReplyWorker busy = new ReplyWorker(queue, replies, full, 10, 3, 20);
        when(queue.claim(10)).thenReturn(List.of(new ReplyJob(8, 2, event)));

        busy.poll();

        verify(queue).markRetry(8, 2, "qua tai");
        verify(replies, never()).reply(event);
    }
}

package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

/**
 * 测试并发集合{@link ConcurrentLinkedQueue} 的复合操作是否线程安全.
 */
public class ConcurrentLinkedQueueTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentLinkedQueueTest.class);

    /**
     * 测试zookeeper可能bug 或者 tricky 的代码
     * 在zookeeper Leader.ToBeAppliedRequestProcessor构造函数的注释
     * This request processor simply maintains the toBeApplied list. For
     * this to work next must be a FinalRequestProcessor and
     * FinalRequestProcessor.processRequest MUST process the request
     * synchronously!
     * 仅说到后续的process必须要同步.
     * 但在方法 processRequest中对 toBeApplied(ConcurrentLinkedQueue)进行了peek判断然后再remove,
     * 个人理解,此方法应该是并行的.同一个zookeeper server,理论上讲同一个ToBeAppliedRequestProcessor可能会
     * 被多个线程共用.
     *  public void processRequest(Request request) throws RequestProcessorException {
     *             // request.addRQRec(">tobe");
     *             next.processRequest(request);
     *             Proposal p = toBeApplied.peek();
     *             if (p != null && p.request != null
     *                     && p.request.zxid == request.zxid) {
     *                 toBeApplied.remove();
     *             }
     *         }
     */
    @Test
    public void testConcurrentLinkedQue() {

        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        queue.offer(1);
        queue.add(2);

        Thread t2 = new Thread(() -> {
            LockSupport.park(ConcurrentLinkedQueueTest.class);
            Integer ele = queue.remove();
            LOGGER.info("t2 removed element:{}", ele);
        });
        Thread t1 = new Thread(() -> {
            Integer ele = queue.peek();
            LOGGER.info("peek element:{}", ele);
            LockSupport.unpark(t2);
            LockSupport.parkNanos(100000);
            ele = queue.remove();
            LOGGER.info("t1 removed element:{}", ele);
        });

        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}

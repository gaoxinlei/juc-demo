package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 *测试阻塞队列,推断线程池行为.
 */
public class QueueTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueTest.class);

    /**
     * 测试SynchronousQueue的默认offer是否会成功.
     * 此处的两例一定会得到false.
     * 第一种情况,进入transfer方法,会因此检测到timed为true,nanos为1的情况,直接会返回一个null给
     * offer方法,进而返回false.
     * 第二种情况,会阻塞5秒等待transfer,因为此处没有启动第二个线程去transfer,最终超时返回null给
     * offer方法,同样返回false.
     * 显然,官方提供的Executors入口最多只是个样板类,除newFix或newSingle外,其余线程池各有槽点.
     * 1.schedule池和ForkJoin池只能因为返回的是ThreadPoolExecutor,需要强转才能提交ScheduleTask
     * 和ForkJoinTask.
     * 2.cache池默认使用了SynchronousQueue,而且直接offer(command)而不是offer(command,time),这
     * 将导致执行workQueue.offer时必然失败.本例就是验证这种失败.
     */
    @Test
    public void testSynchronousQueue(){
        SynchronousQueue<Integer> queue = new SynchronousQueue<>();
        boolean result = queue.offer(1);
        LOGGER.info("加入队列结果:{}",result);
        try {
            result = queue.offer(1,5,TimeUnit.SECONDS);
            LOGGER.info("加入队列结果:{}",result);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

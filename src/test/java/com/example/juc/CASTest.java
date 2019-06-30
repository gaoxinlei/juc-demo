package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * 演示和说明cas
 */
public class CASTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CASTest.class);
    private AtomicInteger number = new AtomicInteger(0);
    private AtomicStampedReference<Integer> refer = new AtomicStampedReference<>(0, 1);


    /**
     * 用AtomicInteger测试aba问题
     * 全部成功
     */
    @Test
    public void testAbaByInteger() {
        LOGGER.info("初值:{}", number.get());
        boolean result = number.compareAndSet(0, 10);
        LOGGER.info("交换:{},结果:{}", result ? "成功" : "失败", number.get());
        result = number.compareAndSet(10, 0);
        LOGGER.info("交换:{},结果:{}", result ? "成功" : "失败", number.get());
        result = number.compareAndSet(0, 20);
        LOGGER.info("交换:{},结果:{}", result ? "成功" : "失败", number.get());
    }

    /**
     * 单线程用refer  aba
     * 全部成功
     */
    @Test
    public void testAbaByRefer() {
        LOGGER.info("初值:{},印戳:{}", refer.getReference(), refer.getStamp());
        boolean result = refer.compareAndSet(0, 10, 1, 2);
        LOGGER.info("交换:{},结果:{},印戳:{}", result ? "成功" : "失败", refer.getReference(), refer.getStamp());
        result = refer.compareAndSet(10, 0, 2, 3);
        LOGGER.info("交换:{},结果:{},印戳:{}", result ? "成功" : "失败", refer.getReference(), refer.getStamp());
        result = refer.compareAndSet(0, 20, refer.getStamp(), refer.getStamp() + 1);
        LOGGER.info("交换:{},结果:{},印戳:{}", result ? "成功" : "失败", refer.getReference(), refer.getStamp());
    }

    /**
     * 用两个线程尝试用atomicInteger修改
     */
    @Test
    public void testAbaIntegerMultiThread() {
        Thread t1 = new Thread(() -> {
            LOGGER.info("初值:{}", number.get());
            boolean result = number.compareAndSet(0, 10);
            LOGGER.info("交换:{},结果:{}", result ? "成功" : "失败", number.get());
            LOGGER.info("睡两秒等待重置结果");
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            result = number.compareAndSet(0, 20);
            LOGGER.info("交换:{},结果:{}", result ? "成功" : "失败", number.get());
        });
        Thread t2 = new Thread(()->{
            LOGGER.info("睡一秒等待线程1执行");
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            boolean result = refer.compareAndSet(10, 0, 2, 3);
            LOGGER.info("交换:{},结果:{},印戳:{}", result ? "成功" : "失败", refer.getReference(), refer.getStamp());
        });
        t1.start();
        t2.start();
        try{
            t1.join();
            t2.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * 用两个线程尝试用refer 做aba修改
     */
    @Test
    public void testAbaByReferMultiThread() {
        Thread t1 = new Thread(() -> {
            LOGGER.info("初值:{}", refer.getReference());
            boolean result = refer.compareAndSet(0, 10,refer.getStamp(),refer.getStamp()+1);
            int stamp = refer.getStamp();
            LOGGER.info("交换:{},结果:{},印戳:{}", result ? "成功" : "失败", refer.getReference(), stamp);
            LOGGER.info("睡两秒等待重置结果");
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            result = refer.compareAndSet(0,20,stamp,stamp+1);
            LOGGER.info("交换:{},结果:{},印戳:{}", result ? "成功" : "失败", refer.getReference(), refer.getStamp());
        });
        Thread t2 = new Thread(()->{
            LOGGER.info("睡一秒等待线程1执行");
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            boolean result = refer.compareAndSet(10, 0, refer.getStamp(), refer.getStamp()+1);
            LOGGER.info("交换:{},结果:{},印戳:{}", result ? "成功" : "失败", refer.getReference(), refer.getStamp());
        });
        t1.start();
        t2.start();
        try{
            t1.join();
            t2.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * 测试用cas同一值.
     */
    @Test
    public void testCasSameValue(){
        AtomicInteger value = new AtomicInteger(1);
        boolean result = value.compareAndSet(1, 1);
        LOGGER.info("结果:{}",result);//true
    }
}

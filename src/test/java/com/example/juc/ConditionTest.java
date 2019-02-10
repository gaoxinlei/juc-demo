package com.example.juc;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Condition test和源码切入.
 */
public class ConditionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionTest.class);
    private LinkedList<String> buffer;//容器
    private int maxSize = 10;//容器最多装入的元素.
    private Lock lock;//锁
    private Condition notFull;//未满condition
    private Condition notEmpty;//非空condition
    private CountDownLatch latch;


    @Before
    public void beforeTest() {
        this.buffer = new LinkedList<>();
        this.lock = new ReentrantLock();
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
        this.latch = new CountDownLatch(100);
        LOGGER.info("初始化成功");
    }

    /**
     * 只有一个线程生产一个线程消费的情况
     */
    @Test
    public void testGetAndSet() {
        LOGGER.info("测试开始");
       new Thread(this::set).start();
       new Thread(this::get).start();
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info("测试成功");
    }

    private void set(){
        int times = 100;
        Random random = new Random(10);
        lock.lock();
        try{
            while(times>0){
                while(maxSize==buffer.size()){
                    LOGGER.info("缓存已满,暂停");
                    notFull.await();
                }
                double number = random.nextDouble();
                String production = String.valueOf(number);
                buffer.add(production);
                times--;
                LOGGER.info("生产了随机种子:{}",production);
                notEmpty.signal();
                Thread.yield();
                TimeUnit.SECONDS.sleep(1);

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
    }
    private void get(){
        int times = 100;
        lock.lock();
        try{
            while(times>0){
                while(0==buffer.size()){
                    LOGGER.info("缓存已空,暂停");
                    notEmpty.await();
                }

                String result = buffer.poll();
                times--;
                latch.countDown();
                LOGGER.info("get result : {}", result);
                notFull.signal();
                Thread.yield();
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
    }
}

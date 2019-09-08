package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * volatile禁用重排序test
 * 涉及知识点:
 * 程序操作中的三个"性":可见性,有序性,原子性.
 * volatile关键字能保证一个操作的可见性和一定程度的有序性,不能保证复合操作的原子性.
 */
public class VolatileTest {

    private static Logger LOGGER = LoggerFactory.getLogger(VolatileTest.class);
    private int stub = 0;
    private volatile boolean flag = false;

    /**
     * JMM及happen before用volatile的实现
     * jvm底层利用memory boundary 实现volatile
     */
    @Test
    public void testMemHappenBefore() {
        //a线程
        new Thread(this::incrementStub).start();
        //b线程
        new Thread(this::logStubByFlag).start();
        sleepOneSecond();
        sleepOneSecond();

    }

    private void sleepOneSecond() {
        sleepLongTime(1);
    }

    private void logStubByFlag() {
        sleepOneSecond();
        //volatile读,将在其后加入LoadLoad屏障和LoadStore屏障,分别禁止与其后的普通读/写重排序.
        if (flag) {//3 volatile读
            LOGGER.info("stub:" + this.stub);//4 普通读
        }

    }

    private void incrementStub() {
        sleepOneSecond();
        this.stub++;//1 happens before 2  普通写
        //volatile写,将在其前加入一个StoreStore屏障,强制刷新前面写操作到主内存,禁止上面的普通写与本句volatile写重排序
        //在volatile写后加入一个StoreLoad屏障,禁止与其后的volatile读/写重排序
        this.flag = true;//2 happens before 3 volatile写
    }


    private volatile int honey;
    private Lock honeyLock = new ReentrantLock();
    private CountDownLatch latch = new CountDownLatch(100);

    /**
     * 蜜蜂与熊(生产者与消费者案例).
     * 共10只蜜蜂,一头熊.
     * 蜂蜜最多生产10份,当份数满时,蜜蜂停工.
     * 熊可以偷吃蜂蜜,当蜂蜜被消耗干将时,熊休眠.
     */
    @Test
    public void testBeerAndBee() {
        //熊
        new Thread(this::eatHoney).start();
        //蜜蜂
        for (int i = 0; i < 10; i++) {
            int j = i;
            new Thread(() -> {
                produceHoney(j);
            }).start();
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sleepLongTime(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void eatHoney() {
        int i = 0;
        while (i < 100) {
            if (this.honey > 0) {
                honeyLock.lock();
                if (this.honey > 0) {
                    LOGGER.info("熊要开始吃蜂蜜了,吃之前还剩{}个蜂蜜", this.honey);
                    this.honey--;
                    i++;
                    latch.countDown();
                    LOGGER.info("熊吃了一次蜂蜜,还剩{}个蜂蜜,吃蜜次数:{}", this.honey, i);
                    honeyLock.unlock();
                    Thread.yield();
                } else {
                    LOGGER.info("当前没有蜂蜜可消费:{},熊要休眠了", this.honey);
                    honeyLock.unlock();
                    Thread.yield();
                    sleepOneSecond();
                }
            }
        }
    }

    private void produceHoney(int number) {
        int i = 0;
        while (i < 10) {
            if (this.honey < 10) {
                honeyLock.lock();
                if (this.honey < 10) {
                    LOGGER.info("蜜蜂{}要开始生产蜂蜜了,此时蜂蜜存量:{}", number, this.honey);
                    this.honey++;
                    i++;
                    LOGGER.info("蜜蜂{}生产了一份蜂蜜,蜂蜜存量:{},生产次数:{}", number, this.honey, i);
                    honeyLock.unlock();
                    Thread.yield();
                } else {
                    LOGGER.info("蜂蜜已满,蜜蜂:{}进入休眠模式", number);
                    honeyLock.unlock();
                    Thread.yield();
                    sleepOneSecond();
                }
            }
        }
    }

    /**
     * volatile加入内存屏障造成的其他变量的可见性.
     */
    @Test
    public void testVisible() throws InterruptedException {
        SetGet bean = new SetGet();
        Thread t1 = new Thread(()->{bean.set();});
        Thread t2 = new Thread(()->{if(bean.check()) LOGGER.info("可见");});
        t1.start();
        t2.start();
        t2.join();
        t1.join();
    }

    private class SetGet{
        int a;
        volatile int b;

        void set(){
            a = 10;
            b = 5;
        }

        boolean check(){
            return (a==0&&b==0)||(a==10&&b==5);
        }
    }


    @Test
    public void testSingle() throws Throwable {
        SingleInt a = this::any;
        SingleInt b = this::any;
        LOGGER.info("a==b:{}",a==b);
//        MethodHandle say = MethodHandles.lookup().findVirtual(a.getClass(),"say",MethodType.methodType(void.class));
//        say.invoke();
//        Object g = new Object();
//        MethodType mt=MethodType.methodType(void.class);
//        MethodHandle handle = MethodHandles.lookup().findVirtual(g.getClass(), "finalize", mt);
//        handle.invoke();
        a.say();

    }

    @FunctionalInterface
    private interface SingleInt{
        void say();
    }

    private void any(){
        LOGGER.info("say..");
    }


}

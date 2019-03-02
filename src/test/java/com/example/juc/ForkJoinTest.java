package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

/**
 * fork join test
 */
public class ForkJoinTest {

    //ForkJoinTask中的一些常量.
    static final int DONE_MASK   = 0xf0000000;  // mask out non-completion bits
    static final int NORMAL      = 0xf0000000;  // must be negative
    static final int CANCELLED   = 0xc0000000;  // must be < NORMAL
    static final int EXCEPTIONAL = 0x80000000;  // must be < CANCELLED
    static final int SIGNAL      = 0x00010000;  // must be >= 1 << 16
    static final int SMASK       = 0x0000ffff;  // short bits for tags

    private static final Logger LOGGER = LoggerFactory.getLogger(ForkJoinTest.class);
    @Test
    public void testMask(){
        LOGGER.info("DONE_MASK:{}",DONE_MASK);
        LOGGER.info("NORMAL:{}",NORMAL);
        LOGGER.info("CANCELLED:{}",CANCELLED);
        LOGGER.info("EXCEPTIONAL:{}",EXCEPTIONAL);
        LOGGER.info("SIGNAL:{}",SIGNAL);
        LOGGER.info("SMASK:{}",SMASK+18);
    }

    @Test
    public void testAddress(){
        int a = 5;
        LOGGER.info("a==(a=3):{}",a==(a=3));//false
    }

    @Test
    public void testFields(){
        ExecutorService pool = Executors.newWorkStealingPool();
    }

    /**
     * 线程中断只是设置一个状态,设置后线程的行为由具体run代码决定.
     */
    @Test
    public void testInterrupt(){
        Thread thread = Thread.currentThread();
        LOGGER.info("扰动前:{}",thread.isInterrupted());
        thread.interrupt();
        LOGGER.info("扰动后:{}",thread.isInterrupted());
        Thread  t = new Thread(()->{
            LOGGER.info("子线程扰动前:{}",Thread.currentThread().isInterrupted());
            Thread.currentThread().interrupt();
            LOGGER.info("子线程扰动后:{}",Thread.currentThread().isInterrupted());
            if(Thread.currentThread().isInterrupted()){
                LOGGER.info("被扰动执行下面代码.");
            }else{
                LOGGER.info("被扰动不能执行下面的代码");
            }

        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testForkJoin(){
        ExecutorService pool = Executors.newWorkStealingPool();
        class ForkJoinTaskWrapper extends RecursiveTask<Integer> {

            private int base;
            ForkJoinTaskWrapper(Integer base){
                this.base = base;
            }

            @Override
            protected Integer compute() {
                //当前数大于10,就分为10和余下的数的compute相乘.
                if(this.base>10){
                    ForkJoinTaskWrapper subTask = new ForkJoinTaskWrapper(base-10);
                    subTask.fork();
                    try {
                        Integer interResult = subTask.get();
                        LOGGER.info("中间结果:{}",interResult);
                        return interResult *10;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                }
                return this.base%10;
            }


        }
        //传统方式获取结果
        ForkJoinTask<Integer> result = ((ForkJoinPool) pool).submit(new ForkJoinTaskWrapper(45));
        try {
            LOGGER.info("最终结果:{}",result.get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        //安静方式获得结果
        ForkJoinTaskWrapper task = new ForkJoinTaskWrapper(52);
        result = ((ForkJoinPool)pool).submit(task);
        LOGGER.info("最终结果:{}",result.join());

    }



}

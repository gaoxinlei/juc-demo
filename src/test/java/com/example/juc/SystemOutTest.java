package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 测试输出对同步造成的影响.
 */
public class SystemOutTest {

    private  static final Logger LOGGER = LoggerFactory.getLogger(SystemOutTest.class);



    /**
     * 多线程,用System out 看同步.
     */
    @Test
    public void testSystemOut() throws InterruptedException {
        BooleanHolder holder = new BooleanHolder();
        Thread t1 = new Thread(()->{
            holder.flag = true;
        });

        Thread t2 = new Thread(()->{
            //中间加上一个monitor enter exit 的过程,发生了从主内存刷回来的操作.
            //同步块中并未进行任何对flag的修改.
            System.out.println();//注释掉,永远打印线程2无标记.
            //同步块退出,flag重新读入,此时可能读取到线程1修改的结果.
            if(holder.flag){
                System.out.println("线程2有标记");
            }else{
                System.out.println("线程2无标记");
            }
        });
        t2.start();
        t1.start();
        t1.join();
        t2.join();
    }

    /**
     * 多线程,先不输出,记录标记.
     */
    @Test
    public void testSyncronized() throws InterruptedException {
        BooleanHolder holder = new BooleanHolder();
        BooleanHolder duringMonitor = new BooleanHolder();
        BooleanHolder afterMonitorExit = new BooleanHolder();
        Thread t1 = new Thread(()->{
            holder.flag = true;
        });
        Thread t2 = new Thread(()->{
            //什么也不做的同步块,监视器也和代码无任何关系,但依旧有monitor enter和exit
            synchronized (LocalDateTime.class){}
            if(afterMonitorExit.flag = holder.flag){
                LOGGER.info("线程2有标记");
            }else{
                LOGGER.info("线程2无标记");
            }
        });
        t2.start();
        t1.start();
        t1.join();
        t2.join();
        LOGGER.info("montior 期间的flag:{}",duringMonitor.flag);
        LOGGER.info("montior exit后的flag:{}",afterMonitorExit.flag);
    }
    /**
     * 多线程,用logger输出,记录标记.
     */
    @Test
    public void testRecordHolder() throws InterruptedException {
        BooleanHolder holder = new BooleanHolder();
        BooleanHolder beforeMonitor = new BooleanHolder();
        BooleanHolder afterMonitorExit = new BooleanHolder();
        Thread t1 = new Thread(()->{
            holder.flag = true;
        });
        Thread t2 = new Thread(()->{
            LOGGER.info(String.valueOf(beforeMonitor.flag = holder.flag));//它也会加锁,注释掉保证读取原始值.
            if(afterMonitorExit.flag = holder.flag){
                LOGGER.info("线程2有标记");
            }else{
                LOGGER.info("线程2无标记");
            }
        });
        t2.start();
        t1.start();
        t1.join();
        t2.join();
        LOGGER.info("montior enter前的flag:{}",beforeMonitor.flag);
        LOGGER.info("montior exit后的flag:{}",afterMonitorExit.flag);
    }

    private static class BooleanHolder{

        boolean flag = false;
    }

}

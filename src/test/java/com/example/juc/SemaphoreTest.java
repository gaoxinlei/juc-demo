package com.example.juc;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * semaphore test
 * 一个vpp只能同时服务10个客户端,若超过10个客户端申请服务,需要等待前面的完成释放.
 */
public class SemaphoreTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SemaphoreTest.class);
    private Semaphore semaphore;

    @Before
    public void before(){
        this.semaphore = new Semaphore(10,true);
    }

    @Test
    public void testVpp(){
        List<Thread> clients = new ArrayList<>();
        for(int i = 0;i<50;i++){
            final int number = i;
            Thread client = new Thread(()->{
                requestVpp(number);
            });
            clients.add(client);
            client.start();
        }
        clients.forEach(client->{
            try {
                client.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void requestVpp(int number){
        LOGGER.info("客户:{}请求vpp服务",number);
        try {
            this.semaphore.acquire();
            LOGGER.info("vpp受理客户:{}请求,耗时1秒",number);
            TimeUnit.SECONDS.sleep(1);
            LOGGER.info("vpp完成对客户:{}的服务",number);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            this.semaphore.release();
        }

    }
}

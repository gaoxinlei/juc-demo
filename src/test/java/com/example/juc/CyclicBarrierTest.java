package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * CyclicBarrier demo
 * 启一个vpp,5个vsd,然后进入后置逻辑
 */
public class CyclicBarrierTest {

    private static  final Logger LOGGER = LoggerFactory.getLogger(CyclicBarrierTest.class);
    private CyclicBarrier barrier;

    @Test
    public void testEnm(){
        initVpp();
        barrier = new CyclicBarrier(5,this::startVirtualDiscoveryServices);
        for(int i=0;i<5;i++){
            int number = i;
            new Thread(()->{
                initVsd(number);
                try {
                    barrier.await();
                    afterInit(number);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void afterInit(int number) {
        LOGGER.info("after {} vsd init ...",number);
    }

    private void initVsd(int number) {
        LOGGER.info("init {} vsd ...",number);
    }

    private void initVpp() {
        LOGGER.info("init vpp...");
    }

    private void startVirtualDiscoveryServices(){
        LOGGER.info("服务发现已可用");
    }

}

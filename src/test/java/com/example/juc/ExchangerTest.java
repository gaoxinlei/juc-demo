package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Exchanger;

/**
 * 测试exchanger
 */
public class ExchangerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangerTest.class);
    private Exchanger<List<String>> exchanger = new Exchanger<>();

    /**
     * 定义两个线程,一个线程生产,另一个线程消费.
     */
    @Test
    public void testExchange() {
        //生产.
        Thread producer = new Thread(this::produceAndExchange);
        Thread consumer = new Thread(this::consumeAndExchange);
        producer.start();
        consumer.start();
        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void consumeAndExchange() {
        for(int i = 0;i<5;i++){
            List<String> buffer = new ArrayList<>();
            try {
                List<String> result = exchanger.exchange(buffer);
                LOGGER.info("消费者第{}次进行消费",i+1);
                LOGGER.info("消费者第{}次消费,消费了:{}",i+1,result);
                int number = i;
                result.forEach(product->{
                    LOGGER.info("第{}次消费的产品:{}",number+1,product);
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void produceAndExchange() {
        for(int i = 0;i<5;i++){
            List<String> buffer = new ArrayList<>();
            LOGGER.info("生产者第{}次进行生产",i+1);
            for(int j=0;j<3;j++){
                buffer.add((i+1)+"-"+(j+1));
                LOGGER.info("生产者生产了:{}-{}",i+1,j+1);
            }
            try {
                List<String> result = exchanger.exchange(buffer);
                LOGGER.info("生产者收取到:",result);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

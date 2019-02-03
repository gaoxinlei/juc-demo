package com.example.juc;

import org.junit.Test;

import java.util.Vector;

/**
 * 锁消除demo
 */
public class LockAbandonTest {

    @Test
    public void testLockAbandon(){
        Vector<Integer> vector = new Vector<Integer>(10);
        for(int i=0 ; i<=5;i++){
            //public synchronized boolean add(E e)
            //尽管add方法本身被synchronized修饰，但jvm可以判断没有锁争抢，因此会锁消除。
            vector.add(i);
        }
    }
}

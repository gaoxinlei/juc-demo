package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试Phaser的简单使用
 */
public class PhaserTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaserTest.class);

    /**
     * 不利用ForkJoin框架的补偿.
     * 本方法重点测试的是顺序问题.
     * 注意:因为没有加锁,在arrive前打印的日志中,打印的getArrivedParties只能表示那一瞬间的数值,
     * 可能会有多个线程读到了同一个值并打印.此处不值得纠结.
     * 乱序问题:每一轮的arrive前的日志打印(记录arrive前轮次)可能和上一轮的进入轮次(arrive后升级的轮次)
     * 混序(即如出现打印进入6轮次和第6个phase的arrive).
     * 原因是arrive数量达到后,有的线程先被唤醒并进入新轮次,打印"第6phase的第...",
     * 而某些后唤醒的线程此后才打印"线程..进入第6轮次..."
     *
     * 重点:
     * 前面说的都不重要,重要的在于,只看arrive前日志或进入轮次日志,他们绝对不会乱序.
     * 1.每一轮的进入轮次日志肯定早于后一轮次的进入日志.
     * 如"线程5进入6轮次"肯定早于"线程2进入7轮次".
     * 2.每一轮的进入前日志一定早于后一轮的进入前日志.
     * 如"第7个phase的第..个arrive"一定早于"第8个phase的第..个arrive".
     * 3.读取到的轮次号一定不乱序.尽管每一轮的进入轮次日志可能会和后一轮次的arrive前日志有同号.
     * 但是不论什么日志,因为arrive前和进入后都使用了getPhase方法(它保证可见性),6一定在7前.
     */
    @Test
    public void testPhaser(){
        Phaser phaser = new Phaser();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        for(int i=0;i<10;i++){
            //不规范用法,没有在一个线程里注册/等待.
            phaser.register();
        }
        LOGGER.info("完成了10个parties注册.");
        List<Future> taskList = new ArrayList<>();
        for(int i=0;i<100;i++){
            Future<?> task = pool.submit(() -> {
                //打印phaser.getArrivedParties是瞬间的,可能有多个线程在此处读出同一值.因为未使用同步块.
                LOGGER.info("第:{}个phase的第:{}个arrive:{}", phaser.getPhase(),
                        phaser.getArrivedParties(), Thread.currentThread().getName());
                phaser.arriveAndAwaitAdvance();
                LOGGER.info("线程:{}进入第:{}轮次", Thread.currentThread().getName(), phaser.getPhase());
            });

            taskList.add(task);

        }
        //等待完成.
        taskList.forEach(task->{
            try {
                task.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        });

    }

    /**
     * 利用ForkJoinPool的补偿机制,动态的满足arrive.
     * 注意,本机测试的common池并行度为3,也就是最多同时有3个线程活跃,每个
     * 线程都会在运行case期间fork出子任务,并且可能在满足条件(本例是满足第十个线程到达并同时执行子任务)
     * 时阻塞等待本phase的advance.
     * 但因为ForkJoinPool的补偿机制.当线程阻塞等待advance时,新的线程创建入池并抢夺任务,它们可能又
     * 去fork子任务,自身也可能会阻塞在等待本phase的advance上,并引发新的补偿,直到全部执行完毕.
     * 很明显,使用ForkJoinPool的自动补偿机制来管理阻塞,Phaser一定能够保证arrive.
     * 这是本测试这样设计的最大目的.
     *
     * 本例中,最初任务入队后只建立了三个线程,他们会逐渐在执行子任务时fork更细的子任务,并且会在满足条件
     * 时,即要执行的任务正好是number细分到1时,阻塞等待有十个线程满足了同样的条件并执行到同样的代码块,
     * 因为只有三个线程,ForkJoinPool只能帮我们补偿线程执行,如此循环往复,可能加杂了唤醒和创建新线程
     * 的步骤,经测试可能会创建15个以上的线程.
     */
    @Test
    public void testForkJoin(){
        //依旧不使用规范用法(其实可以在ForkJoinTask中先注册再await,但是我们测试的就是补偿机制)
        Phaser phaser = new Phaser(10);
        LOGGER.info("common池的并行度:{}",ForkJoinPool.commonPool().getParallelism());
        MyTask task = new MyTask(100,phaser,new AtomicInteger(100));
        task.invoke();
    }

    private static class MyTask extends ForkJoinTask<Integer> {

        private int number;
        private Phaser phaser;
        private AtomicInteger counter;

        MyTask(int number,Phaser phaser,AtomicInteger counter){
            this.number = number;
            this.phaser = phaser;
            this.counter = counter;
        }
        @Override
        public Integer getRawResult() {
            return null;
        }

        @Override
        protected void setRawResult(Integer value) {

        }

        @Override
        protected boolean exec() {
            if(this.number > 1){
                MyTask right = new MyTask(number-1,this.phaser,this.counter);
                MyTask left = new MyTask(1,phaser,this.counter);
                left.fork();
                right.fork();
                right.join();
                left.join();

            }else{
                LOGGER.info("当前线程:{},当前phase:{},当前arrive:{},当前计数器:{}",
                        Thread.currentThread().getName(),
                        this.phaser.getPhase(),
                        this.phaser.getArrivedParties(),
                        this.counter.get());
                phaser.arriveAndAwaitAdvance();
                LOGGER.info("线程:{}完成轮次,当前phase:{},完成前计数器:{}",
                        Thread.currentThread().getName(),
                        this.phaser.getPhase(),
                        this.counter.getAndDecrement());
            }
            return true;
        }
    }

}

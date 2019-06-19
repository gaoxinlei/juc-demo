package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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

    /**
     * 测试completableFuture的dep同步执行的结果。
     * 在本方法的whenComplete方法体中断点可以看调用栈。
     * 在CompletableFuture的uniWhenComplete方法的
     * if (a == null || (r = a.result) == null || f == null)
     * 处打断点然后以不同的手速放行一二次，
     * 可以看到每一次调用uniWhenComplete时source的结果是null还是2.
     * 结论：真正调用了uniWhenComplete这个关键方法（进而可能执行用户定义的fn）有三处，
     * 1.一上来执行whenComplete时，因同步无线程池，uniWhenCompleteStage会在if中调一次。
     * 2.代码1失败时，封装成UniApply再调一次tryFire。
     * 3.source成功后，postComplete会对栈中每一个Completion进行tryFire。
     *
     * 因为本例是同步调用，若为异步，很明显在1处将不会调用，而是在Completion的run方法中调tryFire，进而
     * 再调uniWhenComplete.
     *
     * 若将join移出注释，则一定能看到断点进入whenComplete代码块，但不一定能打印日志。
     */
    @Test
    public void testCompletableFuture(){

        CompletableFuture<Integer> source = CompletableFuture.supplyAsync(()->{
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return 2;
        });

        source.whenComplete((i,t)->{
            LOGGER.info("执行结果：{}，异常：{}",i,t);
        });
        //等待source结束，则一定会进入上面的方法。
//        source.join();
    }

    /**
     * 测试CountedCompleter 道格大神提出的一种应用:
     * 折半加分治查找的完成.
     * 案例中的数量级,divide和下面的直接遍历时间已经差不多.再加一个数量级测试没等到跑完.
     */
    @Test
    public void testDivideSearch(){
        Integer[] array = new Integer[10000000];
        for(int i = 0; i < array.length; i++){
            array[i] = i+1;
        }
        AtomicReference<Integer> result = new AtomicReference<>();
        Integer find = new Searcher<>(null, array, result, 0,
                array.length - 1,this::match).invoke();
        LOGGER.info("查找结束,任务返回:{},result:{}",find,result.get());

    }

    static class Searcher<E> extends CountedCompleter<E> {

        final E[] array; final AtomicReference<E> result; final int lo, hi;
        final Function<E,Boolean> matcher;

        Searcher(CountedCompleter<?> p, E[] array, AtomicReference<E> result,
                 int lo, int hi,Function<E,Boolean> matcher){
            super(p);
            this.array = array;
            this.result = result;
            this.lo = lo;
            this.hi = hi;
            this.matcher = matcher;
        }
        @Override
        public void compute() {
            int l = this.lo;int h = this.hi;
            while(result.get() == null && h >= l){

                if(h - l >=2){
                    int mid = (l + h)>>>1;
                    addToPendingCount(1);
                    new Searcher<E>(this,array,result,mid,h,matcher).fork();
                    h = mid;
                }else{
                    E x = array[l];
                    if(matcher.apply(x) &&  result.compareAndSet(null,x)){
                        super.quietlyCompleteRoot();
                    }
                    break;
                }
            }
            if(null == result.get())
                tryComplete();
        }

    }

    private boolean match(Integer x) {
        return x > 2000000 &&  x%2 ==0 && x%3 == 0 && x%5 ==0 && x %7 ==0;
    }

    @Test
    public void testDirectFound(){
        Integer[] array = new Integer[10000000];
        for(int i = 0; i < array.length; i++){
            array[i] = i+1;
        }
        for(int i=0;i<array.length;i++){
            if(match(array[i])){
                LOGGER.info("查找到结果,索引:{},结果:{}",i,array[i]);
                break;
            }
        }
    }

}

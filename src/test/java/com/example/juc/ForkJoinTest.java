package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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
import java.util.stream.Stream;

/**
 * fork join test
 */
public class ForkJoinTest {

    //ForkJoinTask中的一些常量.
    static final int DONE_MASK = 0xf0000000;  // mask out non-completion bits
    static final int NORMAL = 0xf0000000;  // must be negative
    static final int CANCELLED = 0xc0000000;  // must be < NORMAL
    static final int EXCEPTIONAL = 0x80000000;  // must be < CANCELLED
    static final int SIGNAL = 0x00010000;  // must be >= 1 << 16
    static final int SMASK = 0x0000ffff;  // short bits for tags

    private static final Logger LOGGER = LoggerFactory.getLogger(ForkJoinTest.class);

    @Test
    public void testMask() {
        LOGGER.info("DONE_MASK:{}", DONE_MASK);
        LOGGER.info("NORMAL:{}", NORMAL);
        LOGGER.info("CANCELLED:{}", CANCELLED);
        LOGGER.info("EXCEPTIONAL:{}", EXCEPTIONAL);
        LOGGER.info("SIGNAL:{}", SIGNAL);
        LOGGER.info("SMASK:{}", SMASK + 18);
    }

    @Test
    public void testAddress() {
        int a = 5;
        LOGGER.info("a==(a=3):{}", a == (a = 3));//false
    }

    @Test
    public void testFields() {
        ExecutorService pool = Executors.newWorkStealingPool();
    }

    /**
     * 线程中断只是设置一个状态,设置后线程的行为由具体run代码决定.
     */
    @Test
    public void testInterrupt() {
        Thread thread = Thread.currentThread();
        LOGGER.info("扰动前:{}", thread.isInterrupted());
        thread.interrupt();
        LOGGER.info("扰动后:{}", thread.isInterrupted());
        Thread t = new Thread(() -> {
            LOGGER.info("子线程扰动前:{}", Thread.currentThread().isInterrupted());
            Thread.currentThread().interrupt();
            LOGGER.info("子线程扰动后:{}", Thread.currentThread().isInterrupted());
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info("被扰动执行下面代码.");
            } else {
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
    public void testForkJoin() {
        ExecutorService pool = Executors.newWorkStealingPool();
        class ForkJoinTaskWrapper extends RecursiveTask<Integer> {

            private int base;

            ForkJoinTaskWrapper(Integer base) {
                this.base = base;
            }

            @Override
            protected Integer compute() {
                //当前数大于10,就分为10和余下的数的compute相乘.
                if (this.base > 10) {
                    ForkJoinTaskWrapper subTask = new ForkJoinTaskWrapper(base - 10);
                    subTask.fork();
                    try {
                        Integer interResult = subTask.get();
                        LOGGER.info("中间结果:{}", interResult);
                        return interResult * 10;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                }
                return this.base % 10;
            }


        }
        //传统方式获取结果
        ForkJoinTask<Integer> result = ((ForkJoinPool) pool).submit(new ForkJoinTaskWrapper(45));
        try {
            LOGGER.info("最终结果:{}", result.get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        //安静方式获得结果
        ForkJoinTaskWrapper task = new ForkJoinTaskWrapper(52);
        result = ((ForkJoinPool) pool).submit(task);
        LOGGER.info("最终结果:{}", result.join());

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
     * <p>
     * 因为本例是同步调用，若为异步，很明显在1处将不会调用，而是在Completion的run方法中调tryFire，进而
     * 再调uniWhenComplete.
     * <p>
     * 若将join移出注释，则一定能看到断点进入whenComplete代码块，但不一定能打印日志。
     */
    @Test
    public void testCompletableFuture() {

        CompletableFuture<Integer> source = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return 2;
        });

        source.whenComplete((i, t) -> {
            LOGGER.info("执行结果：{}，异常：{}", i, t);
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
    public void testDivideSearch() {
        Integer[] array = new Integer[10000000];
        for (int i = 0; i < array.length; i++) {
            array[i] = i + 1;
        }
        AtomicReference<Integer> result = new AtomicReference<>();
        Integer find = new Searcher<>(null, array, result, 0,
                array.length - 1, this::match).invoke();
        LOGGER.info("查找结束,任务返回:{},result:{}", find, result.get());

    }

    static class Searcher<E> extends CountedCompleter<E> {

        final E[] array;
        final AtomicReference<E> result;
        final int lo, hi;
        final Function<E, Boolean> matcher;

        Searcher(CountedCompleter<?> p, E[] array, AtomicReference<E> result,
                 int lo, int hi, Function<E, Boolean> matcher) {
            super(p);
            this.array = array;
            this.result = result;
            this.lo = lo;
            this.hi = hi;
            this.matcher = matcher;
        }

        @Override
        public void compute() {
            int l = this.lo;
            int h = this.hi;
            while (result.get() == null && h >= l) {

                if (h - l >= 2) {
                    //满足分治条件,分治.
                    int mid = (l + h) >>> 1;
                    addToPendingCount(1);//分治前增加pending数.
                    new Searcher<E>(this, array, result, mid, h, matcher).fork();
                    h = mid;
                } else {
                    //不满足分治条件,自算.
                    E x = array[l];
                    if (matcher.apply(x) && result.compareAndSet(null, x)) {
                        super.quietlyCompleteRoot();//得到结果并设置成功立即终止root
                    }
                    break;
                }
            }
            //任务已出队,不会再次执行,此例又不需要bookkeeping,故只需在当前没有任何任务查找到result
            //(包含当前任务)的情况下减少一个pending count(或在所有都0的时候完结root).
            if (null == result.get())
                tryComplete();
        }

    }

    private boolean match(Integer x) {
        return x > 2000000 && x % 2 == 0 && x % 3 == 0 && x % 5 == 0 && x % 7 == 0;
    }

    @Test
    public void testDirectFound() {
        Integer[] array = new Integer[10000000];
        for (int i = 0; i < array.length; i++) {
            array[i] = i + 1;
        }
        for (int i = 0; i < array.length; i++) {
            if (match(array[i])) {
                LOGGER.info("查找到结果,索引:{},结果:{}", i, array[i]);
                break;
            }
        }
    }

    @Test
    public void testMapReduce() {
        Integer[] array = {1, 2, 3};
        //方法一.
        Integer result = new MapRed<>(null, array, (a) -> a + 2, (a, b) -> a + b, 0, array.length).invoke();
        LOGGER.info("方法一result:{}", result);
        //方法二.
        result = new MapReducer<>(null, array, (a) -> a + 1
                , (a, b) -> a + b, 0, array.length, null).invoke();
        LOGGER.info("方法二result:{}", result);

    }


    /**
     * 第一种map reduce方式,很好理解.
     *
     * @param <E>
     */
    private class MapRed<E> extends CountedCompleter<E> {
        final E[] array;
        final MyMapper<E> mapper;
        final MyReducer<E> reducer;
        final int lo, hi;
        MapRed<E> sibling;
        E result;

        MapRed(CountedCompleter<?> p, E[] array, MyMapper<E> mapper,
               MyReducer<E> reducer, int lo, int hi) {
            super(p);
            this.array = array;
            this.mapper = mapper;
            this.reducer = reducer;
            this.lo = lo;
            this.hi = hi;
        }

        public void compute() {
            if (hi - lo >= 2) {
                int mid = (lo + hi) >>> 1;
                MapRed<E> left = new MapRed(this, array, mapper, reducer, lo, mid);
                MapRed<E> right = new MapRed(this, array, mapper, reducer, mid, hi);
                left.sibling = right;
                right.sibling = left;
                setPendingCount(1); // only right is pending
                right.fork();
                left.compute();     // directly execute left
            } else {
                if (hi > lo)
                    result = mapper.apply(array[lo]);
                //它会依次调用onCompletion.并且是自己调自己或completer调子,
                //且只有左右两个子后完成的能调成功(父 pending达到0).
                tryComplete();
            }
        }

        public void onCompletion(CountedCompleter<?> caller) {
            if (caller != this) {
                MapRed<E> child = (MapRed<E>) caller;//被调的是子.
                MapRed<E> sib = child.sibling;
                //设置父的result.
                if (sib == null || sib.result == null)
                    result = child.result;
                else
                    result = reducer.apply(child.result, sib.result);
            }
        }

        public E getRawResult() {
            return result;
        }


    }

    private static class MapReducer<E> extends CountedCompleter<E> {

        final E[] array;
        final MyMapper<E> mapper;
        final MyReducer<E> reducer;
        final int lo, hi;
        MapReducer<E> forks, next; // record subtask forks in list
        E result;

        MapReducer(CountedCompleter<?> p, E[] array, MyMapper<E> mapper,
                   MyReducer<E> reducer, int lo, int hi, MapReducer<E> next) {
            super(p);
            this.array = array;
            this.mapper = mapper;
            this.reducer = reducer;
            this.lo = lo;
            this.hi = hi;
            //forks指向该任务最新fork的子任务,子任务的next指向创建它的任务的原forks,可以理解为最近的一个兄弟
            //且forks没初值,只有fork过才会有值.
            this.next = next;
        }

        @Override
        public void compute() {
            int l = lo, h = hi;
            while (h - l >= 2) {
                int mid = (l + h) >>> 1;
                addToPendingCount(1);
                //fork.
                (forks = new MapReducer<E>(this, array, mapper, reducer, mid, h, forks)).fork();
                h = mid;
            }
            if (h > l)
                //出了上面的循环,叶子节点,result直接是mapper的结果.
                result = mapper.apply(array[l]);
            // process completions by reducing along and advancing subtask links
            //每一个非叶子completer会在firstComplete失败退出.每一层只有最后一个兄弟节点可以成功进入循环.
            for (CountedCompleter<?> c = firstComplete(); c != null; c = c.nextComplete()) {
                //第一轮外循环叶子节点s=t.forks是null,不进入内循环.
                for (MapReducer<E> t = (MapReducer<E>) c, s = t.forks; s != null; s = t.forks = s.next)
                    //用c和它fork的子节点进行reduce作为c结果.reduce后再与该子节点记录的next进行reduce
                    t.result = reducer.apply(t.result, s.result);
                //一轮内循环中深度不变,s指向下一个兄弟节点,一轮外循环将兄弟节点的结果都reduce后存放在c中.
                //一轮外循环结束(非首轮),nextComplete移向c的栈链下一元素,重复上面的过程.
            }
            //非叶子非根不自行维护status,运行compute前已出栈,叶子会一步步在nextComplete中设置complete.
        }

        public E getRawResult() {
            return result;
        }

    }

    @FunctionalInterface
    private static interface MyMapper<E> {
        E apply(E e);
    }

    private static interface MyReducer<E> {
        E apply(E a, E b);
    }

    /**
     * 测试并行流.
     */
    @Test
    public void testParallelStream() {
        int result = Stream.of(1, 2, 3, 4, 5).parallel().map(x -> x + 1).reduce((a, b) -> a + b).get();
        LOGGER.info("result:{}", result);
    }

    /**
     * 测试ForkJoinPool初始ctl和config
     */
    @Test
    public void testInitCtlAndConfig() {
        int parallel = Runtime.getRuntime().availableProcessors();
        LOGGER.info("并行度:{}", parallel);
        int fMode = 1 << 16;//FIFO
        int lMode = 0;//LIFO
        int mode = fMode;
        long fifteen = 0xffffL;
        long aMask = fifteen << 48;//能取前16位.
        long tMask = fifteen << 32;//能取33-48位.

        int sMask = 0xffff;//SMASK常量,16位取

        //config初值
        long config = (parallel & sMask) | mode;//10004
        LOGGER.info("FIFO mode下的config:{}", Long.toHexString(config));
        long np = (long) -parallel;
        LOGGER.info("FIFO mode下的np:{}", Long.toHexString(np));
        long ctlHigh = np << 48 & aMask;
        long ctlLow = np << 32 & tMask;
        LOGGER.info("FIFO mode 下的高低ctl:{},{}", Long.toHexString(ctlHigh),
                Long.toHexString(ctlLow));
        long ctl = ctlHigh | ctlLow;//fffcfffc00000000
        LOGGER.info("FIFO mode下的ctl:{}", Long.toHexString(ctl));
        //addWorker方法中计算的nc
        long nc = ((aMask & (ctl + (1L << 48))) | (tMask & (ctl + 1L << 32)));
        LOGGER.info("FIFO mode下新增一个worker后的ctl:{}", Long.toHexString(nc));

        //切换模式
        mode = lMode;
        //config初值
        config = (parallel & sMask) | mode;//4
        LOGGER.info("LIFO mode下的config:{}", Long.toHexString(config));
        np = (long) -parallel;
        LOGGER.info("LIFO mode下的np:{}", Long.toHexString(np));
        ctlHigh = np << 48 & aMask;
        ctlLow = np << 32 & tMask;
        LOGGER.info("LIFO mode 下的高低ctl:{},{}", Long.toHexString(ctlHigh),
                Long.toHexString(ctlLow));
        ctl = ctlHigh | ctlLow;//fffcfffc00000000
        LOGGER.info("LIFO mode下的ctl:{}", Long.toHexString(ctl));
        //addWorker方法中计算的nc
        nc = ((aMask & (ctl + (1L << 48))) | (tMask & (ctl + (1L << 32))));
        LOGGER.info("LIFO mode下新增一个worker后的ctl:{}", Long.toHexString(nc));
        //显然二者只有config不同.ctl相同
    }

    /**
     * 测试赋值表达式,应当返回false.
     */
    @Test
    public void testExpression() {
        int a = 6, b = 4;
        LOGGER.info("result:{}", b == (b = a));
    }

    /**
     * 测试随机数在顺序执行runWorker,scan,awaitWork的奇偶.
     * 原始种子r从1-10发现奇偶性在迭代过程中可能没有准确的规律.
     */
    @Test
    public void testEvenOrOdd() {
        for (int i = 1; i <= 10; i++) {
            int r = i, a = r;
            r ^= r << 13;
            r ^= r >>> 17;
            r ^= r << 5;
            LOGGER.info("初值为:{},runWorker的迭代:{},是否偶数:{}", a, a = r, r % 2 == 0);
            r ^= r << 1;
            r ^= r >>> 3;
            r ^= r << 10;
            LOGGER.info("初值为:{},scan的迭代:{},是否偶数:{}", a, a = r, r % 2 == 0);
            r ^= r << 6;
            r ^= r >>> 21;
            r ^= r << 7;
            LOGGER.info("初值为:{},awaitWork的迭代:{},是否偶数:{}", a, r, r % 2 == 0);
        }
    }

    /**
     * 测试runWorker方法中随机数r的迭代.
     */
    @Test
    public void testRunWorkerEvenOrOdd() {
        List<Integer> changedNumbers = new ArrayList<>();
        int max = 0;
        int maxHolder = 0;
        for (int i = 1; i <= 100; i++) {
            int r = i, a = r;
            //迭代十轮.
            for (int j = 1; j <= 20; j++) {
                r ^= r << 13;
                r ^= r >>> 17;
                r ^= r << 5;
                if ((a & 1) != (r & 1)) {
                    LOGGER.info("种子为:{},runWorker第:{}轮的迭代结果:{},出现奇偶改变,由:{}变:{}",
                            a, j, r, a % 2 == 0, r % 2 == 0);
                    //1000轮内找到的数break的添加.
                    changedNumbers.add(i);
                    max = j > max ? j : max;
                    maxHolder = j > maxHolder ? i : maxHolder;
                    break;
                }
            }
        }
        LOGGER.info("100以内数字,20轮循环内最晚变化在{}轮,数字为:{},发生变化的数共{}个:{}",
                max, maxHolder, changedNumbers.size(), changedNumbers);
    }

    /**
     * 测试ForkJoinPool scan中的随机数r算法迭代.
     */
    @Test
    public void testScanEvenOrOdd() {
        List<Integer> changedNumbers = new ArrayList<>();
        int max = 0;
        int maxHolder = 0;
        for (int i = 1; i <= 100; i++) {
            int r = i, a = r;
            //迭代十轮.
            for (int j = 1; j <= 20; j++) {
                r ^= r << 1;
                r ^= r >>> 3;
                r ^= r << 10;
                if ((a & 1) != (r & 1)) {
                    LOGGER.info("种子为:{},scan第:{}轮的迭代结果:{},出现奇偶改变,由:{}变:{}",
                            a, j, r, a % 2 == 0, r % 2 == 0);
                    //1000轮内找到的数break的添加.
                    changedNumbers.add(i);
                    max = j > max ? j : max;
                    maxHolder = j > maxHolder ? i : maxHolder;
                    break;
                }
            }
        }
        LOGGER.info("100以内数字,20轮循环内最晚变化在{}轮,数字为:{},发生变化的数共{}个:{}",
                max, maxHolder, changedNumbers.size(), changedNumbers);
    }

    /**
     * 模拟ForkJoinPool的awaitWork方法对随机数i的迭代.
     */
    @Test
    public void testAwaitWorkEvenOrOdd() {
        List<Integer> changedNumbers = new ArrayList<>();
        int max = 0;
        int maxHolder = 0;
        for (int i = 1; i <= 100; i++) {
            int r = i, a = r;
            //迭代十轮.
            for (int j = 1; j <= 20; j++) {
                r ^= r << 6;
                r ^= r >>> 21;
                r ^= r << 7;
                if ((a & 1) != (r & 1)) {
                    LOGGER.info("种子为:{},awaitWork第:{}轮的迭代结果:{},出现奇偶改变,由:{}变:{}",
                            a, j, r, a % 2 == 0, r % 2 == 0);
                    //1000轮内找到的数break的添加.
                    changedNumbers.add(i);
                    max = j > max ? j : max;
                    maxHolder = j > maxHolder ? i : maxHolder;
                    break;
                }
            }
        }
        LOGGER.info("100以内数字,20轮循环内最晚变化在{}轮,数字为:{},发生变化的数共{}个:{}",
                max, maxHolder, changedNumbers.size(), changedNumbers);
    }

}

package com.example.juc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 模仿ForkJoin框架的CountedCompleter和并行流，用以分析低版本java中的应对策略。
 * 例：
 * 一个请求（有一个线程）需要开n个线程并行执行对其他服务的请求，且主线程需要等待n个线程执行完成，
 * 并将结果merge到最终结果返回。
 * 目标：
 * 1.多个请求能共用同一个线程池，且不能使用7版才出现的ForkJoinPool，线程池大小可人为调整。
 * 2.一请求一响应为一接口，要求接口响应尽可能快。
 * <p>
 * 解决方案细则：
 * 1.使用固定线程池模拟，暴露阻塞队列和拒绝策略。
 * 2.用父子任务的格式存放任务，任何一个子任务出现异常，整个任务为异常完成，其他还未执行的子任务不再执行。
 * 3.只有一部分子任务提交到线程池，请求所在的主线程可执行部分任务而不空等。
 * 4.主线程可在执行完自己负责的子任务后，尝试将未开始执行的其他子任务获取并执行。
 * 5.主线程可在执行完自己负责的子任务后，且发现没有执行完父任务，且子任务已都被获取的情况下，尝试帮助线程池执行其他。
 * 6.线程池队列不配置过大，当线程池队满，且最大线程数已满时，任务的拒绝策略为调用者执行。
 * 7.子任务不再分裂，线程池中的线程不需要自行维护队列，也不存在互相偷取。
 * <p>
 * 模仿相关的反偷和分治两大策略，不模仿补偿策略。
 */
public class CompleterTest {

    private ExecutorService pool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
            10, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(18), new ThreadPoolExecutor.CallerRunsPolicy());

    private class Completer {
        Completer completer;
        AtomicInteger status = new AtomicInteger(0);//小于0代表完成，等于0代表未取，等于1代表已取走，等于2代表异常。
        AtomicInteger count = new AtomicInteger(0);//只有root维护。
        //存放要split的子任务列表，存放它的原因，是反偷时不需要利用线程池构造的队列进行获取，而是可以用
        //task.claim的方式将队列声明到本线程。阻塞队列使用remove方式需要加全局锁，目测会伤性能。
        //另外，当出现拒绝策略被触发时，很可能当前父任务已经压入队列2个任务，结果外部线程自身快速执行完另外两个
        //任务，却要等提交到线程池中的其他任务完成的情况，因此需要一个快速的反偷机制，且反偷后是否要从线程池中
        //移除相应的任务，待定。（如果能删除，意味着一开始就不需要tasks字段。
        List<CompleteTask> tasks = new ArrayList<>();


        void reduceCount() {
            int c, s;
            while (!count.compareAndSet((c = count.get()), c - 1) && c >= 0) ;
            if (c == 1 && (s = status.get()) == 0) {
                status.set(1 << 31);
            }

        }

        boolean shouldSplit() {
            return !this.tasks.stream().allMatch(CompleteTask::claimed);
        }

        CompleteTask claim() {
            for (CompleteTask t : tasks) {
                if (t.claimed())
                    return t;
            }
            return null;
        }

        Integer invoke() {

            CompleteTask task = claim();
            tasks.forEach(t -> {
                if (!t.claimed())
                    pool.submit(t);
            });
            //runTask,并减掉一个count.且当count到0时完成status
            //运行任务后，再尝试反窃。
            while (status.get() == 0) {
                if ((task = claim()) != null) {
                    //runTask并减掉一个count，且当count到0时完成status。
                } else {
                    //说明没有task了，从池中取任务？或者直接await一个闭锁？
                }
            }
            //从循环中走出，要么异常，要么子任务都完成。
            //进入异常处理逻辑。
            return 0;
        }
    }

    @Test
    public void testCompleter() {
        Completer root = new Completer();
        root.count = new AtomicInteger(4);


    }

    private int running() {
        return 0;
    }

    private class CompleteTask extends FutureTask<Integer> {
        volatile AtomicInteger status = new AtomicInteger(0);

        public CompleteTask(Callable<Integer> callable) {
            super(callable);
        }

        boolean claimed() {
            int s;
            return (s = status.get()) != 0;
        }

        boolean claim() {
            return status.compareAndSet(0, 1);
        }
    }

}

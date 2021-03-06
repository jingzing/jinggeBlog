package gq.jingge.blog.base.thread.chapter07;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author wangyj
 * @description
 * @create 2018-05-02 11:27
 *
 * 从运行结果可以看出，<font color="red">当一个线程运行完毕后才把锁释放，其他线程才能执行，其他线程的执行顺序是不确定的</font>
 **/
public class ReentrantLockTest {

    public static void main(String[] args) {
        MyService service = new MyService();

        MyThread a1 = new MyThread(service);
        MyThread a2 = new MyThread(service);
        MyThread a3 = new MyThread(service);
        MyThread a4 = new MyThread(service);
        MyThread a5 = new MyThread(service);

        a1.start();
        a2.start();
        a3.start();
        a4.start();
        a5.start();
    }

    static public class MyService {

        private Lock lock = new ReentrantLock();

        public void testMethod() {
            lock.lock();
            try {
                for (int i = 0; i < 5; i++) {
                    System.out.println("ThreadName=" + Thread.currentThread().getName() + (" " + (i + 1)));
                }
            } finally {
                lock.unlock();
            }

        }

    }

    static public class MyThread extends Thread {

        private MyService service;

        public MyThread(MyService service) {
            super();
            this.service = service;
        }

        @Override
        public void run() {
            service.testMethod();
        }
    }
}

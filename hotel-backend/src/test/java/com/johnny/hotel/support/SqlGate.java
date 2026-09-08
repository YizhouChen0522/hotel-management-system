package com.johnny.hotel.support;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import java.util.concurrent.*;

/** One-shot deterministic barrier after a chosen SQL statement has acquired its lock. */
@Intercepts({@Signature(type=Executor.class, method="query", args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}),
        @Signature(type=Executor.class, method="update", args={MappedStatement.class,Object.class})})
public class SqlGate implements Interceptor {
    public volatile String thread, suffix;
    public volatile boolean fail;
    public CountDownLatch reached = new CountDownLatch(1), release = new CountDownLatch(1);
    public void arm(String thread, String suffix, boolean fail) {
        this.thread=thread; this.suffix=suffix; this.fail=fail;
        reached=new CountDownLatch(1); release=new CountDownLatch(1);
    }
    public void clear() { thread=null; release.countDown(); }
    public static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("SQL barrier timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    @Override public Object intercept(Invocation invocation) throws Throwable {
        Object result=invocation.proceed();
        var ms=(MappedStatement)invocation.getArgs()[0];
        if (Thread.currentThread().getName().equals(thread) && ms.getId().endsWith(suffix)) {
            thread=null; reached.countDown();
            if (fail) throw new IllegalStateException("Injected transaction failure");
            await(release);
        }
        return result;
    }
}

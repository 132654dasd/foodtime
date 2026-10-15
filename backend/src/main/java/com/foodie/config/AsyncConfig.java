package com.foodie.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步线程池。
 *
 * <p>用来承载「图片缩略图生成」这类 CPU 密集但不需要用户等待的工作。
 *
 * <p>关键点是<b>有界队列 + CallerRunsPolicy</b>：
 * 无界队列在流量突增时会把任务一直堆积到 OOM；
 * 有界队列配 CallerRunsPolicy，则是在队列满时让调用线程自己执行，
 * 相当于对上游做一次天然的反向压力，服务不至于崩掉。
 */
@Configuration
public class AsyncConfig {

    /** 图片处理专用线程池名，注入时用 {@code @Qualifier} 引用。 */
    public static final String IMAGE_EXECUTOR = "imageTaskExecutor";

    @Bean(name = IMAGE_EXECUTOR)
    public Executor imageTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("foodie-img-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等在途图片处理完再退出，避免产生半截文件
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}

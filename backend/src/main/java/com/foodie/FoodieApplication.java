package com.foodie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 食光 Foodie —— 美食内容平台后端入口。
 *
 * <p>开启的能力：
 * <ul>
 *   <li>{@code @EnableCaching} —— Caffeine 进程内缓存（食谱详情／字典／热门榜）</li>
 *   <li>{@code @EnableAsync} —— 图片缩略图生成等耗时任务异步化</li>
 *   <li>{@code @EnableScheduling} —— 浏览量内存计数批量落库</li>
 *   <li>{@code @ConfigurationPropertiesScan} —— 扫描 app.* 下的 record 配置类</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableAsync
@EnableScheduling
public class FoodieApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodieApplication.class, args);
    }
}
